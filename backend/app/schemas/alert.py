from datetime import datetime
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, Field, model_validator

from app.db.models import AlertStatus, RiskLevel, TriggerType


class AlertCreate(BaseModel):
    senior_sync_id: UUID
    risk_level: RiskLevel
    trigger_type: TriggerType
    location_cluster_id: str | None = None
    escalation_steps: list | None = None
    # When the phone's detector actually fired (migration 0009). Optional for older clients.
    triggered_at: datetime | None = None
    # Fall alerts only (migration 0015): thresholds in force plus the counts that earned them.
    # Held to key=value pairs of plain characters so it can only ever carry that, never free text.
    fall_tolerance: str | None = Field(default=None, max_length=200, pattern=r"^[a-z_]+=[0-9.]+(;[a-z_]+=[0-9.]+)*$")
    # Fall alerts only (migration 0017): what each gate measured, four summary figures. Same
    # key=value-numbers-only shape, so it can carry nothing but those figures.
    fall_evidence: str | None = Field(default=None, max_length=200, pattern=r"^[a-z_]+=[0-9.]+(;[a-z_]+=[0-9.]+)*$")
    # Fall alerts only (migration 0019): the movement before the fall and whether it was night.
    # Same key=value-numbers-only shape.
    fall_context: str | None = Field(default=None, max_length=200, pattern=r"^[a-z_]+=[0-9.]+(;[a-z_]+=[0-9.]+)*$")
    # Fall alerts only (migration 0023): the working behind fall_context's pre_dev, one figure per
    # reading in the 2 s before the free fall, oldest first, e.g. "dev=0.0034,0.0034". Held to
    # that exact shape (at most 256 figures, matching the phone's buffer) so it can carry nothing
    # but those figures: no axes, no timestamps, no free text.
    fall_trace: str | None = Field(
        default=None,
        max_length=2600,
        pattern=r"^dev=[0-9]{1,3}(\.[0-9]{1,4})?(,[0-9]{1,3}(\.[0-9]{1,4})?){0,255}$",
    )


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
    # Fall alerts only: what the phone was doing before the drop and the detector setting in force,
    # so the family app can explain a potential fall in plain words. Null otherwise. Two derived
    # figures, a flag and a setting; never sensor samples.
    fall_context: str | None = None
    fall_tolerance: str | None = None
    # What the senior said happened after answering "I'm safe" (migration 0026). A fixed code.
    outcome_reason: str | None = None

    model_config = {"from_attributes": True}


class AlertCancel(BaseModel):
    """The senior's own phone closing an alert it raised.

    Carries both sync_ids, since the senior has no account and holding both is the only
    credential.
    """

    senior_sync_id: UUID
    # "phone_use": the senior merely used their phone, they did not say anything. The server
    # closes the alert only if nobody has started working on it; otherwise it stays open and the
    # unchanged alert comes back. Absent means the senior answered themselves.
    reason: Literal["phone_use"] | None = None


OutcomeReason = Literal[
    "phone_dropped", "fell_fine", "unused_fine", "left_behind_fine", "sos_cancelled", "other"
]

# Which codes make sense for which alert. The phone only offers the right set, so this is the
# server holding the same line against a stray or hand-made request.
FALL_OUTCOMES = {"phone_dropped", "fell_fine", "other"}
ANOMALY_OUTCOMES = {"unused_fine", "left_behind_fine", "other"}
# An SOS is never asked a question; the phone records a cancelled one by itself (migration 0028).
SOS_OUTCOMES = {"sos_cancelled"}


OUTCOME_DETAIL_MAX = 120


def _clean_detail(code: str, detail: str | None) -> str | None:
    """The typed reason belongs only to "Others"; trimmed, and blank means none."""
    if detail is None:
        return None
    detail = " ".join(detail.split())
    if not detail:
        return None
    if code != "other":
        raise ValueError("A typed reason is only allowed with the 'other' answer")
    return detail


class AlertOutcome(BaseModel):
    """The senior's phone reporting the answer to "what happened" after they said they were safe.

    Carries both sync_ids, like AlertCancel. A fixed code, never free text.
    """

    senior_sync_id: UUID
    outcome_reason: OutcomeReason
    # What the senior typed after choosing "Others" (migration 0029). 120 characters, only with `other`.
    outcome_detail: str | None = Field(default=None, max_length=OUTCOME_DETAIL_MAX)

    @model_validator(mode="after")
    def _detail_only_for_other(self):
        self.outcome_detail = _clean_detail(self.outcome_reason, self.outcome_detail)
        return self


class OutcomeRecordCreate(BaseModel):
    """The phone reporting the answer for an alert the senior closed inside the response window.

    That alert never reached the cloud, so there is no `alerts` row to patch; this carries just
    enough to count it. No location, evidence, score or steps (migration 0027).
    """

    senior_sync_id: UUID
    # The phone's own id for the alert. Makes a retry safe: one row per alert.
    alert_sync_id: UUID
    trigger_type: TriggerType
    risk_level: RiskLevel
    time_block: Literal["morning", "afternoon", "evening", "night"]
    triggered_at: datetime | None = None
    outcome_reason: OutcomeReason
    # Seconds from the alert firing to the senior closing it. Optional for an older phone.
    cancelled_after_seconds: int | None = Field(default=None, ge=0, le=86400)
    # What the senior typed after choosing "Others" (migration 0029). 120 characters, only with `other`.
    outcome_detail: str | None = Field(default=None, max_length=OUTCOME_DETAIL_MAX)

    @model_validator(mode="after")
    def _detail_only_for_other(self):
        self.outcome_detail = _clean_detail(self.outcome_reason, self.outcome_detail)
        return self


class OutcomeRecordOut(BaseModel):
    alert_sync_id: UUID
    outcome_reason: str

    model_config = {"from_attributes": True}


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
