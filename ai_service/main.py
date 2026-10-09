from __future__ import annotations

from typing import Any, Dict

from fastapi import FastAPI, HTTPException

try:
    from ai_service.config import APP_HOST, APP_NAME, APP_PORT, EMBEDDING_MODEL
    from ai_service.database import fetch_candidates, fetch_semifinalized_candidates, fetch_vacancy
    from ai_service.schemas import DataReadyRequest, HealthResponse, ScreeningRequest
    from ai_service.services.embedding_service import EmbeddingService
    from ai_service.services.ollama_service import OllamaService
    from ai_service.services.ranking_service import RerankerService, find_semantic_matches, generate_ranked_candidates
except ModuleNotFoundError:  # pragma: no cover - fallback for repo-root package path
    from Resume_Screener_09.ai_service.config import APP_HOST, APP_NAME, APP_PORT, EMBEDDING_MODEL
    from Resume_Screener_09.ai_service.database import fetch_candidates, fetch_semifinalized_candidates, fetch_vacancy
    from Resume_Screener_09.ai_service.schemas import DataReadyRequest, HealthResponse, ScreeningRequest
    from Resume_Screener_09.ai_service.services.embedding_service import EmbeddingService
    from Resume_Screener_09.ai_service.services.ollama_service import OllamaService
    from Resume_Screener_09.ai_service.services.ranking_service import RerankerService, find_semantic_matches, generate_ranked_candidates

app = FastAPI(title=APP_NAME, version="1.0.0")


def _truncate(text: str, max_chars: int = 1200) -> str:
    if not text:
        return ""
    return text.strip()[:max_chars] + ("..." if len(text.strip()) > max_chars else "")


def _build_rag_context(vacancy_id: int, vacancy_text: str, resume_text: str) -> str:
    context_parts = [
        "You are evaluating a candidate for a hiring decision.",
        f"Vacancy: {vacancy_text.strip()[:800]}",
        f"Current candidate resume: {_truncate(resume_text, 1200)}",
    ]

    ranked_candidates = generate_ranked_candidates(vacancy_id, vacancy_text, limit=5)
    if ranked_candidates:
        context_parts.append("Recent top-ranked candidates for this vacancy:")
        for item in ranked_candidates:
            context_parts.append(
                f"- {item.get('candidate_name', 'Unknown')} (candidate_id={item.get('candidate_id')}): "
                f"score={item.get('score', 0):.3f}, fit={item.get('match_percentage', 0)}%"
            )
    else:
        context_parts.append("No ranked candidates were available for this vacancy in the DB.")

    semifinalized = fetch_semifinalized_candidates(vacancy_id, limit=5)
    if semifinalized:
        context_parts.append("Previously semifinalized candidates for this vacancy:")
        for item in semifinalized:
            context_parts.append(
                f"- {item.get('full_name') or item.get('candidate_id')} "
                f"(candidate_id={item.get('candidate_id')}): score={item.get('score') if item.get('score') is not None else 'n/a'}, status={item.get('status', 'unknown')}"
            )
    else:
        context_parts.append("No semifinalized candidates have been recorded for this vacancy yet.")

    return "\n".join(context_parts)


def _extract_one_sentence_conclusion(text: str) -> str:
    if not text:
        return "This candidate is a reasonable fit for the role based on the available evidence."
    cleaned = text.strip().replace("\n", " ")
    sentences = [part.strip() for part in cleaned.split(".") if part.strip()]
    if not sentences:
        return cleaned[:220] if len(cleaned) <= 220 else cleaned[:217] + "..."
    first = sentences[0]
    if len(first) > 220:
        first = first[:217].rstrip() + "..."
    return first if first.endswith((".", "!", "?")) else first + "."


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(
        status="ok",
        service="resume-screener-ai",
        version="1.0.0",
        db_ready=True,
        embedding_model=EMBEDDING_MODEL,
    )


@app.get("/data-ready")
def data_ready_get() -> Dict[str, Any]:
    return {
        "status": "ok",
        "service": "resume-screener-ai",
        "message": "AI service is ready",
        "ready": True,
    }


@app.post("/data-ready")
def data_ready_post(payload: DataReadyRequest) -> Dict[str, Any]:
    if payload.event != "resume_uploaded":
        raise HTTPException(status_code=400, detail="Unsupported event")

    return {
        "status": "accepted",
        "event": payload.event,
        "batch_id": payload.batch_id,
        "candidate_ids": payload.candidate_ids or [],
        "vacancy_id": payload.vacancy_id,
        "count": payload.count or 0,
        "message": "Java payload accepted by AI service",
    }


@app.post("/screening/run")
async def screening_run(payload: ScreeningRequest) -> Dict[str, Any]:
    vacancy_id = payload.vacancy_id
    vacancy_entry = fetch_vacancy(vacancy_id)
    vacancy_text = payload.vacancy_text.strip() if payload.vacancy_text else ""

    if not vacancy_text and vacancy_entry:
        vacancy_text = " ".join(
            part for part in [
                vacancy_entry.get("job_title"),
                vacancy_entry.get("department"),
                vacancy_entry.get("job_description"),
                vacancy_entry.get("required_skills"),
                vacancy_entry.get("preferred_skills"),
                vacancy_entry.get("qualifications"),
                vacancy_entry.get("responsibilities"),
            ]
            if part
        )

    resume_text = payload.resume_text.strip() if payload.resume_text else ""
    if not vacancy_text or not resume_text:
        raise HTTPException(status_code=400, detail="Both vacancy text and resume text are required")

    vacancy_vector = EmbeddingService.embed_text(vacancy_text)
    resume_vector = EmbeddingService.embed_text(resume_text)
    semantic_similarity = sum(a * b for a, b in zip(vacancy_vector, resume_vector))
    norm = 0.0
    if vacancy_vector and resume_vector:
        import math

        va = math.sqrt(sum(v * v for v in vacancy_vector))
        vb = math.sqrt(sum(v * v for v in resume_vector))
        if va and vb:
            norm = semantic_similarity / (va * vb)

    rerank_score = RerankerService.rerank_pair(vacancy_text, resume_text)
    score = max(0.0, min(1.0, (norm + rerank_score) / 2.0))
    match_percentage = int(round(score * 100))

    reasoning = "Semantic search and BGE reranking were combined to score this candidate."
    try:
        rag_context = _build_rag_context(vacancy_id, vacancy_text, resume_text)
        prompt = (
            f"{rag_context}\n\n"
            "Use the vacancy description, the current resume, and the ranked/semifinalized context above. "
            "Return exactly one sentence that concludes whether this candidate is a good fit for the role, and keep it concise. "
            "Do not use bullet points or labels like 'shortlisted' or 'recommended'; just write a final concluding sentence."
        )
        response = await OllamaService.generate(prompt)
        if isinstance(response, dict) and response.get("response"):
            reasoning = _extract_one_sentence_conclusion(str(response["response"]))
    except Exception:
        pass

    return {
        "status": "ok",
        "vacancy_id": vacancy_id,
        "candidate_id": payload.candidate_id,
        "score": round(score, 4),
        "match_percentage": match_percentage,
        "embedding_size": len(vacancy_vector),
        "semantic_similarity": round(float(norm), 4),
        "rerank_score": round(float(rerank_score), 4),
        "reasoning": reasoning,
        "message": "AI screening signal generated successfully using semantic search and BGE reranking",
    }


@app.post("/semantic-search")
async def semantic_search(payload: Dict[str, Any]) -> Dict[str, Any]:
    vacancy_id = int(payload.get("vacancy_id", 0))
    vacancy_text = str(payload.get("vacancy_text") or "")
    if not vacancy_id or not vacancy_text:
        raise HTTPException(status_code=400, detail="vacancy_id and vacancy_text are required")

    results = generate_ranked_candidates(vacancy_id, vacancy_text, limit=int(payload.get("limit", 10)))
    return {"status": "ok", "vacancy_id": vacancy_id, "results": results}


@app.post("/screening/rerank")
async def rerank_candidates(payload: Dict[str, Any]) -> Dict[str, Any]:
    vacancy_id = int(payload.get("vacancy_id", 0))
    vacancy_text = str(payload.get("vacancy_text") or "")
    if not vacancy_id or not vacancy_text:
        raise HTTPException(status_code=400, detail="vacancy_id and vacancy_text are required")

    candidate_rows = fetch_candidates(limit=int(payload.get("limit", 20)))
    ranked = []
    for row in candidate_rows:
        resume_text = row.get("raw_text") or ""
        if not resume_text:
            continue
        vacancy_vector = EmbeddingService.embed_text(vacancy_text)
        candidate_vector = EmbeddingService.embed_text(resume_text)
        best = sum(a * b for a, b in zip(vacancy_vector, candidate_vector))
        norm = 0.0
        import math

        va = math.sqrt(sum(v * v for v in vacancy_vector))
        vb = math.sqrt(sum(v * v for v in candidate_vector))
        if va and vb:
            norm = best / (va * vb)
        rerank_score = RerankerService.rerank_pair(vacancy_text, resume_text)
        score = max(0.0, min(1.0, (norm + rerank_score) / 2.0))
        ranked.append({
            "candidate_id": row.get("id"),
            "candidate_name": row.get("full_name"),
            "score": round(score, 4),
            "semantic_similarity": round(float(norm), 4),
            "rerank_score": round(float(rerank_score), 4),
            "match_percentage": int(round(score * 100)),
        })

    ranked.sort(key=lambda item: item["score"], reverse=True)
    return {"status": "ok", "vacancy_id": vacancy_id, "results": ranked[:10]}


@app.post("/embed")
def embed_payload(payload: Dict[str, str]) -> Dict[str, Any]:
    text = payload.get("text") or payload.get("resume_text") or payload.get("vacancy_text") or ""
    if not text.strip():
        raise HTTPException(status_code=400, detail="Text is required")

    vector = EmbeddingService.embed_text(text)
    return {
        "status": "ok",
        "embedding_size": len(vector),
        "vector": vector[:10],
    }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("ai_service.main:app", host=APP_HOST, port=APP_PORT, reload=False)
