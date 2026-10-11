"""The fall tolerance note that rides on a fall alert from the phone to the cloud.

It must carry a setting and two counts and nothing else: the column is the one place a fall's
adaptation is visible in the cloud, and the privacy boundary says no sensor reading leaves the phone.
"""

from uuid import uuid4

import pytest
from pydantic import ValidationError

from app.db.models import Alert
from app.schemas.alert import AlertCreate

NOTE = "impact_min=27.5;base_impact=25.0;still_ms=8800;base_still=8000;false=2;real=0"


def _payload(**extra):
    return {
        "senior_sync_id": str(uuid4()),
        "risk_level": "high",
        "trigger_type": "fall_pattern",
        **extra,
    }


def test_the_note_the_phone_sends_is_accepted():
    assert AlertCreate(**_payload(fall_tolerance=NOTE)).fall_tolerance == NOTE


def test_older_phones_send_no_note():
    assert AlertCreate(**_payload()).fall_tolerance is None


@pytest.mark.parametrize(
    "note",
    [
        "free text about the senior",
        "impact_min=27.5; DROP TABLE alerts",
        "name=Maria",
        "impact_min=" + "9" * 200,
    ],
)
def test_anything_but_settings_and_counts_is_refused(note):
    with pytest.raises(ValidationError):
        AlertCreate(**_payload(fall_tolerance=note))


def test_the_column_exists_on_the_model_and_is_optional():
    column = Alert.__table__.c.fall_tolerance
    assert column.nullable and column.type.length == 200
