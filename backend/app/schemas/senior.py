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
    """Why a senior is deleting their account. `reason` is a stable code from the
    app's reason picker (not the translated label); `note` is optional free text."""

    reason: str = Field(min_length=1, max_length=64)
    note: str | None = Field(default=None, max_length=500)


class SeniorHeartbeat(BaseModel):
    """What the senior's phone reports when it checks in.

    Both fields are optional because a reading can genuinely be unavailable, and a
    check-in that says nothing but "I am still running" is still worth recording -- that
    alone is what tells the family monitoring has not stopped.
    """

    battery_percent: int | None = Field(default=None, ge=0, le=100)
    is_charging: bool | None = None
    # Rides along on the check-in instead of having an endpoint of its own: one call
    # site cannot drift out of step with another, and the token is refreshed on exactly
    # the schedule that already proves this phone is alive. Optional because a handset
    # with no Play Services, or one whose token request failed, still checks in.
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
    # Device health, not behaviour -- see the Senior model. Null until the phone has
    # checked in at least once.
    last_seen_at: datetime | None = None
    battery_percent: int | None = None
    is_charging: bool | None = None
    # Roster state, so a list of seniors can be rendered with its badges from one call.
    # Defaulted rather than required: a client reading an older server simply sees every
    # senior as active, which is what they were before the column existed.
    status: SeniorStatus = SeniorStatus.ACTIVE

    model_config = {"from_attributes": True}
