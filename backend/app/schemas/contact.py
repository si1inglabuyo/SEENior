from datetime import datetime

from pydantic import BaseModel

from app.db.models import ContactType
from app.schemas.auth import Token
from app.schemas.senior import SeniorOut


class InviteCodeOut(BaseModel):
    code: str
    expires_at: datetime


class PairRequest(BaseModel):
    invite_code: str
    username: str
    password: str


class ContactOut(BaseModel):
    id: int
    contact_type: ContactType
    created_at: datetime
    senior: SeniorOut

    model_config = {"from_attributes": True}


class PairResponse(BaseModel):
    contact: ContactOut
    token: Token
