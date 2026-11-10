"""The per-reading series that rides on a confirmed fall alert from the phone to the cloud.

Each figure is |total acceleration - 1 g| for one reading in the 2 s before the free fall, oldest
first. It is the first per-reading series to leave the phone (CLAUDE.md section 11 amendment), so
what the server accepts is held to exactly that shape and nothing else.
"""

import asyncio
from types import SimpleNamespace
from uuid import uuid4

import pytest
from pydantic import ValidationError

from app.api.routes import seniors
from app.db.models import Alert
from app.schemas.alert import AlertCreate

STILL = "dev=" + ",".join(["0.0034"] * 50)
MOVING = "dev=" + ",".join(["30.8000"] * 99)
ONE_READING = "dev=9.8"


def _payload(**extra):
    return {"senior_sync_id": str(uuid4()), "risk_level": "high", "trigger_type": "fall_pattern", **extra}


@pytest.mark.parametrize("trace", [STILL, MOVING, ONE_READING, "dev=" + ",".join(["157.0000"] * 256)])
def test_what_the_phone_sends_is_accepted(trace):
    assert AlertCreate(**_payload(fall_trace=trace)).fall_trace == trace


def test_older_phones_send_none():
    assert AlertCreate(**_payload()).fall_trace is None


@pytest.mark.parametrize(
    "trace",
    [
        "free text",
        "dev=",
        "dev=0.1,",
        "dev=0.1,,0.2",
        "dev=-0.1",  # a distance is never negative
        "dev=0.1;night=1",  # one key only
        "x=0.1,0.2",  # not the right key
        "dev=0.1 ,0.2",
        "dev=0.12345",  # four decimals at most
        "dev=1234.5",  # three integer digits at most
        "dev=" + ",".join(["0.1"] * 257),  # the phone's buffer holds 256
        "dev=0.1,0.2; DROP TABLE alerts",
        "dev=" + "9" * 3000,
    ],
)
def test_anything_but_figures_is_refused(trace):
    with pytest.raises(ValidationError):
        AlertCreate(**_payload(fall_trace=trace))


def test_the_working_adds_up_to_the_context_note():
    """What the panel will do by hand: add the figures, divide by n, compare with pre_dev."""
    values = [float(v) for v in MOVING.removeprefix("dev=").split(",")]
    n = len(values)
    assert n == 99
    assert round(sum(values) / n, 1) == 30.8


def test_the_column_exists_and_is_optional():
    column = Alert.__table__.c.fall_trace
    assert column.nullable
    # TEXT, not the 200-character columns the other fall notes use: 2 s at 50 Hz is ~700 characters.
    assert column.type.length is None


class _Db:
    """Records what delete_senior runs, so the test can see the series being cleared."""

    def __init__(self):
        self.statements = []
        self.committed = False

    async def execute(self, statement):
        self.statements.append(str(statement))
        return SimpleNamespace(scalars=lambda: SimpleNamespace(all=lambda: []))

    async def commit(self):
        self.committed = True


def test_deleting_the_account_clears_the_series_but_keeps_the_alert_rows():
    senior = SimpleNamespace(
        id=1, deleted_at=None, deletion_reason=None, deletion_note=None,
        push_token="t", last_nudge_at=None, invite_code=None, invite_code_expires_at=None,
    )
    db = _Db()
    asyncio.run(seniors.delete_senior(SimpleNamespace(reason="privacy", note=None), senior, db))

    assert db.committed
    clears = [s for s in db.statements if s.startswith("UPDATE alerts SET fall_trace")]
    assert len(clears) == 1, db.statements
    assert not any(s.startswith("DELETE FROM alerts") for s in db.statements)
