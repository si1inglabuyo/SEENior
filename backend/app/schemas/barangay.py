from datetime import datetime
from uuid import UUID

from pydantic import BaseModel

from app.db.models import AlertStatus, RiskLevel, TriggerType


class BarangayAlertOut(BaseModel):
    """One incident, shaped for the responder's screen.

    Wider than AlertOut on purpose. A family member already knows who their senior is and
    where they live; a responder is being asked to go to a house and has to be told.

    Sharing the senior's name and address with the barangay during an active alert is
    permitted under RA 10173 §12(c), the vital-interests provision (CLAUDE.md §11) -- an
    explicit exception, not a privacy hole. Note what is still absent: no sensor readings,
    no coordinates, no behavioural history. Only who, where, and what happened.
    """

    sync_id: UUID
    risk_level: RiskLevel
    trigger_type: TriggerType
    status: AlertStatus
    escalation_steps: list | None
    created_at: datetime
    resolved_at: datetime | None

    senior_sync_id: UUID
    senior_name: str
    senior_age: int
    senior_address: str
    senior_mobile: str


class BarangaySeniorOut(BaseModel):
    """A senior record in this responder's barangay, for the roster screen."""

    sync_id: UUID
    first_name: str
    last_name: str
    age: int
    gender: str
    address: str
    mobile_number: str
    # Device health, not behaviour. Null means the phone has never checked in at all,
    # which is a different answer from "checked in, battery at 0".
    last_seen_at: datetime | None
    battery_percent: int | None
    is_charging: bool | None
    open_incidents: int

    model_config = {"from_attributes": True}


class ResponderAction(BaseModel):
    """What a responder types when acting on an incident. Optional -- an urgent dispatch
    must never be blocked behind a required text box."""

    notes: str | None = None


class DayCount(BaseModel):
    day: str  # ISO date, e.g. "2026-08-26"
    count: int


class BarangayStats(BaseModel):
    seniors_monitored: int
    open_incidents: int
    alerts_this_week: list[DayCount]
    outcomes: dict[str, int]
