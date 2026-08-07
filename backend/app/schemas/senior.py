from datetime import datetime
from uuid import UUID

from pydantic import BaseModel


class SeniorCreate(BaseModel):
    first_name: str
    last_name: str
    age: int
    gender: str
    barangay: str
    address: str
    mobile_number: str


class SeniorUpdate(BaseModel):
    first_name: str
    last_name: str
    age: int
    gender: str
    barangay: str
    address: str
    mobile_number: str


class SeniorOut(BaseModel):
    sync_id: UUID
    first_name: str
    last_name: str
    age: int
    gender: str
    barangay: str
    address: str
    mobile_number: str
    created_at: datetime

    model_config = {"from_attributes": True}
