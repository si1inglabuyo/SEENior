"""The fall context that rides on a fall alert from the phone to the cloud.

Two derived figures and a flag: the privacy boundary says no sensor reading leaves the phone.
"""

from uuid import uuid4

import pytest
from pydantic import ValidationError

from app.db.models import Alert
from app.schemas.alert import AlertCreate

ACTIVE = "pre_dev=2.8;active=1;night=0"
AT_REST = "pre_dev=0.1;active=0;night=1"
UNKNOWN_MOTION = "night=1"


def _payload(**extra):
    return {"senior_sync_id": str(uuid4()), "risk_level": "high", "trigger_type": "fall_pattern", **extra}


@pytest.mark.parametrize("note", [ACTIVE, AT_REST, UNKNOWN_MOTION])
def test_what_the_phone_sends_is_accepted(note):
    assert AlertCreate(**_payload(fall_context=note)).fall_context == note


def test_older_phones_send_none():
    assert AlertCreate(**_payload()).fall_context is None


@pytest.mark.parametrize("note", ["free text", "night=1; DROP TABLE alerts", "name=Maria", "pre_dev=" + "9" * 300])
def test_anything_but_figures_is_refused(note):
    with pytest.raises(ValidationError):
        AlertCreate(**_payload(fall_context=note))


def test_the_column_exists_and_is_optional():
    column = Alert.__table__.c.fall_context
    assert column.nullable and column.type.length == 200
