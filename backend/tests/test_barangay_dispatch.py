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


def test_a_responder_name_has_a_length_limit():
    with pytest.raises(ValidationError):
        _action(action="send_team", team="x" * 65)
