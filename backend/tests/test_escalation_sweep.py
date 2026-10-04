"""Tests for the server-side escalation clock (app/api/escalation.py).

The sweep is driven with in-memory stand-ins for the database and the senders, so these check
the decisions it makes (who is told, when, and that nobody is told twice) without Postgres.
"""

import asyncio
from datetime import datetime, timedelta
from types import SimpleNamespace
from uuid import uuid4

import pytest

from app.api import escalation
from app.core.config import settings
from app.db.models import AlertStatus, ContactType, RiskLevel, TriggerType

CREATED = datetime(2026, 10, 1, 8, 0, 0)


def _contact(kind=ContactType.FAMILY, unlinked=False):
    return SimpleNamespace(
        contact_type=kind, unlinked_at=datetime(2026, 9, 30) if unlinked else None
    )


def _senior(contacts):
    return SimpleNamespace(
        sync_id=uuid4(), first_name="Reviman", barangay="Barangay 123",
        address="14 Sampaguita St", contacts=contacts,
    )


def _alert(trigger=TriggerType.INACTIVITY, contacts=None, steps=None):
    senior = _senior([_contact()] if contacts is None else contacts)
    return SimpleNamespace(
        sync_id=uuid4(), senior_id=1, senior=senior, trigger_type=trigger,
        risk_level=RiskLevel.HIGH, status=AlertStatus.PENDING,
        escalation_steps=steps or [], created_at=CREATED,
    )


class _FakeDb:
    def __init__(self, alerts):
        self._alerts = alerts
        self.commits = 0

    async def execute(self, _statement):
        alerts = self._alerts
        return SimpleNamespace(scalars=lambda: SimpleNamespace(all=lambda: alerts))

    async def commit(self):
        self.commits += 1


@pytest.fixture
def sent(monkeypatch):
    """Replaces every outbound call with a recorder and returns what was sent."""
    log = SimpleNamespace(pushes=[], sms=[], family_sms=[])

    async def tokens(_db, _senior_id):
        return ["token-1"]

    async def numbers(_db, _barangay):
        return ["09170000000"]

    async def push(tokens_, payload):
        log.pushes.append((tokens_, payload))

    async def sms(numbers_, message, *, context):
        log.sms.append((numbers_, message, context))

    async def family_sms(*args):
        log.family_sms.append(args)

    monkeypatch.setattr(escalation, "family_device_tokens", tokens)
    monkeypatch.setattr(escalation, "barangay_phone_numbers", numbers)
    monkeypatch.setattr(escalation, "deliver_alert_push", push)
    monkeypatch.setattr(escalation, "deliver_alert_sms", sms)
    monkeypatch.setattr(escalation, "deliver_family_sms_after_grace", family_sms)
    return log


def _sweep(monkeypatch, alerts, seconds_after_creation):
    async def now(_db):
        return CREATED + timedelta(seconds=seconds_after_creation)

    monkeypatch.setattr(escalation, "db_now", now)
    db = _FakeDb(alerts)

    async def run():
        result = await escalation.sweep_overdue_alerts(db)
        await asyncio.sleep(0)  # let the queued family-SMS task start
        return result

    return asyncio.run(run()), db


def _steps(alert):
    return [entry["step"] for entry in alert.escalation_steps]


# ------------------------------------------------------------------ deadlines

def test_sos_and_fall_get_short_windows_and_the_rest_the_default():
    assert escalation.senior_window(_alert(TriggerType.SOS)) == 10
    assert escalation.senior_window(_alert(TriggerType.FALL_PATTERN)) == 60
    assert escalation.senior_window(_alert(TriggerType.INACTIVITY)) == 600


def test_barangay_waits_a_family_window_after_family_for_ordinary_alerts():
    alert = _alert()
    gap = escalation.barangay_deadline(alert, True) - escalation.family_deadline(alert)
    assert gap == timedelta(seconds=settings.family_response_seconds)


def test_sos_and_a_senior_with_no_family_skip_the_family_wait():
    sos = _alert(TriggerType.SOS)
    assert escalation.barangay_deadline(sos, True) == escalation.family_deadline(sos)
    alone = _alert()
    assert escalation.barangay_deadline(alone, False) == escalation.family_deadline(alone)


def test_unlinked_family_do_not_count_as_a_tier():
    senior = _senior([_contact(unlinked=True), _contact(ContactType.BARANGAY_RESPONDER)])
    assert escalation.has_family_tier(senior) is False
    assert escalation.has_family_tier(_senior([_contact()])) is True


# ---------------------------------------------------------------------- sweep

def test_nothing_happens_before_the_family_deadline(monkeypatch, sent):
    alert = _alert()
    deadline = (escalation.family_deadline(alert) - CREATED).total_seconds()
    counts, db = _sweep(monkeypatch, [alert], deadline - 1)

    assert counts == (0, 0)
    assert alert.status == AlertStatus.PENDING and alert.escalation_steps == []
    assert db.commits == 0 and not sent.pushes and not sent.sms


def test_family_is_notified_at_their_deadline_but_the_barangay_is_not(monkeypatch, sent):
    alert = _alert()
    deadline = (escalation.family_deadline(alert) - CREATED).total_seconds()
    counts, _ = _sweep(monkeypatch, [alert], deadline)

    assert counts == (1, 0)
    assert _steps(alert) == ["escalated_family_server"]
    assert alert.status == AlertStatus.PENDING
    assert len(sent.pushes) == 1 and len(sent.family_sms) == 1 and not sent.sms


def test_barangay_is_escalated_after_the_family_window(monkeypatch, sent):
    alert = _alert(steps=[{"step": "escalated_family"}])
    deadline = (escalation.barangay_deadline(alert, True) - CREATED).total_seconds()
    counts, _ = _sweep(monkeypatch, [alert], deadline)

    assert counts == (0, 1)
    assert alert.status == AlertStatus.ESCALATED
    assert "escalated_barangay_auto" in _steps(alert)
    assert len(sent.sms) == 1 and "Barangay 123" in sent.sms[0][1]


def test_sos_notifies_family_and_barangay_in_the_same_pass(monkeypatch, sent):
    alert = _alert(TriggerType.SOS)
    deadline = (escalation.family_deadline(alert) - CREATED).total_seconds()
    counts, _ = _sweep(monkeypatch, [alert], deadline)

    assert counts == (1, 1)
    assert _steps(alert) == ["escalated_family_server", "escalated_barangay_auto"]
    assert len(sent.pushes) == 1 and len(sent.sms) == 1
    assert alert.escalation_steps[-1]["reason"] == "SOS pressed by the senior"


def test_a_senior_with_no_family_goes_straight_to_the_barangay(monkeypatch, sent):
    alert = _alert(contacts=[])
    deadline = (escalation.family_deadline(alert) - CREATED).total_seconds()
    counts, _ = _sweep(monkeypatch, [alert], deadline)

    assert counts == (0, 1)
    assert _steps(alert) == ["no_family_contact", "escalated_barangay_auto"]
    assert "escalated_family_server" not in _steps(alert)  # nobody was actually told
    assert not sent.pushes and not sent.family_sms and len(sent.sms) == 1


def test_the_server_does_not_repeat_what_the_phone_already_did(monkeypatch, sent):
    alert = _alert(steps=[{"step": "escalated_family"}])
    deadline = (escalation.family_deadline(alert) - CREATED).total_seconds()
    counts, _ = _sweep(monkeypatch, [alert], deadline)

    assert counts == (0, 0)
    assert _steps(alert) == ["escalated_family"]
    assert not sent.pushes and not sent.family_sms


def test_a_second_pass_sends_nothing_again(monkeypatch, sent):
    alert = _alert()
    late = (escalation.barangay_deadline(alert, True) - CREATED).total_seconds() + 5
    _sweep(monkeypatch, [alert], late)
    pushes, texts = len(sent.pushes), len(sent.sms)

    # The row is ESCALATED now, so the real query would no longer return it. Even if it did,
    # the recorded steps must keep the family tier from firing twice.
    alert.status = AlertStatus.PENDING
    counts, _ = _sweep(monkeypatch, [alert], late + 20)
    assert counts[0] == 0
    assert len(sent.pushes) == pushes
