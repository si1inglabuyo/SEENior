"""Tests for the sign-up, sign-in, senior-creation and alert limits, and the invite code format."""

import asyncio
import re
from types import SimpleNamespace

import pytest
from fastapi import HTTPException

from app.api.routes import alerts, auth, seniors
from app.core import ratelimit


def _request(ip):
    return SimpleNamespace(headers={"x-forwarded-for": ip}, client=SimpleNamespace(host="10.0.0.1"))


def test_per_ip_dependency_blocks_the_call_after_the_limit_and_only_that_address():
    async def run():
        ratelimit.reset()
        guard = ratelimit.per_ip("test-bucket", 3, 60)
        for _ in range(3):
            await guard(_request("203.0.113.7"))
        with pytest.raises(HTTPException) as blocked:
            await guard(_request("203.0.113.7"))
        assert blocked.value.status_code == 429 and "Retry-After" in blocked.value.headers
        await guard(_request("203.0.113.8"))  # a different address is unaffected

    asyncio.run(run())


@pytest.mark.parametrize(
    "router, path, method",
    [
        (auth.router, "/auth/register", "POST"),
        (auth.router, "/auth/google", "POST"),
        (auth.router, "/auth/firebase", "POST"),
        (seniors.router, "/seniors", "POST"),
    ],
)
def test_account_and_senior_creation_routes_are_rate_limited(router, path, method):
    route = next(r for r in router.routes if r.path == path and method in r.methods)
    assert any(d.call.__name__ == "dependency" for d in route.dependant.dependencies)


def test_invite_codes_are_six_digits_and_come_from_a_secure_source():
    source = open(seniors.__file__, encoding="utf-8").read()
    assert "secrets.randbelow" in source and "random.choices" not in source

    class _Db:
        committed = False

        async def commit(self):
            self.committed = True

    senior = SimpleNamespace(invite_code=None, invite_code_expires_at=None, deleted_at=None)
    invite = asyncio.run(seniors.generate_invite(senior, _Db()))

    assert re.fullmatch(r"\d{6}", invite.code)
    assert senior.invite_code == invite.code


def test_alert_creation_uses_a_per_senior_limit_not_a_per_address_one():
    # A per-address cap could block a real emergency raised from a shared network.
    source = open(alerts.__file__, encoding="utf-8").read()
    assert 'ratelimit.check("create-alert", str(payload.senior_sync_id)' in source
