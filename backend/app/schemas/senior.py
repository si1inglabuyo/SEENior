from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, Field

from app.db.models import SeniorStatus


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


class SeniorDeletionRequest(BaseModel):
    """Why a senior is deleting their account: a stable `reason` code from the picker and
    an optional free-text `note`."""

    reason: str = Field(min_length=1, max_length=64)
    note: str | None = Field(default=None, max_length=500)


class SeniorHeartbeat(BaseModel):
    """What the senior's phone reports when it checks in.

    Both fields are optional; a check-in with no readings is still worth recording.
    """

    battery_percent: int | None = Field(default=None, ge=0, le=100)
    is_charging: bool | None = None
    # Sent with the check-in so the token is refreshed on the same schedule. Optional for
    # phones without Play Services.
    push_token: str | None = Field(default=None, max_length=255)


class SeniorStatusUpdate(BaseModel):
    """Body of PATCH /seniors/{sync_id}/status."""

    status: SeniorStatus


class SeniorStatusOut(BaseModel):
    """What the flip returns, so the dashboard can render the new state without refetching."""

    sync_id: UUID
    status: SeniorStatus
    status_changed_at: datetime | None = None
    status_changed_by: int | None = None

    model_config = {"from_attributes": True}


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
    # Device health, not behaviour. Null until the phone has checked in.
    last_seen_at: datetime | None = None
    battery_percent: int | None = None
    is_charging: bool | None = None
    # Roster state, defaulting to active for clients reading an older server.
    status: SeniorStatus = SeniorStatus.ACTIVE

    model_config = {"from_attributes": True}


class ClosedAlertOut(BaseModel):
    """One of this senior's alerts that family or the barangay has closed.

    Just enough for the phone to stop showing it as open.
    """

    sync_id: UUID
    status: str  # "resolved" or "false_positive"

    model_config = {"from_attributes": True}
