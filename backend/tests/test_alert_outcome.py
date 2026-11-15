"""After "I'm safe" the senior may say what happened. It is one fixed code, kept as a record: it never
changes the alert's status and is never overwritten."""

import asyncio
from types import SimpleNamespace
from unittest.mock import AsyncMock
from uuid import uuid4

import pytest
from fastapi import HTTPException
from pydantic import ValidationError

from app.api.routes import alerts
from app.db.models import AlertStatus, TriggerType
from app.schemas.alert import ANOMALY_OUTCOMES, FALL_OUTCOMES, AlertOutcome


def _alert(trigger=TriggerType.FALL_PATTERN, status=AlertStatus.RESOLVED, outcome=None):
    senior = SimpleNamespace(sync_id=uuid4(), first_name="Rosa")
    return SimpleNamespace(
        trigger_type=trigger, status=status, resolved_at=None, senior=senior,
        escalation_steps=[{"step": "self_cancelled_senior"}], outcome_reason=outcome,
    )


def _report(monkeypatch, alert, code):
    db = AsyncMock()
    db.execute.return_value = SimpleNamespace(scalar_one_or_none=lambda: alert)
    monkeypatch.setattr(alerts, "check_device_key", lambda senior, key: None)
    body = AlertOutcome(senior_sync_id=alert.senior.sync_id, outcome_reason=code)
    return asyncio.run(alerts.set_alert_outcome(uuid4(), body, None, db))


def test_the_code_sets_are_exactly_the_agreed_ones():
    assert FALL_OUTCOMES == {"phone_dropped", "fell_fine", "other"}
    assert ANOMALY_OUTCOMES == {"unused_fine", "left_behind_fine", "other"}


def test_free_text_is_refused():
    with pytest.raises(ValidationError):
        AlertOutcome(senior_sync_id=uuid4(), outcome_reason="I dropped it in the kitchen")


@pytest.mark.parametrize("code", sorted(FALL_OUTCOMES))
def test_a_fall_takes_the_fall_codes(monkeypatch, code):
    out = _report(monkeypatch, _alert(TriggerType.FALL_PATTERN), code)
    assert out.outcome_reason == code
    assert out.escalation_steps[-1]["step"] == "outcome_reported"


@pytest.mark.parametrize("trigger", ["inactivity", "movement", "screen_idle", "charging", "ml_flag"])
@pytest.mark.parametrize("code", sorted(ANOMALY_OUTCOMES))
def test_an_anomaly_takes_the_anomaly_codes(monkeypatch, trigger, code):
    out = _report(monkeypatch, _alert(TriggerType(trigger)), code)
    assert out.outcome_reason == code


def test_a_fall_refuses_an_anomaly_code_and_the_reverse(monkeypatch):
    with pytest.raises(HTTPException) as e:
        _report(monkeypatch, _alert(TriggerType.FALL_PATTERN), "unused_fine")
    assert e.value.status_code == 422
    with pytest.raises(HTTPException):
        _report(monkeypatch, _alert(TriggerType.INACTIVITY), "phone_dropped")


def test_an_sos_is_never_asked(monkeypatch):
    with pytest.raises(HTTPException) as e:
        _report(monkeypatch, _alert(TriggerType.SOS), "other")
    assert e.value.status_code == 422


def test_the_first_answer_stays_and_status_is_untouched(monkeypatch):
    alert = _alert(TriggerType.FALL_PATTERN, status=AlertStatus.ESCALATED, outcome="phone_dropped")
    out = _report(monkeypatch, alert, "fell_fine")
    assert out.outcome_reason == "phone_dropped"
    assert out.status == AlertStatus.ESCALATED
    assert len(out.escalation_steps) == 1  # no second step written


def test_somebody_elses_alert_looks_like_no_alert(monkeypatch):
    alert = _alert()
    db = AsyncMock()
    db.execute.return_value = SimpleNamespace(scalar_one_or_none=lambda: alert)
    monkeypatch.setattr(alerts, "check_device_key", lambda senior, key: None)
    body = AlertOutcome(senior_sync_id=uuid4(), outcome_reason="other")  # wrong senior
    with pytest.raises(HTTPException) as e:
        asyncio.run(alerts.set_alert_outcome(uuid4(), body, None, db))
    assert e.value.status_code == 404
