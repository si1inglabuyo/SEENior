"""Each pook's designated responder: migration, validation, and who may edit it."""

import asyncio
import importlib.util
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock

import pytest
from fastapi import HTTPException
from pydantic import ValidationError

from app.api.routes import barangay as route
from app.schemas.barangay import PookResponderUpdate, PookSummary


def _migration():
    path = Path(__file__).parent.parent / "migrations" / "versions" / "0022_add_pook_responders.py"
    spec = importlib.util.spec_from_file_location("m0022", path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def test_migration_chains_after_0021():
    m = _migration()
    assert (m.revision, m.down_revision) == ("0022", "0021")


def test_seed_numbers_are_not_real_subscriber_numbers():
    # 0900000nnnn: the seed must never be a number that could ring a real person.
    assert "0900000" + f"{16:04d}" == "09000000016"


def test_name_and_number_are_trimmed():
    p = PookResponderUpdate(responder_name="  Ana Reyes ", responder_phone=" 0917 123 4567 ")
    assert (p.responder_name, p.responder_phone) == ("Ana Reyes", "0917 123 4567")


def test_both_blank_clears_the_responder():
    p = PookResponderUpdate(responder_name="", responder_phone="  ")
    assert (p.responder_name, p.responder_phone) == (None, None)


@pytest.mark.parametrize(
    "name,phone",
    [("Ana", None), (None, "09171234567"), ("Ana", ""), ("", "09171234567")],
)
def test_name_without_number_or_the_reverse_is_refused(name, phone):
    with pytest.raises(ValidationError):
        PookResponderUpdate(responder_name=name, responder_phone=phone)


@pytest.mark.parametrize("phone", ["abc", "12345", "09171234567; DROP", "<script>"])
def test_a_bad_number_is_refused(phone):
    with pytest.raises(ValidationError):
        PookResponderUpdate(responder_name="Ana", responder_phone=phone)


def test_landline_and_international_forms_are_accepted():
    for phone in ["8920-1234", "(02) 8920 1234", "+63 917 123 4567"]:
        assert PookResponderUpdate(responder_name="Ana", responder_phone=phone).responder_phone == phone


def test_the_summary_carries_the_responder_and_defaults_to_none():
    assert PookSummary(id=1, name="Arboretum", sector="north").responder_phone is None


def _db_returning(pook):
    result = MagicMock()
    result.scalar_one_or_none.return_value = pook
    db = AsyncMock()
    db.execute.return_value = result
    return db


def test_editing_a_pook_saves_it():
    pook = SimpleNamespace(id=1, name="Arboretum", sector="north", responder_name=None, responder_phone=None)
    db = _db_returning(pook)
    user = SimpleNamespace(id=1, barangay="U.P. Campus")
    body = PookResponderUpdate(responder_name="Ana Reyes", responder_phone="09171234567")
    out = asyncio.run(route.set_pook_responder(1, body, db, user))
    assert (out.responder_name, out.responder_phone) == ("Ana Reyes", "09171234567")
    db.commit.assert_awaited_once()


def test_a_pook_in_another_barangay_looks_like_it_does_not_exist():
    db = _db_returning(None)
    user = SimpleNamespace(id=1, barangay="Elsewhere")
    body = PookResponderUpdate(responder_name="Ana", responder_phone="09171234567")
    with pytest.raises(HTTPException) as e:
        asyncio.run(route.set_pook_responder(1, body, db, user))
    assert e.value.status_code == 404
    db.commit.assert_not_awaited()


def test_an_account_without_a_barangay_cannot_edit():
    user = SimpleNamespace(id=1, barangay=None)
    body = PookResponderUpdate(responder_name="Ana", responder_phone="09171234567")
    with pytest.raises(HTTPException) as e:
        asyncio.run(route.set_pook_responder(1, body, AsyncMock(), user))
    assert e.value.status_code == 403
