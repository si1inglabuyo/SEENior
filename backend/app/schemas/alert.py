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
    # When the phone's detector actually fired (migration 0009). Optional for older clients.
    triggered_at: datetime | None = None


class AlertOut(BaseModel):
    sync_id: UUID
    risk_level: RiskLevel
    trigger_type: TriggerType
    status: AlertStatus
    location_cluster_id: str | None
    escalation_steps: list | None
    created_at: datetime
    triggered_at: datetime | None
    resolved_at: datetime | None

    model_config = {"from_attributes": True}


class AlertCancel(BaseModel):
    """The senior's own phone closing an alert it raised.

    Carries both sync_ids, since the senior has no account and holding both is the only
    credential.
    """

    senior_sync_id: UUID


class AlertSeverityUpdate(BaseModel):
    """The senior's phone reporting that an open alert has got worse (re-classified).

    Carries both sync_ids, like AlertCancel.
    """

    senior_sync_id: UUID
    risk_level: RiskLevel


class AlertLocationUpdate(BaseModel):
    """The senior's phone reporting where an already-sent alert happened.

    An SOS posts when its cancel window ends, which can be before the GPS fix arrives, so
    the location is sent afterwards. Carries both sync_ids, like AlertCancel.
    """

    senior_sync_id: UUID
    location_cluster_id: str


class AlertDispatchRequest(BaseModel):
    reason: str
    notes: str | None = None
