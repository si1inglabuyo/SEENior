"""A family contact can close an alert as a false alarm (PATCH /alerts/{sync_id}/false-positive).

Checks that it needs a login, closes the alert as `false_positive` (not `resolved`),
records who did it, and refuses to re-close a closed alert. The handler is driven directly
with a stand-in alert, so no database is involved.
"""

import asyncio
import uuid
from types import SimpleNamespace

import pytest
from fastapi import HTTPException

from app.api.routes import alerts
from app.db.models import AlertStatus


class FakeDb:
    def __init__(self):
        self.committed = False

    async def commit(self):
        self.committed = True

    async def refresh(self, _obj):
        pass


def _alert(status):
    return SimpleNamespace(status=status, resolved_at=None, escalation_steps=[])


def _user(full_name="Ana Cruz", username="ana"):
    return SimpleNamespace(id=1, full_name=full_name, username=username)


def _run(monkeypatch, alert, user):
    async def fake_family_alert(_sync_id, _db, _user):
        return alert

    monkeypatch.setattr(alerts, "_family_alert", fake_family_alert)
    db = FakeDb()
    result = asyncio.run(alerts.mark_false_positive(uuid.uuid4(), db, user))
    return result, db


def test_route_is_a_patch_behind_a_login():
    route = next(r for r in alerts.router.routes if r.path == "/alerts/{sync_id}/false-positive")
    assert "PATCH" in route.methods
    dependants = {d.call.__name__ for d in route.dependant.dependencies}
    assert "get_current_user" in dependants


def test_closes_the_alert_as_false_positive_not_resolved(monkeypatch):
    alert = _alert(AlertStatus.PENDING)
    result, db = _run(monkeypatch, alert, _user())
    assert result.status is AlertStatus.FALSE_POSITIVE
    assert result.resolved_at is not None
    assert db.committed


def test_records_who_closed_it(monkeypatch):
    alert = _alert(AlertStatus.ACKNOWLEDGED)
    result, _ = _run(monkeypatch, alert, _user(full_name="Ana Cruz"))
    step = result.escalation_steps[-1]
    assert step["step"] == "false_positive_family"
    assert step["by"] == "Ana Cruz"


def test_falls_back_to_the_username_when_there_is_no_full_name(monkeypatch):
    alert = _alert(AlertStatus.PENDING)
    result, _ = _run(monkeypatch, alert, _user(full_name=None, username="ana"))
    assert result.escalation_steps[-1]["by"] == "ana"


@pytest.mark.parametrize("closed", [AlertStatus.RESOLVED, AlertStatus.FALSE_POSITIVE])
def test_refuses_an_alert_that_is_already_closed(monkeypatch, closed):
    with pytest.raises(HTTPException) as exc:
        _run(monkeypatch, _alert(closed), _user())
    assert exc.value.status_code == 400


def test_an_escalated_alert_can_still_be_marked_false(monkeypatch):
    """The barangay may already have been told; this is exactly when family find out it was a mistake."""
    alert = _alert(AlertStatus.ESCALATED)
    result, _ = _run(monkeypatch, alert, _user())
    assert result.status is AlertStatus.FALSE_POSITIVE
