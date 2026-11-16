"""The phone reports how many 5-minute readings it collected per day: counts only (migration 0028)."""

import asyncio
from datetime import datetime, timedelta, timezone
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from fastapi import HTTPException
from pydantic import ValidationError

from app.api.routes import routine
from app.schemas.sampling import SAMPLING_DAYS, SamplingDayIn, SamplingSync


def _today():
    return datetime.now(timezone.utc).date()


def _day(offset, **counts):
    base = dict(morning=60, afternoon=60, evening=60, night=108)
    base.update(counts)
    return SamplingDayIn(day=_today() - timedelta(days=offset), **base)


def _sync(days, deleted=False):
    senior = SimpleNamespace(id=7, deleted_at=object() if deleted else None)
    db = AsyncMock()
    out = asyncio.run(routine.sync_sampling(SamplingSync(days=days), senior, db))
    return out, db


def test_it_carries_counts_and_nothing_else():
    assert set(SamplingDayIn.model_fields) == {"day", "morning", "afternoon", "evening", "night"}


def test_a_week_of_days_is_stored():
    out, db = _sync([_day(i) for i in range(1, 8)])
    assert out.stored == 7
    db.commit.assert_awaited_once()


def test_a_repeated_day_counts_once():
    out, _ = _sync([_day(1), _day(1, morning=10)])
    assert out.stored == 1


def test_days_older_than_the_window_are_not_kept():
    out, _ = _sync([_day(1), _day(SAMPLING_DAYS + 5)])
    assert out.stored == 1


def test_a_future_day_is_refused():
    with pytest.raises(HTTPException) as e:
        _sync([_day(-5)])
    assert e.value.status_code == 422


def test_a_deleted_senior_gets_a_404():
    with pytest.raises(HTTPException) as e:
        _sync([_day(1)], deleted=True)
    assert e.value.status_code == 404


def test_a_negative_or_absurd_count_is_refused():
    with pytest.raises(ValidationError):
        SamplingDayIn(day=_today(), morning=-1, afternoon=0, evening=0, night=0)
    with pytest.raises(ValidationError):
        SamplingDayIn(day=_today(), morning=10_000, afternoon=0, evening=0, night=0)
