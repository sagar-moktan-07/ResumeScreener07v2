from typing import List, Optional

from pydantic import BaseModel, Field


class DataReadyRequest(BaseModel):
    event: str = Field(..., description="Event name from the Java app")
    batch_id: Optional[str] = None
    candidate_ids: Optional[List[int]] = None
    vacancy_id: Optional[int] = None
    count: Optional[int] = None


class ScreeningRequest(BaseModel):
    vacancy_id: int
    candidate_id: int
    vacancy_text: str = ""
    resume_text: str = ""


class HealthResponse(BaseModel):
    status: str
    service: str
    version: str
    db_ready: bool = False
    embedding_model: str = ""
