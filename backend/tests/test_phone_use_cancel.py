"""The senior using their phone may close an alert the family and barangay already know about,
but only a routine one that nobody has started working on."""

import asyncio
from types import SimpleNamespace
from unittest.mock import AsyncMock
from uuid import uuid4

import pytest
from pydantic import ValidationError

from app.api.routes import alerts
from app.db.models import AlertStatus, TriggerType
from app.schemas.alert import AlertCancel


def _alert(trigger=TriggerType.INACTIVITY, status=AlertStatus.ESCALATED, steps=None):
    senior = SimpleNamespace(sync_id=uuid4(), first_name="Rosa")
    return SimpleNamespace(
        trigger_type=trigger, status=status, resolved_at=None, senior=senior,
        escalation_steps=list(steps or [{"step": "escalated_barangay_auto"}]),
    )


@pytest.mark.parametrize("trigger", ["inactivity", "movement", "screen_idle", "charging", "ml_flag", "fall_pattern"])
def test_routine_anomalies_may_be_closed_by_phone_use(trigger):
    assert alerts.phone_use_may_close(_alert(TriggerType(trigger)))


def test_sos_never_is():
    assert not alerts.phone_use_may_close(_alert(TriggerType.SOS))


@pytest.mark.parametrize(
    "step",
    ["acknowledged_family", "acknowledged_barangay", "team_sent", "help_requested", "responder_unavailable", "call_result"],
)
def test_an_alert_somebody_is_working_stays_open(step):
    assert not alerts.phone_use_may_close(_alert(steps=[{"step": step}]))


def test_an_alert_the_family_acknowledged_stays_open():
    assert not alerts.phone_use_may_close(_alert(status=AlertStatus.ACKNOWLEDGED))


def test_a_nonsense_reason_is_refused():
    with pytest.raises(ValidationError):
        AlertCancel(senior_sync_id=uuid4(), reason="because")


def _cancel(monkeypatch, alert, reason):
    result = SimpleNamespace(scalar_one_or_none=lambda: alert)
    db = AsyncMock()
    db.execute.return_value = result
    monkeypatch.setattr(alerts, "check_device_key", lambda senior, key: None)
    body = AlertCancel(senior_sync_id=alert.senior.sync_id, reason=reason)
    return asyncio.run(alerts.cancel_alert(uuid4(), body, None, db))


def test_phone_use_closes_with_its_own_step_not_a_senior_answer(monkeypatch):
    alert = _alert()
    out = _cancel(monkeypatch, alert, "phone_use")
    assert out.status == AlertStatus.RESOLVED and out.resolved_at is not None
    assert [s["step"] for s in out.escalation_steps][-1] == "auto_cleared_phone_use"


def test_phone_use_on_an_alert_being_worked_changes_nothing(monkeypatch):
    alert = _alert(steps=[{"step": "acknowledged_barangay"}])
    out = _cancel(monkeypatch, alert, "phone_use")
    assert out.status == AlertStatus.ESCALATED and out.resolved_at is None
    assert [s["step"] for s in out.escalation_steps] == ["acknowledged_barangay"]


def test_the_seniors_own_answer_still_closes_whatever_is_happening(monkeypatch):
    alert = _alert(TriggerType.SOS, steps=[{"step": "acknowledged_barangay"}])
    out = _cancel(monkeypatch, alert, None)
    assert out.status == AlertStatus.RESOLVED
    assert [s["step"] for s in out.escalation_steps][-1] == "self_cancelled_senior"
