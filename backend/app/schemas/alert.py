from datetime import datetime
from uuid import UUID

from pydantic import BaseModel

from app.db.models import AlertStatus, RiskLevel, TriggerType


class AlertCreate(BaseModel):
    senior_sync_id: UUID
    risk_level: RiskLevel
    trigger_type: TriggerType
    location_cluster_id: str | None = None
    escalation_steps: list | None = None


class AlertOut(BaseModel):
    sync_id: UUID
    risk_level: RiskLevel
    trigger_type: TriggerType
    status: AlertStatus
    location_cluster_id: str | None
    escalation_steps: list | None
    created_at: datetime
    resolved_at: datetime | None

    model_config = {"from_attributes": True}


class AlertCancel(BaseModel):
    """The senior's own phone closing an alert it raised.

    Carries the senior's sync_id as well as the alert's. The senior has no account to sign in
    with (CLAUDE.md 2), so a UUID in the path is the only credential available -- and unlike
    generating an invite code, this one closes an emergency. Requiring both ids means a caller
    has to hold the alert's id *and* prove it belongs to the senior they claim, which is what
    the senior's own device is the only thing that naturally does.
    """

    senior_sync_id: UUID


class AlertSeverityUpdate(BaseModel):
    """The senior's phone reporting that an already-sent alert has got worse.

    Layer 1 re-scores every sample, so an alert posted as Medium can be re-classified High
    minutes later while it is still open. Without this the cloud copy keeps the level it was
    created with, and the family app and the barangay dashboard show Medium for an incident the
    phone now calls High -- measured on 2026-09-01, five hours apart.

    Carries the senior's sync_id for the same reason AlertCancel does: the senior has no account
    (CLAUDE.md 2), so holding both ids is the only credential available.
    """

    senior_sync_id: UUID
    risk_level: RiskLevel


class AlertDispatchRequest(BaseModel):
    reason: str
    notes: str | None = None
