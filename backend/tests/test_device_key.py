"""Tests for the per-device key that authenticates a senior's phone.

The sync_id is shown to linked family members and barangay responders, so on its own it must
not be enough to act as the senior's phone.
"""

import asyncio
from types import SimpleNamespace
from uuid import uuid4

import pytest
from fastapi import HTTPException

from app.api import deps
from app.api.routes import alerts, contacts, seniors
from app.core import device_key
from app.core.config import settings


def _senior(key=None):
    return SimpleNamespace(
        id=1, sync_id=uuid4(), device_key_hash=device_key.hash_key(key) if key else None, deleted_at=None
    )


# ----------------------------------------------------------------- the key itself

def test_keys_are_long_random_and_never_repeat():
    keys = {device_key.generate() for _ in range(50)}
    assert len(keys) == 50 and all(len(k) >= 40 for k in keys)


def test_only_the_hash_is_kept_and_it_matches_the_key():
    key = device_key.generate()
    stored = device_key.hash_key(key)
    assert key not in stored and len(stored) == 64
    assert device_key.matches(key, stored)
    assert not device_key.matches(key + "x", stored)
    assert not device_key.matches("", stored)


# --------------------------------------------------------------------- the check

def test_the_right_key_is_accepted():
    key = device_key.generate()
    deps.check_device_key(_senior(key), key)


@pytest.mark.parametrize("presented", [None, "", "wrong-key"])
def test_a_missing_or_wrong_key_is_refused_once_a_senior_has_one(presented):
    with pytest.raises(HTTPException) as refused:
        deps.check_device_key(_senior(device_key.generate()), presented)
    assert refused.value.status_code == 401


def test_a_senior_without_a_key_is_still_accepted_during_the_transition(monkeypatch):
    monkeypatch.setattr(settings, "require_device_key", False)
    deps.check_device_key(_senior(None), None)


def test_strict_mode_refuses_a_senior_who_never_claimed_a_key(monkeypatch):
    monkeypatch.setattr(settings, "require_device_key", True)
    with pytest.raises(HTTPException) as refused:
        deps.check_device_key(_senior(None), None)
    assert refused.value.status_code == 401


def test_another_seniors_key_does_not_work():
    mine, theirs = device_key.generate(), device_key.generate()
    with pytest.raises(HTTPException):
        deps.check_device_key(_senior(mine), theirs)


# ------------------------------------------------------ every sync_id route is covered

def _route(router, suffix, method):
    return next(r for r in router.routes if r.path.endswith(suffix) and method in r.methods)


@pytest.mark.parametrize(
    "router, suffix, method",
    [
        (seniors.router, "/{sync_id}", "PATCH"),
        (seniors.router, "/{sync_id}/heartbeat", "POST"),
        (seniors.router, "/{sync_id}/delete", "POST"),
        (seniors.router, "/{sync_id}/closed-alerts", "GET"),
        (seniors.router, "/{sync_id}/invite", "POST"),
        (contacts.router, "/seniors/{sync_id}/family-contacts", "GET"),
        (contacts.router, "/seniors/{sync_id}/family-contacts/{contact_id}", "DELETE"),
    ],
)
def test_senior_routes_require_the_device_key(router, suffix, method):
    names = {d.call.__name__ for d in _route(router, suffix, method).dependant.dependencies}
    assert "get_authenticated_senior" in names


@pytest.mark.parametrize("handler", [alerts.create_alert, alerts.cancel_alert, alerts.update_alert_severity, alerts.update_alert_location])
def test_alert_routes_take_the_device_key_header(handler):
    assert "x_device_key" in handler.__code__.co_varnames[: handler.__code__.co_argcount]
    assert "check_device_key" in handler.__code__.co_names


# --------------------------------------------------------------- issuing the key

class _Db:
    def __init__(self):
        self.commits = 0

    async def commit(self):
        self.commits += 1


def test_claiming_issues_a_key_once_and_stores_only_its_hash(monkeypatch):
    senior = _senior(None)

    async def fake_get(_sync_id, _db):
        return senior

    monkeypatch.setattr(seniors, "_get_senior_or_404", fake_get)
    issued = asyncio.run(seniors.claim_device_key(senior.sync_id, _Db()))

    assert device_key.matches(issued.device_key, senior.device_key_hash)
    assert issued.device_key != senior.device_key_hash

    with pytest.raises(HTTPException) as again:
        asyncio.run(seniors.claim_device_key(senior.sync_id, _Db()))
    assert again.value.status_code == 409


def test_a_deleted_senior_cannot_claim_a_key(monkeypatch):
    senior = _senior(None)
    senior.deleted_at = object()

    async def fake_get(_sync_id, _db):
        return senior

    monkeypatch.setattr(seniors, "_get_senior_or_404", fake_get)
    with pytest.raises(HTTPException) as refused:
        asyncio.run(seniors.claim_device_key(senior.sync_id, _Db()))
    assert refused.value.status_code == 409
