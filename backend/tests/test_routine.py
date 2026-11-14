"""The routine sync accepts only the fingerprint's shape and gates who can read it."""

import asyncio
import importlib.util
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock
from uuid import uuid4

import pytest
from fastapi import HTTPException
from pydantic import ValidationError

from app.api.routes import routine as routes
from app.db.models import RoutineBaseline, UserRole
from app.schemas.routine import RoutineRow, RoutineSync

ROW = dict(feature_name="movement_score", time_block="morning", median_value=0.2,
           mad_value=0.05, sample_count=14, is_seed=False)


def test_row_accepts_the_fingerprint_shape():
    assert RoutineRow(**ROW).time_block == "morning"


@pytest.mark.parametrize("bad", [
    {"feature_name": "raw_accel_x"},          # only the five fingerprint features
    {"time_block": "midnight"},
    {"median_value": -1},
    {"mad_value": float("nan")},
    {"median_value": float("inf")},
    {"sample_count": 10_000},
])
def test_row_rejects_anything_else(bad):
    with pytest.raises(ValidationError):
        RoutineRow(**{**ROW, **bad})


def test_at_most_twenty_rows():
    RoutineSync(rows=[ROW] * 20)
    with pytest.raises(ValidationError):
        RoutineSync(rows=[ROW] * 21)


def test_table_holds_one_row_per_cell():
    names = {c.name for c in RoutineBaseline.__table__.constraints}
    assert "uq_routine_baseline_cell" in names


def test_migration_chains_after_0017():
    path = Path(__file__).parent.parent / "migrations" / "versions" / "0018_add_routine_baseline.py"
    spec = importlib.util.spec_from_file_location("m0018", path)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    assert (m.revision, m.down_revision) == ("0018", "0017")


# ---- read access ----

def _db(senior, paired=0):
    """A session whose first query finds `senior` and whose count query answers `paired`."""
    lookup = MagicMock()
    lookup.scalar_one_or_none.return_value = senior
    count = MagicMock()
    count.scalar_one.return_value = paired
    # Then the fingerprint rows, then the timing row (none yet, so the card shows bare block names).
    no_timing = MagicMock()
    no_timing.scalar_one_or_none.return_value = None
    return SimpleNamespace(execute=AsyncMock(side_effect=[lookup, count, MagicMock(), no_timing]))


def _read(senior, user, paired=0):
    return asyncio.run(routes.read_routine(uuid4(), db=_db(senior, paired), user=user))


def _senior(barangay="U.P. Campus", deleted=None):
    return SimpleNamespace(id=1, barangay=barangay, deleted_at=deleted)


def _user(role, barangay=None):
    return SimpleNamespace(id=9, role=role, barangay=barangay)


def test_unknown_senior_is_404():
    with pytest.raises(HTTPException) as e:
        _read(None, _user(UserRole.FAMILY_CONTACT))
    assert e.value.status_code == 404


def test_family_contact_without_a_pairing_gets_404():
    with pytest.raises(HTTPException) as e:
        _read(_senior(), _user(UserRole.FAMILY_CONTACT), paired=0)
    assert e.value.status_code == 404


def test_responder_from_another_barangay_gets_404(monkeypatch):
    from app.core.config import settings
    monkeypatch.setattr(settings, "barangay_show_all_seniors", False)
    with pytest.raises(HTTPException) as e:
        _read(_senior("Commonwealth"), _user(UserRole.BARANGAY_RESPONDER, "U.P. Campus"))
    assert e.value.status_code == 404


def test_deleted_senior_is_404_even_for_a_paired_contact():
    from datetime import datetime
    with pytest.raises(HTTPException) as e:
        _read(_senior(deleted=datetime(2026, 10, 1)), _user(UserRole.FAMILY_CONTACT), paired=1)
    assert e.value.status_code == 404


# ---- history (migration 0024) ----

from datetime import date, timedelta  # noqa: E402

from app.db.models import RoutineBaselineHistory  # noqa: E402
from app.schemas.routine import RoutineHistoryRow, RoutineHistorySync  # noqa: E402

HIST = dict(ROW, day=date(2026, 11, 10), is_estimate=True)


def test_history_row_accepts_the_fingerprint_shape_with_a_day():
    row = RoutineHistoryRow(**HIST)
    assert row.day == date(2026, 11, 10) and row.is_estimate is True


@pytest.mark.parametrize("bad", [
    {"feature_name": "raw_accel_x"},
    {"time_block": "midnight"},
    {"median_value": float("nan")},
    {"day": "not-a-date"},
    {"is_estimate": None},
])
def test_history_row_rejects_anything_else(bad):
    with pytest.raises(ValidationError):
        RoutineHistoryRow(**{**HIST, **bad})


def test_history_is_capped_at_thirty_days_of_twenty_cells():
    RoutineHistorySync(rows=[HIST] * 600)
    with pytest.raises(ValidationError):
        RoutineHistorySync(rows=[HIST] * 601)


def test_history_table_holds_one_row_per_day_and_cell():
    names = {c.name for c in RoutineBaselineHistory.__table__.constraints}
    assert "uq_routine_history_cell" in names


def test_history_migration_chains_after_0023():
    path = Path(__file__).parent.parent / "migrations" / "versions" / "0024_add_routine_baseline_history.py"
    spec = importlib.util.spec_from_file_location("m0024", path)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    assert (m.revision, m.down_revision) == ("0024", "0023")


def _read_history(senior, user, paired=0):
    rows = MagicMock()
    rows.scalars.return_value.all.return_value = []
    lookup = MagicMock()
    lookup.scalar_one_or_none.return_value = senior
    count = MagicMock()
    count.scalar_one.return_value = paired
    db = SimpleNamespace(execute=AsyncMock(side_effect=[lookup, count, rows]))
    return asyncio.run(routes.read_routine_history(uuid4(), db=db, user=user))


def test_history_unpaired_family_contact_gets_404():
    with pytest.raises(HTTPException) as e:
        _read_history(_senior(), _user(UserRole.FAMILY_CONTACT), paired=0)
    assert e.value.status_code == 404


def test_history_deleted_senior_is_404_even_for_a_paired_contact():
    from datetime import datetime
    with pytest.raises(HTTPException) as e:
        _read_history(_senior(deleted=datetime(2026, 10, 1)), _user(UserRole.FAMILY_CONTACT), paired=1)
    assert e.value.status_code == 404


def test_history_paired_family_contact_can_read():
    assert _read_history(_senior(), _user(UserRole.FAMILY_CONTACT), paired=1).rows == []


def _sync(rows, senior=None):
    db = SimpleNamespace(execute=AsyncMock(return_value=MagicMock()), commit=AsyncMock())
    senior = senior or SimpleNamespace(id=1, deleted_at=None)
    return db, asyncio.run(routes.sync_routine_history(RoutineHistorySync(rows=rows), senior=senior, db=db))


def test_history_sync_refuses_a_future_day():
    far = date.today() + timedelta(days=5)
    with pytest.raises(HTTPException) as e:
        _sync([dict(HIST, day=far)])
    assert e.value.status_code == 422


def test_history_sync_for_a_deleted_senior_is_404():
    from datetime import datetime
    with pytest.raises(HTTPException) as e:
        _sync([HIST], senior=SimpleNamespace(id=1, deleted_at=datetime(2026, 10, 1)))
    assert e.value.status_code == 404


# ---- timing (migration 0025) ----

from app.db.models import RoutineTiming as RoutineTimingRow  # noqa: E402
from app.schemas.routine import RoutineTiming  # noqa: E402

TIMING = dict(morning_start=360, afternoon_start=660, evening_start=960, night_start=1260,
              wake_minute=490, wake_days=4, evening_last_minute=1120, evening_last_days=4)


def test_timing_accepts_block_starts_and_observed_times():
    t = RoutineTiming(**TIMING)
    assert (t.morning_start, t.wake_minute, t.wake_days) == (360, 490, 4)


def test_timing_observed_times_are_optional():
    t = RoutineTiming(morning_start=360, afternoon_start=660, evening_start=960, night_start=1260)
    assert t.wake_minute is None and t.wake_days == 0


@pytest.mark.parametrize("bad", [
    {"morning_start": -1},
    {"night_start": 1440},            # minutes after midnight only
    {"wake_minute": 5000},
    {"wake_days": 99},
    {"morning_start": "6:00"},        # a number, never text
])
def test_timing_rejects_anything_else(bad):
    with pytest.raises(ValidationError):
        RoutineTiming(**{**TIMING, **bad})


def test_timing_table_holds_one_row_per_senior():
    assert RoutineTimingRow.__table__.c.senior_id.unique


def _put_timing(payload):
    db = SimpleNamespace(execute=AsyncMock(), commit=AsyncMock())
    senior = SimpleNamespace(id=1, deleted_at=None)
    out = asyncio.run(routes.sync_routine_timing(RoutineTiming(**payload), senior=senior, db=db))
    return out


def test_one_or_two_days_never_become_a_usual_time():
    out = _put_timing({**TIMING, "wake_days": 2, "evening_last_days": 1})
    assert out.wake_minute is None and out.evening_last_minute is None


def test_three_days_are_enough_for_a_usual_time():
    out = _put_timing({**TIMING, "wake_days": 3, "evening_last_days": 3})
    assert out.wake_minute == 490 and out.evening_last_minute == 1120


def test_deleted_senior_cannot_store_timing():
    from datetime import datetime
    db = SimpleNamespace(execute=AsyncMock(), commit=AsyncMock())
    with pytest.raises(HTTPException) as e:
        asyncio.run(routes.sync_routine_timing(
            RoutineTiming(**TIMING), senior=SimpleNamespace(id=1, deleted_at=datetime(2026, 10, 1)), db=db))
    assert e.value.status_code == 404


def test_timing_migration_chains_after_0024():
    path = Path(__file__).parent.parent / "migrations" / "versions" / "0025_add_routine_timing.py"
    spec = importlib.util.spec_from_file_location("m0025", path)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    assert (m.revision, m.down_revision) == ("0025", "0024")
