"""The fall evidence that rides on a fall alert from the phone to the cloud.

Four summary figures and nothing else: the privacy boundary says no sensor reading leaves the
phone, and this column is what shows the panel which phases satisfied a fall.
"""

from uuid import uuid4

import pytest
from pydantic import ValidationError

from app.db.models import Alert
from app.schemas.alert import AlertCreate

WITH_GYRO = "ff=400;imp=35.0;still=10500;rot=4.5"
NO_GYRO = "ff=400;imp=35.0;still=10500"


def _payload(**extra):
    return {"senior_sync_id": str(uuid4()), "risk_level": "high", "trigger_type": "fall_pattern", **extra}


@pytest.mark.parametrize("note", [WITH_GYRO, NO_GYRO])
def test_what_the_phone_sends_is_accepted(note):
    assert AlertCreate(**_payload(fall_evidence=note)).fall_evidence == note


def test_older_phones_send_none():
    assert AlertCreate(**_payload()).fall_evidence is None


@pytest.mark.parametrize("note", ["free text", "ff=400; DROP TABLE alerts", "name=Maria", "ff=" + "9" * 300])
def test_anything_but_figures_is_refused(note):
    with pytest.raises(ValidationError):
        AlertCreate(**_payload(fall_evidence=note))


def test_the_column_exists_and_is_optional():
    column = Alert.__table__.c.fall_evidence
    assert column.nullable and column.type.length == 200


def test_the_responder_sees_it_on_a_fall_alert():
    from types import SimpleNamespace
    from datetime import datetime

    from app.api.routes.barangay import _alert_out
    from app.db.models import AlertStatus, RiskLevel, TriggerType

    senior = SimpleNamespace(
        sync_id=uuid4(), first_name="A", last_name="B", age=70, gender="f", address="x",
        mobile_number="09170000000", contacts=[], pook=None,
    )
    alert = SimpleNamespace(
        sync_id=uuid4(), risk_level=RiskLevel.HIGH, trigger_type=TriggerType.FALL_PATTERN,
        status=AlertStatus.ESCALATED, escalation_steps=[], created_at=datetime(2026, 10, 14),
        resolved_at=None, location_cluster_id=None, senior=senior,
        fall_evidence=WITH_GYRO, fall_tolerance="impact_min=25.0;base_impact=25.0", fall_context="pre_dev=2.8;active=1;night=0",
    )
    out = _alert_out(alert)
    assert out.fall_evidence == WITH_GYRO
    assert out.fall_tolerance == "impact_min=25.0;base_impact=25.0"
    assert out.fall_context == "pre_dev=2.8;active=1;night=0"
