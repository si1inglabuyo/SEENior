from datetime import datetime
from uuid import UUID

from pydantic import BaseModel

from app.db.models import ContactType
from app.schemas.auth import Token
from app.schemas.senior import SeniorOut


class InviteCodeOut(BaseModel):
    code: str
    expires_at: datetime


class VerifyCodeRequest(BaseModel):
    invite_code: str


class InviteSeniorOut(BaseModel):
    """The redacted senior returned by the unauthenticated code check.

    The code is the credential, so this returns only what the Connected screen shows
    (first name, last name, age, gender, barangay). `address` and `mobile_number` keep
    their names so the installed Android app still parses them, but hold a redacted value.
    """

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

    @classmethod
    def redacted(cls, senior) -> "InviteSeniorOut":
        return cls(
            sync_id=senior.sync_id,
            first_name=senior.first_name,
            last_name=senior.last_name,
            age=senior.age,
            gender=senior.gender,
            barangay=senior.barangay,
            # Barangay only, which the caller already sees.
            address=senior.barangay,
            mobile_number="•••••••••••",
            created_at=senior.created_at,
        )


class VerifyCodeResponse(BaseModel):
    """Returned when a family member checks a code on the Link screen. Nothing is created
    until POST /contacts/pair."""
    senior: InviteSeniorOut


class PairRequest(BaseModel):
    """Requires an authenticated caller; links the logged-in account to a senior."""
    invite_code: str
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
    """Senior-side view of a family member on the Contacts list (flattened from the User)."""
    id: int
    full_name: str | None
    phone: str | None
    relationship_label: str | None
    contact_type: ContactType
    created_at: datetime
    # Latest time any of this contact's devices registered its FCM token (done on every
    # launch). A "recently opened the app" proxy, not live presence. Null if none.
    last_active_at: datetime | None = None

    model_config = {"from_attributes": True}
