import math
import re
from typing import Any, Dict, Iterable, List

try:
    from ai_service.config import RERANKER_MODEL
    from ai_service.database import create_candidate_embedding, create_vacancy_embedding, fetch_candidates, find_similar_candidates
    from ai_service.services.embedding_service import EmbeddingService
except ModuleNotFoundError:  # pragma: no cover - fallback for repo-root package path
    from Resume_Screener_09.ai_service.config import RERANKER_MODEL
    from Resume_Screener_09.ai_service.database import create_candidate_embedding, create_vacancy_embedding, fetch_candidates, find_similar_candidates
    from Resume_Screener_09.ai_service.services.embedding_service import EmbeddingService


class RerankerService:
    _model = None

    @classmethod
    def get_model(cls):
        if cls._model is None:
            try:
                from sentence_transformers import CrossEncoder
                cls._model = CrossEncoder(RERANKER_MODEL, max_length=1024)
            except Exception:
                cls._model = None
        return cls._model

    @classmethod
    def rerank_pair(cls, vacancy_text: str, resume_text: str) -> float:
        if not vacancy_text or not resume_text:
            return 0.0
        model = cls.get_model()
        if model is None:
            return 0.0
        try:
            prediction = model.predict([(vacancy_text.strip(), resume_text.strip())], show_progress_bar=False)
            raw = float(prediction[0]) if hasattr(prediction, '__len__') and len(prediction) else float(prediction)
            return max(0.0, min(1.0, 1.0 / (1.0 + math.exp(-raw))))
        except Exception:
            return 0.0


def _normalize(text: str) -> List[str]:
    if not text:
        return []
    words = re.findall(r"[a-zA-Z0-9+#.]+", text.lower())
    stop_words = {"the", "and", "for", "with", "from", "into", "that", "this", "your", "have", "will", "over", "about"}
    return [word for word in words if word and word not in stop_words and len(word) > 2]


def keyword_overlap_score(vacancy_text: str, resume_text: str) -> float:
    vacancy_terms = set(_normalize(vacancy_text))
    resume_terms = set(_normalize(resume_text))
    if not vacancy_terms or not resume_terms:
        return 0.0
    overlap = len(vacancy_terms & resume_terms)
    union = len(vacancy_terms | resume_terms)
    if union == 0:
        return 0.0
    return overlap / union


def cosine_similarity(a: Iterable[float], b: Iterable[float]) -> float:
    a_list = list(a)
    b_list = list(b)
    if len(a_list) != len(b_list) or not a_list or not b_list:
        return 0.0

    dot = sum(x * y for x, y in zip(a_list, b_list))
    mag_a = math.sqrt(sum(x * x for x in a_list))
    mag_b = math.sqrt(sum(x * x for x in b_list))
    if mag_a == 0 or mag_b == 0:
        return 0.0
    return dot / (mag_a * mag_b)


def _build_resume_text(row: Dict[str, Any]) -> str:
    return " ".join(
        part for part in [
            row.get("skills"),
            row.get("qualifications"),
            row.get("experience"),
            row.get("raw_text"),
        ]
        if part and str(part).strip()
    )


def score_candidate(vacancy_text: str, resume_text: str, vacancy_vector: List[float], resume_vector: List[float]) -> Dict[str, Any]:
    semantic_similarity = cosine_similarity(vacancy_vector, resume_vector)
    keyword_score = keyword_overlap_score(vacancy_text, resume_text)
    rerank_score = RerankerService.rerank_pair(vacancy_text, resume_text)
    final_score = max(0.0, min(1.0, ((semantic_similarity + rerank_score) / 2.0)))
    if final_score == 0.0 and keyword_score > 0.0:
        final_score = max(0.0, min(1.0, (0.7 * semantic_similarity) + (0.3 * keyword_score)))
    return {
        "semantic_similarity": semantic_similarity,
        "keyword_overlap": keyword_score,
        "rerank_score": rerank_score,
        "score": final_score,
        "match_percentage": int(round(final_score * 100)),
    }


def generate_ranked_candidates(vacancy_id: int, vacancy_text: str, limit: int = 10) -> List[Dict[str, Any]]:
    candidate_rows = fetch_candidates(limit=limit * 5)
    if not candidate_rows:
        return []

    vacancy_vector = EmbeddingService.embed_text(vacancy_text)
    create_vacancy_embedding(vacancy_id, vacancy_vector, source="vacancy")

    ranked: List[Dict[str, Any]] = []
    for row in candidate_rows:
        resume_text = _build_resume_text(row)
        if not resume_text:
            continue

        candidate_vector = EmbeddingService.embed_text(resume_text)
        create_candidate_embedding(int(row["id"]), candidate_vector, source="resume")

        result = score_candidate(vacancy_text, resume_text, vacancy_vector, candidate_vector)
        ranked.append({
            "candidate_id": int(row["id"]),
            "candidate_name": row.get("full_name") or "Unknown Candidate",
            "email": row.get("email"),
            "score": result["score"],
            "semantic_similarity": result["semantic_similarity"],
            "rerank_score": result["rerank_score"],
            "keyword_overlap": result["keyword_overlap"],
            "match_percentage": result["match_percentage"],
        })

    ranked.sort(key=lambda item: item["score"], reverse=True)
    return ranked[:limit]


def find_semantic_matches(vacancy_id: int, vacancy_text: str, limit: int = 10) -> List[Dict[str, Any]]:
    vacancy_vector = EmbeddingService.embed_text(vacancy_text)
    create_vacancy_embedding(vacancy_id, vacancy_vector, source="vacancy")
    db_matches = find_similar_candidates(vacancy_vector, limit=limit)
    if not db_matches:
        return []

    reranked = []
    for item in db_matches:
        resume_text = str(item.get("raw_text") or "")
        rerank_score = RerankerService.rerank_pair(vacancy_text, resume_text)
        semantic = float(item.get("similarity") or 0.0)
        final_score = max(0.0, min(1.0, ((semantic + rerank_score) / 2.0)))
        item["semantic_similarity"] = semantic
        item["rerank_score"] = rerank_score
        item["score"] = final_score
        item["match_percentage"] = int(round(final_score * 100))
        reranked.append(item)
    reranked.sort(key=lambda item: item["score"], reverse=True)
    return reranked[:limit]
