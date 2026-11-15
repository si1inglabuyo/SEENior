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


# ---- An alert the senior closed inside the window (migration 0027) ----------------------------

from app.schemas.alert import OutcomeRecordCreate  # noqa: E402


def _record(monkeypatch, trigger, code, existing=None, senior_deleted=False):
    senior = SimpleNamespace(
        id=7, sync_id=uuid4(), deleted_at=object() if senior_deleted else None,
    )
    results = [
        SimpleNamespace(scalar_one_or_none=lambda: senior),
        SimpleNamespace(scalar_one_or_none=lambda: existing),
    ]
    db = AsyncMock()
    db.execute.side_effect = results
    db.add = lambda row: added.append(row)
    added: list = []
    monkeypatch.setattr(alerts, "check_device_key", lambda s, key: None)
    monkeypatch.setattr(alerts.ratelimit, "check", AsyncMock())
    body = OutcomeRecordCreate(
        senior_sync_id=senior.sync_id, alert_sync_id=uuid4(), trigger_type=trigger,
        risk_level="high", time_block="night", outcome_reason=code,
    )
    out = asyncio.run(alerts.record_alert_outcome(body, None, db))
    return out, added, db


def test_a_cancelled_fall_answer_is_kept_as_its_own_row(monkeypatch):
    out, added, db = _record(monkeypatch, "fall_pattern", "phone_dropped")
    assert len(added) == 1 and added[0].outcome_reason == "phone_dropped"
    assert added[0].trigger_type == "fall_pattern" and added[0].time_block == "night"
    db.commit.assert_awaited_once()


def test_it_carries_nothing_but_the_agreed_fields():
    fields = set(OutcomeRecordCreate.model_fields)
    assert fields == {
        "senior_sync_id", "alert_sync_id", "trigger_type", "risk_level",
        "time_block", "triggered_at", "outcome_reason",
    }


def test_a_retry_returns_the_first_row_and_adds_none(monkeypatch):
    first = SimpleNamespace(alert_sync_id=uuid4(), outcome_reason="phone_dropped")
    out, added, db = _record(monkeypatch, "fall_pattern", "fell_fine", existing=first)
    assert out is first and added == []
    db.commit.assert_not_awaited()


def test_an_sos_or_a_mismatched_code_is_refused(monkeypatch):
    with pytest.raises(HTTPException) as e:
        _record(monkeypatch, "sos", "other")
    assert e.value.status_code == 422
    with pytest.raises(HTTPException):
        _record(monkeypatch, "inactivity", "phone_dropped")


def test_a_deleted_senior_gets_a_404(monkeypatch):
    with pytest.raises(HTTPException) as e:
        _record(monkeypatch, "fall_pattern", "other", senior_deleted=True)
    assert e.value.status_code == 404
