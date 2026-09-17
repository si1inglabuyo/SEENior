"""Guards the four security fixes applied 2026-09-18, each of which is invisible in normal
use and would be easy to undo by accident.

Deliberately no database: every one of these is a property of the token format, the rate
limiter, or the redaction, and none of them needs Postgres to be true.
"""

import asyncio

import pytest
from fastapi import HTTPException

from app.core import ratelimit
from app.core.security import create_access_token, decode_access_token
from app.schemas.contact import InviteSeniorOut


class _FakeSenior:
    sync_id = "11111111-2222-3333-4444-555555555555"
    first_name = "Reviman"
    last_name = "Ocasion"
    age = 71
    gender = "female"
    barangay = "Barangay 123"
    address = "14 Sampaguita Street, Barangay 123, Manila, NCR"
    mobile_number = "09171234567"
    created_at = __import__("datetime").datetime(2026, 9, 6, 1, 7, 43)


# --------------------------------------------------------------- token subject (fix 1)

def test_token_subject_is_the_user_id_not_a_recyclable_string():
    """The whole point of fix 1: a token names an account by something never reissued.

    Deletion tombstones `username` to free it for a fresh sign-up, so a token carrying a
    username would keep resolving after that username changed hands.
    """
    token = create_access_token(subject="42", role="family_contact")
    payload = decode_access_token(token)
    assert payload["sub"] == "42"
    assert int(payload["sub"]) == 42


def test_a_legacy_username_token_no_longer_parses_as_an_identity():
    """get_current_user int()s the subject, so a pre-fix token fails closed rather than
    falling back to the username lookup that carried the bug."""
    payload = decode_access_token(create_access_token(subject="alice@example.com", role="family_contact"))
    with pytest.raises(ValueError):
        int(payload["sub"])


# ------------------------------------------------------------------- redaction (fix 2)

def test_invite_lookup_never_discloses_address_or_mobile_number():
    redacted = InviteSeniorOut.redacted(_FakeSenior())

    assert "Sampaguita" not in redacted.address
    assert redacted.address == _FakeSenior.barangay
    assert _FakeSenior.mobile_number not in redacted.mobile_number
    assert not any(ch.isdigit() for ch in redacted.mobile_number)


def test_invite_lookup_still_carries_what_the_connected_screen_renders():
    """Redaction must not break pairing: these five are what ConnectedScreen reads."""
    redacted = InviteSeniorOut.redacted(_FakeSenior())

    assert redacted.first_name == "Reviman"
    assert redacted.last_name == "Ocasion"
    assert redacted.age == 71
    assert redacted.gender == "female"
    assert redacted.barangay == "Barangay 123"


def test_redacted_payload_keeps_every_field_the_installed_android_dto_expects():
    """SeniorDto declares address/mobileNumber as non-null String. Dropping the keys would
    hand Gson a null for a non-null field, so they stay present and carry a safe value."""
    dumped = InviteSeniorOut.redacted(_FakeSenior()).model_dump()
    for field in ("sync_id", "first_name", "last_name", "age", "gender", "barangay",
                  "address", "mobile_number", "created_at"):
        assert dumped[field] is not None, field


# ----------------------------------------------------------------- rate limiter (fix 5)

def test_limiter_allows_up_to_the_limit_then_refuses():
    async def run():
        ratelimit.reset()
        for _ in range(5):
            await ratelimit.check("t", "k", limit=5, window_seconds=60)
        with pytest.raises(HTTPException) as exc:
            await ratelimit.check("t", "k", limit=5, window_seconds=60)
        assert exc.value.status_code == 429
        assert "Retry-After" in exc.value.headers

    asyncio.run(run())


def test_limiter_keys_are_independent():
    """One family member exhausting their allowance must not lock anybody else out."""
    async def run():
        ratelimit.reset()
        for _ in range(5):
            await ratelimit.check("t", "noisy", limit=5, window_seconds=60)
        # A different key is unaffected.
        await ratelimit.check("t", "quiet", limit=5, window_seconds=60)

    asyncio.run(run())


def test_window_slides_so_an_allowance_returns():
    async def run():
        ratelimit.reset()
        await ratelimit.check("t", "k", limit=1, window_seconds=0.05)
        with pytest.raises(HTTPException):
            await ratelimit.check("t", "k", limit=1, window_seconds=0.05)
        await asyncio.sleep(0.08)
        await ratelimit.check("t", "k", limit=1, window_seconds=0.05)

    asyncio.run(run())


def test_global_bucket_bounds_an_attacker_rotating_addresses():
    """The per-IP limit alone is defeated by a proxy pool; the GLOBAL key is what caps the
    endpoint's total throughput and therefore the real search rate against a 6-digit code."""
    async def run():
        ratelimit.reset()
        for _ in range(120):
            await ratelimit.check("verify-all", ratelimit.GLOBAL, limit=120, window_seconds=60)
        with pytest.raises(HTTPException):
            await ratelimit.check("verify-all", ratelimit.GLOBAL, limit=120, window_seconds=60)

    asyncio.run(run())


def test_client_ip_prefers_the_forwarded_header_over_the_proxy():
    """Behind Render, request.client.host is the proxy. Using it would file every caller in
    the world under one key and throttle all of them together."""
    class _Req:
        headers = {"x-forwarded-for": "203.0.113.7, 10.0.0.1"}
        client = type("C", (), {"host": "10.0.0.1"})()

    assert ratelimit.client_ip(_Req()) == "203.0.113.7"


def test_client_ip_falls_back_when_there_is_no_proxy():
    class _Req:
        headers: dict = {}
        client = type("C", (), {"host": "198.51.100.4"})()

    assert ratelimit.client_ip(_Req()) == "198.51.100.4"
