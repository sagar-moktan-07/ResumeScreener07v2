from __future__ import annotations

import ast
from typing import Any, Dict, List, Optional

from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine

try:
    from ai_service.config import DATABASE_URL
except ModuleNotFoundError:  # pragma: no cover - fallback for repo-root package path
    from Resume_Screener_09.ai_service.config import DATABASE_URL

engine: Engine = create_engine(DATABASE_URL, pool_pre_ping=True)


def _vector_to_string(values: Any) -> str:
    if isinstance(values, str):
        return values.strip()
    if values is None:
        return "[]"
    return "[" + ",".join(str(float(v)) for v in values) + "]"


def _parse_vector(value: Any) -> List[float]:
    if value is None:
        return []
    if isinstance(value, str):
        cleaned = value.strip()
        if cleaned.startswith("[") and cleaned.endswith("]"):
            try:
                parsed = ast.literal_eval(cleaned)
                if isinstance(parsed, list):
                    return [float(v) for v in parsed]
            except (ValueError, SyntaxError):
                pass
        return [float(part.strip()) for part in cleaned.strip("[]").split(",") if part.strip()]
    if isinstance(value, (list, tuple)):
        return [float(v) for v in value]
    return [float(value)]


def fetch_candidates(limit: int = 50) -> List[Dict[str, Any]]:
    with engine.begin() as conn:
        rows = conn.execute(
            text(
                """
                SELECT id, file_name, full_name, email, phone, raw_text, skills, qualifications, experience
                FROM candidate_resume
                ORDER BY id DESC
                LIMIT :limit
                """
            ),
            {"limit": limit},
        )
        return [dict(r._mapping) for r in rows]


def fetch_vacancy(vacancy_id: int) -> Optional[Dict[str, Any]]:
    with engine.begin() as conn:
        row = conn.execute(
            text(
                """
                SELECT id, job_title, department, job_description, required_skills, preferred_skills,
                       qualifications, education_requirements, experience_requirements,
                       responsibilities, other_requirements, status
                FROM vacancy_post
                WHERE id = :vacancy_id
                """
            ),
            {"vacancy_id": vacancy_id},
        ).first()
    if row is None:
        return None
    return dict(row._mapping)


def fetch_semifinalized_candidates(vacancy_id: Optional[int] = None, limit: int = 5) -> List[Dict[str, Any]]:
    with engine.begin() as conn:
        params: Dict[str, Any] = {"limit": limit}
        query = text(
            """
            SELECT sc.id, sc.vacancy_id, sc.candidate_id, sc.score, sc.status,
                   cr.full_name, cr.email, cr.skills, cr.qualifications
            FROM semifinalized_candidate sc
            LEFT JOIN candidate_resume cr ON cr.id = sc.candidate_id
            """
        )
        if vacancy_id is not None:
            query = text(
                """
                SELECT sc.id, sc.vacancy_id, sc.candidate_id, sc.score, sc.status,
                       cr.full_name, cr.email, cr.skills, cr.qualifications
                FROM semifinalized_candidate sc
                LEFT JOIN candidate_resume cr ON cr.id = sc.candidate_id
                WHERE sc.vacancy_id = :vacancy_id
                ORDER BY sc.score DESC NULLS LAST, sc.id DESC
                LIMIT :limit
                """
            )
            params["vacancy_id"] = vacancy_id
        else:
            query = text(
                """
                SELECT sc.id, sc.vacancy_id, sc.candidate_id, sc.score, sc.status,
                       cr.full_name, cr.email, cr.skills, cr.qualifications
                FROM semifinalized_candidate sc
                LEFT JOIN candidate_resume cr ON cr.id = sc.candidate_id
                ORDER BY sc.score DESC NULLS LAST, sc.id DESC
                LIMIT :limit
                """
            )
        rows = conn.execute(query, params)
        return [dict(row._mapping) for row in rows]


def create_candidate_embedding(candidate_id: int, embedding: List[float], source: str = "resume") -> None:
    with engine.begin() as conn:
        conn.execute(
            text(
                """
                INSERT INTO candidate_embedding (candidate_id, embedding, source)
                VALUES (:candidate_id, CAST(:embedding AS vector), :source)
                """
            ),
            {"candidate_id": candidate_id, "embedding": _vector_to_string(embedding), "source": source},
        )


def create_vacancy_embedding(vacancy_id: int, embedding: List[float], source: str = "vacancy") -> None:
    with engine.begin() as conn:
        conn.execute(
            text(
                """
                INSERT INTO vacancy_embedding (vacancy_id, embedding, source)
                VALUES (:vacancy_id, CAST(:embedding AS vector), :source)
                """
            ),
            {"vacancy_id": vacancy_id, "embedding": _vector_to_string(embedding), "source": source},
        )


def fetch_candidate_embeddings() -> List[Dict[str, Any]]:
    with engine.begin() as conn:
        rows = conn.execute(
            text(
                """
                SELECT id, candidate_id, source, embedding::text AS embedding_text
                FROM candidate_embedding
                ORDER BY id DESC
                """
            )
        )
        results: List[Dict[str, Any]] = []
        for row in rows:
            mapping = dict(row._mapping)
            mapping["embedding"] = _parse_vector(mapping.get("embedding_text"))
            results.append(mapping)
        return results


def fetch_vacancy_embeddings() -> List[Dict[str, Any]]:
    with engine.begin() as conn:
        rows = conn.execute(
            text(
                """
                SELECT id, vacancy_id, source, embedding::text AS embedding_text
                FROM vacancy_embedding
                ORDER BY id DESC
                """
            )
        )
        results: List[Dict[str, Any]] = []
        for row in rows:
            mapping = dict(row._mapping)
            mapping["embedding"] = _parse_vector(mapping.get("embedding_text"))
            results.append(mapping)
        return results


def find_similar_candidates(vacancy_embedding: List[float], limit: int = 10) -> List[Dict[str, Any]]:
    with engine.begin() as conn:
        rows = conn.execute(
            text(
                """
                SELECT ce.id, ce.candidate_id, ce.source,
                       1 - (ce.embedding <=> CAST(:vacancy_embedding AS vector)) AS similarity
                FROM candidate_embedding ce
                ORDER BY ce.embedding <=> CAST(:vacancy_embedding AS vector)
                LIMIT :limit
                """
            ),
            {"vacancy_embedding": _vector_to_string(vacancy_embedding), "limit": limit},
        )
        result: List[Dict[str, Any]] = []
        for row in rows:
            mapping = dict(row._mapping)
            mapping["similarity"] = float(mapping.get("similarity", 0.0))
            result.append(mapping)
        return result
