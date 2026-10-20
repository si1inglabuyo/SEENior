from datetime import datetime
from uuid import UUID

from pydantic import BaseModel

from app.db.models import AlertStatus, RiskLevel, SeniorStatus, TriggerType


class BarangayAlertOut(BaseModel):
    """One incident, shaped for the responder's screen.

    Wider than AlertOut because the responder has to be told who and where. Sharing the
    senior's name, address and alert-time location is allowed during an active alert
    (RA 10173 12(c), vital interests). Still no sensor readings or behavioural history.
    """

    sync_id: UUID
    risk_level: RiskLevel
    trigger_type: TriggerType
    status: AlertStatus
    escalation_steps: list | None
    created_at: datetime
    resolved_at: datetime | None
    # Position when the alert fired, as a geohash (precise since 2026-08-31; older rows
    # ~150 m). Captured once at trigger time; null if no fix. Not anonymised, despite the
    # column name; it is held under RA 10173 12(c).
    location_cluster_id: str | None

    senior_sync_id: UUID
    senior_name: str
    senior_age: int
    # None if the senior skipped gender; the dashboard then omits the line.
    senior_gender: str | None
    senior_address: str
    senior_mobile: str
    # The pook (area of the barangay) the senior lives in, so the responder knows which area
    # the alert is in. Null when the senior never chose one. A label only.
    senior_pook: str | None = None
    senior_sector: str | None = None

    # Fall alerts only: what each detector gate measured, so the responder (and a panel) can see
    # which phases satisfied the alert. Summary figures, not sensor samples. Null otherwise.
    fall_evidence: str | None = None
    # The thresholds those figures were judged against (see alerts.fall_tolerance).
    fall_tolerance: str | None = None
    # What the phone was doing just before the drop and whether it was the senior's night block
    # (alerts.fall_context). Two derived figures and a flag. Null otherwise.
    fall_context: str | None = None

    # Whether the senior has any family contact. False means nobody else was notified.
    # Same signal the escalation clock uses.
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
    pook: str | None = None
    sector: str | None = None
    # The responder's own roster state. Inactive seniors are still listed and their alerts
    # still arrive.
    status: SeniorStatus
    # Device health, not behaviour. Null means the phone never checked in.
    last_seen_at: datetime | None
    battery_percent: int | None
    is_charging: bool | None
    open_incidents: int

    model_config = {"from_attributes": True}


class BarangayContactOut(BaseModel):
    """One family contact on a senior's record, for the Senior Details page.

    Name/phone/email come from the Users row, the relationship label from the pairing.
    Barangay responders are not included.
    """

    name: str
    relationship_label: str | None
    phone: str | None
    email: str | None


class BarangaySeniorDetail(BaseModel):
    """Full record for one senior: profile, family contacts and alert history.

    Metadata only. `living_arrangement` is derived from whether an active family contact
    exists (the onboarding answer never syncs), and is display text only.
    """

    sync_id: UUID
    first_name: str
    last_name: str
    age: int
    gender: str
    address: str
    mobile_number: str
    pook: str | None = None
    sector: str | None = None
    status: SeniorStatus
    living_arrangement: str
    has_family_contact: bool
    # Device health, same fields as the roster.
    last_seen_at: datetime | None
    battery_percent: int | None
    is_charging: bool | None
    contacts: list[BarangayContactOut]
    alerts: list[BarangayAlertOut]


class ResponderAction(BaseModel):
    """What a responder types when acting on an incident. Optional, so an urgent dispatch
    is never blocked."""

    notes: str | None = None


class DayCount(BaseModel):
    day: str  # ISO date, e.g. "2026-08-26"
    count: int


class BarangayStats(BaseModel):
    seniors_monitored: int
    open_incidents: int
    alerts_this_week: list[DayCount]
    # Keyed by alert status, plus `attending` (acknowledged escalated alerts, counted
    # there instead of under `escalated`).
    outcomes: dict[str, int]

    # This week's alerts by category: `anomaly`, `potential_fall`, `sos`, `dispatch_family`.
    # Same rows as the bar chart and outcome donut. Backs the "Alerts by Type" donut.
    alert_categories: dict[str, int] = {}

    # Dashboard stat-card figures, scoped to this barangay and the database clock.
    resolved_today: int = 0
    sos_today: int = 0
    sos_last_at: datetime | None = None
    seniors_added_this_month: int = 0
    # Non-pending alert counts for two days, for the "N from yesterday" delta.
    alerts_today_total: int = 0
    alerts_yesterday_total: int = 0
