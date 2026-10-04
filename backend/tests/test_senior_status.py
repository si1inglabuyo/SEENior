"""Tests for the senior roster status (migration 0012). The key rule is that a roster flag
never switches off a detector or stops alerts reaching the barangay."""

import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app.db.models import Senior, SeniorStatus
from app.main import app
from app.schemas.senior import SeniorOut, SeniorStatusOut, SeniorStatusUpdate

SOME_SYNC_ID = "11111111-2222-3333-4444-555555555555"
STATUS_URL = f"/seniors/{SOME_SYNC_ID}/status"


# ------------------------------------------------------------------------ the column

def test_status_defaults_to_active_and_cannot_be_null():
    """The server default backfills existing rows to active, with no third 'unknown' state."""
    column = Senior.__table__.c["status"]
    assert column.server_default.arg == "active"
    assert column.nullable is False


def test_status_has_exactly_two_values():
    assert [e.value for e in SeniorStatus] == ["active", "inactive"]


def test_the_audit_pair_is_nullable():
    """Rows that predate the column were never changed, so 'who and when' may be empty."""
    assert Senior.__table__.c["status_changed_at"].nullable is True
    assert Senior.__table__.c["status_changed_by"].nullable is True


def test_status_is_a_separate_axis_from_deletion():
    """A responder tidying the roster and a senior deleting their account are different
    events and use different columns."""
    cols = Senior.__table__.c
    assert "deleted_at" in cols and "status" in cols
    assert cols["deleted_at"].nullable is True


# ------------------------------------------------------------------------- the schema

def test_request_rejects_a_status_that_is_not_one_of_the_two():
    with pytest.raises(ValidationError):
        SeniorStatusUpdate(status="retired")


def test_request_accepts_both_real_values():
    assert SeniorStatusUpdate(status="inactive").status is SeniorStatus.INACTIVE
    assert SeniorStatusUpdate(status="active").status is SeniorStatus.ACTIVE


def test_response_carries_the_audit_fields_so_the_dashboard_need_not_refetch():
    assert set(SeniorStatusOut.model_fields) == {
        "sync_id", "status", "status_changed_at", "status_changed_by",
    }


def test_senior_out_defaults_status_so_an_older_client_still_parses():
    """A client reading a server that doesn't send `status` sees every senior as active."""
    assert SeniorOut.model_fields["status"].default is SeniorStatus.ACTIVE


# --------------------------------------------------------------------- the endpoint

def test_flipping_a_roster_status_requires_a_token():
    with TestClient(app) as client:
        assert client.patch(STATUS_URL, json={"status": "inactive"}).status_code == 401


def test_a_garbage_token_is_refused():
    with TestClient(app) as client:
        response = client.patch(
            STATUS_URL,
            json={"status": "inactive"},
            headers={"Authorization": "Bearer not-a-real-token"},
        )
        assert response.status_code == 401


# ------------------------------------------------------- the negative that matters most

def test_escalation_never_consults_roster_status():
    """A senior marked inactive must still reach the barangay when their phone raises an alert.

    Checked by reading the escalation module, since the way this breaks is someone adding
    a filter to a roster query.
    """
    import inspect

    from app.api import escalation

    source = inspect.getsource(escalation)
    assert "status_changed" not in source
    assert "SeniorStatus" not in source
    assert "Senior.status" not in source
