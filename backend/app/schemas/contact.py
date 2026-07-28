from datetime import datetime

from pydantic import BaseModel

from app.db.models import ContactType
from app.schemas.auth import Token
from app.schemas.senior import SeniorOut


class InviteCodeOut(BaseModel):
    code: str
    expires_at: datetime


class VerifyCodeRequest(BaseModel):
    invite_code: str


class VerifyCodeResponse(BaseModel):
    """Returned when a family member checks a code on the Link screen — shows the
    senior on the Connected screen BEFORE anything is committed. No account is
    created here; that happens on POST /contacts/pair after they pick a relationship."""
    senior: SeniorOut


class PairRequest(BaseModel):
    invite_code: str
    # Family member's own identity (stands in for the separate account-setup signup).
    full_name: str
    phone: str
    username: str
    password: str
    # How they relate to the senior — chosen on the Connected screen.
    relationship_label: str


class ContactOut(BaseModel):
    """Family-side view: which senior this contact links me to."""
    id: int
    contact_type: ContactType
    relationship_label: str | None = None
    created_at: datetime
    senior: SeniorOut

    model_config = {"from_attributes": True}


class PairResponse(BaseModel):
    contact: ContactOut
    token: Token


class FamilyContactOut(BaseModel):
    """Senior-side view: a family member on the senior's Contacts list
    (their name, phone, and relationship — flattened from the linked User)."""
    id: int
    full_name: str | None
    phone: str | None
    relationship_label: str | None
    contact_type: ContactType
    created_at: datetime

    model_config = {"from_attributes": True}
