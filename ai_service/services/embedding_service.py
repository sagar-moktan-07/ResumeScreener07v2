from __future__ import annotations

import os
from typing import List, Sequence

try:
    from ai_service.config import EMBEDDING_MODEL
except ModuleNotFoundError:  # pragma: no cover - fallback for repo-root package path
    from Resume_Screener_09.ai_service.config import EMBEDDING_MODEL


class EmbeddingService:
    _model = None

    @classmethod
    def get_model(cls):
        if cls._model is None:
            from sentence_transformers import SentenceTransformer

            cls._model = SentenceTransformer(EMBEDDING_MODEL)
        return cls._model

    @classmethod
    def embed_text(cls, text: str) -> List[float]:
        if not text or not text.strip():
            return []
        model = cls.get_model()
        vector = model.encode(text, normalize_embeddings=True)
        return vector.tolist()

    @classmethod
    def embed_batch(cls, texts: Sequence[str]) -> List[List[float]]:
        if not texts:
            return []
        model = cls.get_model()
        vectors = model.encode(list(texts), normalize_embeddings=True)
        return vectors.tolist()
