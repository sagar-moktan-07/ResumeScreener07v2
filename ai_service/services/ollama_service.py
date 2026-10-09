from __future__ import annotations

import json
from typing import Any, Dict

import httpx

try:
    from ai_service.config import OLLAMA_MODEL, OLLAMA_URL
except ModuleNotFoundError:  # pragma: no cover - fallback for repo-root package path
    from Resume_Screener_09.ai_service.config import OLLAMA_MODEL, OLLAMA_URL


class OllamaService:
    @staticmethod
    async def generate(prompt: str) -> Dict[str, Any]:
        async with httpx.AsyncClient(timeout=120.0) as client:
            response = await client.post(
                OLLAMA_URL,
                json={
                    "model": OLLAMA_MODEL,
                    "prompt": prompt,
                    "stream": False,
                },
            )
            response.raise_for_status()
            return response.json()
