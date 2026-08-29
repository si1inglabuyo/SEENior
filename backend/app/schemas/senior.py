from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, Field


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

    model_config = {"from_attributes": True}
