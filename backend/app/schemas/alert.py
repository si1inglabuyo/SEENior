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


class AlertDispatchRequest(BaseModel):
    reason: str
    notes: str | None = None
