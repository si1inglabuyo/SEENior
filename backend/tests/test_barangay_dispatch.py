"""What the barangay's dispatch actions may write to an alert's timeline, and what they refuse."""

import pytest
from fastapi import HTTPException
from pydantic import ValidationError

from app.api.routes import barangay as routes
from app.schemas.barangay import DispatchAction


def _action(**fields):
    return DispatchAction(**fields)


def test_each_action_maps_to_its_timeline_step():
    assert routes._dispatch_step(_action(action="send_team", team="Tanod team 1"), set()) == (
        "team_sent",
        {"team": "Tanod team 1"},
    )
    assert routes._dispatch_step(_action(action="request_help", service="Ambulance"), set()) == (
        "help_requested",
        {"service": "Ambulance"},
    )
    assert routes._dispatch_step(_action(action="call_result", result="no_answer"), set()) == (
        "call_result",
        {"result": "no_answer"},
    )


@pytest.mark.parametrize(
    "fields",
    [
        {"action": "send_team"},
        {"action": "send_team", "team": "   "},
        {"action": "request_help", "service": ""},
        {"action": "cancel_help", "service": "Ambulance"},  # never requested on this alert
        {"action": "call_result"},
    ],
)
def test_an_incomplete_or_unknown_action_is_refused(fields):
    with pytest.raises(HTTPException) as refused:
        routes._dispatch_step(_action(**fields), set())
    assert refused.value.status_code == 422


def test_help_can_only_be_cancelled_where_it_was_requested():
    step, extra = routes._dispatch_step(
        _action(action="cancel_help", service="Ambulance"), {"Ambulance"}
    )
    assert (step, extra) == ("help_cancelled", {"service": "Ambulance"})


def test_resources_out_are_read_off_the_timeline():
    steps = [
        {"step": "escalated_barangay_auto"},
        None,
        {"step": "team_sent", "team": "Tanod team 1"},
        {"step": "help_requested", "service": "Ambulance"},
        {"step": "help_requested", "service": "PRC volunteers"},
        {"step": "help_cancelled", "service": "Ambulance"},
    ]
    assert routes._resources_out(steps) == {"Tanod team 1", "PRC volunteers"}
    assert routes._resources_out(None) == set()


def test_a_responder_the_admin_added_can_be_sent():
    assert routes._dispatch_step(_action(action="send_team", team=" Rescue van "), set()) == (
        "team_sent",
        {"team": "Rescue van"},
    )


def test_an_unavailable_responder_is_noted_but_sends_nobody():
    assert routes._dispatch_step(_action(action="responder_unavailable", team=" Ana Reyes "), set()) == (
        "responder_unavailable",
        {"team": "Ana Reyes"},
    )
    with pytest.raises(HTTPException):
        routes._dispatch_step(_action(action="responder_unavailable"), set())


def test_an_unavailable_responder_does_not_count_as_out():
    steps = [{"step": "responder_unavailable", "team": "Ana Reyes"}]
    assert routes._resources_out(steps) == set()


def test_a_responder_name_has_a_length_limit():
    with pytest.raises(ValidationError):
        _action(action="send_team", team="x" * 65)


# ---- acting on an alert marks it attended -------------------------------------------------

import asyncio
from types import SimpleNamespace
from unittest.mock import AsyncMock

from app.db.models import AlertStatus


def _dispatch(monkeypatch, payload, steps=None):
    alert = SimpleNamespace(status=AlertStatus.ESCALATED, escalation_steps=list(steps or []))

    async def fake_alert(sync_id, db, responder):
        return alert

    async def nothing_elsewhere(alert, barangay, db):
        return set()

    monkeypatch.setattr(routes, "_responder_alert", fake_alert)
    monkeypatch.setattr(routes, "_out_on_other_alerts", nothing_elsewhere)
    monkeypatch.setattr(routes, "_alert_out", lambda a: a)
    monkeypatch.setattr(routes, "append_step", lambda a, step, **kw: a.escalation_steps.append({"step": step, **kw}))
    responder = SimpleNamespace(id=1, barangay="U.P. Campus", full_name="Desk", username="desk")
    asyncio.run(routes.dispatch_incident("sync", payload, AsyncMock(), responder))
    return [s["step"] for s in alert.escalation_steps]


@pytest.mark.parametrize(
    "fields",
    [
        {"action": "call_result", "result": "no_answer"},
        {"action": "send_team", "team": "Ana Reyes"},
        {"action": "responder_unavailable", "team": "Ana Reyes"},
        {"action": "request_help", "service": "Ambulance"},
    ],
)
def test_the_first_action_marks_the_alert_attended(monkeypatch, fields):
    steps = _dispatch(monkeypatch, _action(**fields))
    assert steps[-1] == "acknowledged_barangay" and len(steps) == 2


def test_a_second_action_does_not_acknowledge_again(monkeypatch):
    steps = _dispatch(
        monkeypatch,
        _action(action="request_help", service="Ambulance"),
        [{"step": "acknowledged_barangay"}],
    )
    assert steps == ["acknowledged_barangay", "help_requested"]


def test_cancelling_a_request_is_not_a_claim(monkeypatch):
    steps = _dispatch(
        monkeypatch,
        _action(action="cancel_help", service="Ambulance"),
        [{"step": "help_requested", "service": "Ambulance"}],
    )
    assert steps == ["help_requested", "help_cancelled"]
