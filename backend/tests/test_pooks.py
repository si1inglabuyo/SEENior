"""Pooks are a display label on a senior. These pin the shape and that escalation ignores them."""

import importlib.util
from pathlib import Path

import pytest
from pydantic import ValidationError

from app.db.models import Pook, Senior
from app.schemas.senior import PookOut, SeniorCreate, SeniorOut, SeniorUpdate

BASE = dict(first_name="A", last_name="B", age=70, gender="f", barangay="U.P. CAMPUS",
            address="x", mobile_number="09170000000")


def _migration():
    path = Path(__file__).parent.parent / "migrations" / "versions" / "0016_add_pooks.py"
    spec = importlib.util.spec_from_file_location("m0016", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_pook_is_optional_everywhere():
    assert Senior.__table__.c["pook_id"].nullable is True
    assert SeniorCreate(**BASE).pook_id is None
    assert SeniorUpdate(**BASE).pook_id is None
    assert SeniorOut.model_fields["pook"].default is None


def test_an_update_that_omits_pook_id_is_not_treated_as_clearing_it():
    assert "pook_id" not in SeniorUpdate(**BASE).model_dump(exclude_unset=True)
    assert SeniorUpdate(**BASE, pook_id=3).model_dump(exclude_unset=True)["pook_id"] == 3


def test_pook_out_shape():
    assert set(PookOut.model_fields) == {"id", "barangay", "name", "description", "sector"}
    assert {c.name for c in Pook.__table__.c} == set(PookOut.model_fields)


def test_seed_is_the_sixteen_pooks_in_two_sectors():
    pooks = _migration().POOKS
    assert len(pooks) == 16
    assert len({name for name, _, _ in pooks}) == 16
    assert sum(1 for _, sector, _ in pooks if sector == "north") == 6
    assert sum(1 for _, sector, _ in pooks if sector == "south") == 10
    assert {sector for _, sector, _ in pooks} == {"north", "south"}


def test_migration_chains_after_0015():
    m = _migration()
    assert (m.revision, m.down_revision) == ("0016", "0015")


def test_escalation_never_reads_the_pook():
    source = (Path(__file__).parent.parent / "app" / "api" / "escalation.py").read_text(encoding="utf-8")
    assert "pook" not in source.lower()
