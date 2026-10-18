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
    return SimpleNamespace(execute=AsyncMock(side_effect=[lookup, count, MagicMock()]))


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
