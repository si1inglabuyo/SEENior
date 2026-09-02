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

    # Whether anyone sits on the family tier for this senior. Not a privacy field -- it
    # says nothing about who the family are -- but it changes what the incident means to
    # the responder. False is "there is nobody else, this is on you"; True is "the family
    # have had their turn and did not answer". Same signal the escalation clock uses.
    senior_has_family_contact: bool


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


class BarangayContactOut(BaseModel):
    """One family contact on a senior's record, for the responder's Senior Details page.

    Name/phone/email come from the linked Users row; the relationship label ("daughter",
    "son", ...) from the pairing. Barangay-responder contacts are not included -- the
    responder is looking at who ELSE can be called, and that is the family.
    """

    name: str
    relationship_label: str | None
    phone: str | None
    email: str | None


class BarangaySeniorDetail(BaseModel):
    """Full record for one senior: profile, family contacts, and their own alert history.

    Still metadata only (CLAUDE.md §11) -- no sensor readings, no coordinates. `living_
    arrangement` is *derived* from whether an active family contact exists, because the
    onboarding `living_arrangement` answer lives in the phone's local database and never
    syncs; it is display text on this screen and nothing routes on it.
    """

    sync_id: UUID
    first_name: str
    last_name: str
    age: int
    gender: str
    address: str
    mobile_number: str
    living_arrangement: str
    has_family_contact: bool
    contacts: list[BarangayContactOut]
    alerts: list[BarangayAlertOut]


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

    # Dashboard stat-card figures. All scoped to this responder's barangay and reckoned
    # against the database clock (see db_now) so "today" means the same day the stored
    # timestamps were written in.
    resolved_today: int = 0
    sos_today: int = 0
    sos_last_at: datetime | None = None
    seniors_added_this_month: int = 0
    # Non-pending alert counts for the two days, backing the "N from yesterday" delta on
    # the Active Alerts card. A per-day volume, not a snapshot of how many were open.
    alerts_today_total: int = 0
    alerts_yesterday_total: int = 0
