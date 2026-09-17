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
    """The redacted view of a senior handed back by the *unauthenticated* code check.

    `POST /contacts/verify` needs no credentials by design -- the code is the credential --
    so whatever it returns is readable by anyone who guesses a live six-digit code. It used
    to return the full `SeniorOut`, which meant a correct guess disclosed a senior's home
    address and mobile number to a stranger.

    The fields kept here are exactly the five the Connected screen renders (first name, last
    name, age, gender, barangay) -- enough for a family member to recognise their own
    relative and no more. `address` and `mobile_number` keep their names and types so the
    installed Android DTO still parses, but carry a redacted value: the screen never reads
    either, and the pairing that follows is authenticated, after which the full record is
    available through the normal contact endpoints.
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
            # Barangay only -- the same granularity the caller already sees in `barangay`,
            # so this adds nothing and leaks nothing.
            address=senior.barangay,
            mobile_number="•••••••••••",
            created_at=senior.created_at,
        )


class VerifyCodeResponse(BaseModel):
    """Returned when a family member checks a code on the Link screen — shows the
    senior on the Connected screen BEFORE anything is committed. No account is
    created here; that happens on POST /contacts/pair after they pick a relationship."""
    senior: InviteSeniorOut


class PairRequest(BaseModel):
    """Requires an authenticated caller (POST /auth/register or /auth/google happens
    first, separately) - this only links the already-logged-in account to a senior."""
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
    """Senior-side view: a family member on the senior's Contacts list
    (their name, phone, and relationship — flattened from the linked User)."""
    id: int
    full_name: str | None
    phone: str | None
    relationship_label: str | None
    contact_type: ContactType
    created_at: datetime
    # The most recent time any of this contact's devices registered its FCM token —
    # which the family app does on every launch. A "recently opened the app" proxy, not
    # live presence; the senior's Contacts screen turns it into "Active … ago". Null when
    # the contact has never registered a device.
    last_active_at: datetime | None = None

    model_config = {"from_attributes": True}
