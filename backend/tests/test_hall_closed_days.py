"""The hall-closed days: migration chain, model, and the rules on who may set a day."""

import asyncio
import importlib.util
from datetime import timedelta
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock

import pytest
from fastapi import HTTPException

from app.api.routes import config as route
from app.db.models import HallClosedDay


def _migration():
    path = Path(__file__).parent.parent / "migrations" / "versions" / "0021_add_hall_closed_days.py"
    spec = importlib.util.spec_from_file_location("m0021", path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def test_migration_chains_after_0020():
    m = _migration()
    assert (m.revision, m.down_revision) == ("0021", "0020")


def test_one_row_per_barangay_and_day():
    names = {c.name for c in HallClosedDay.__table__.constraints}
    assert "uq_hall_closed_day" in names


def test_a_past_day_is_refused():
    user = SimpleNamespace(id=1, barangay="U.P. CAMPUS")
    with pytest.raises(HTTPException) as e:
        asyncio.run(route.close_hall_on(route.manila_today() - timedelta(days=1), user, AsyncMock()))
    assert e.value.status_code == 422


def test_a_day_too_far_ahead_is_refused():
    user = SimpleNamespace(id=1, barangay="U.P. CAMPUS")
    far = route.manila_today() + timedelta(days=route.MAX_DAYS_AHEAD + 1)
    with pytest.raises(HTTPException) as e:
        asyncio.run(route.close_hall_on(far, user, AsyncMock()))
    assert e.value.status_code == 422


def test_an_account_without_a_barangay_cannot_set_a_day():
    user = SimpleNamespace(id=1, barangay=None)
    with pytest.raises(HTTPException) as e:
        asyncio.run(route.close_hall_on(route.manila_today(), user, AsyncMock()))
    assert e.value.status_code == 403


def test_marking_today_adds_one_row_for_the_responders_barangay():
    user = SimpleNamespace(id=7, barangay="U.P. CAMPUS")
    db = AsyncMock()
    db.add = MagicMock()
    missing = MagicMock()
    missing.first.return_value = None
    listing = MagicMock()
    listing.scalars.return_value = iter([route.manila_today()])
    db.execute.side_effect = [missing, listing]
    out = asyncio.run(route.close_hall_on(route.manila_today(), user, db))
    row = db.add.call_args.args[0]
    assert (row.barangay, row.set_by_user_id) == ("U.P. CAMPUS", 7)
    assert out.closed_days == [route.manila_today().isoformat()]


def test_escalation_never_reads_closed_days():
    root = Path(__file__).parent.parent / "app"
    for name in ("api/escalation.py", "api/routes/alerts.py"):
        assert "HallClosedDay" not in (root / name).read_text(encoding="utf-8")
