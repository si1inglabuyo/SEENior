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

    senior = SimpleNamespace(id=1, invite_code=None, invite_code_expires_at=None, deleted_at=None)
    invite = asyncio.run(seniors.generate_invite(senior, _Db(linked=0)))

    assert re.fullmatch(r"\d{6}", invite.code)
    assert senior.invite_code == invite.code


class _Db:
    """Stands in for the session: `linked` is the active family-contact count the query returns."""

    def __init__(self, linked):
        self.linked = linked
        self.committed = False

    async def execute(self, _statement):
        return SimpleNamespace(scalar_one=lambda: self.linked)

    async def commit(self):
        self.committed = True


def test_a_senior_with_five_family_contacts_cannot_generate_an_invite_code():
    senior = SimpleNamespace(id=1, invite_code=None, invite_code_expires_at=None, deleted_at=None)
    db = _Db(linked=5)

    with pytest.raises(HTTPException) as caught:
        asyncio.run(seniors.generate_invite(senior, db))

    assert caught.value.status_code == 409
    assert caught.value.detail["code"] == "senior_contact_limit"
    assert senior.invite_code is None and not db.committed


def test_alert_creation_uses_a_per_senior_limit_not_a_per_address_one():
    # A per-address cap could block a real emergency raised from a shared network.
    source = open(alerts.__file__, encoding="utf-8").read()
    assert 'ratelimit.check("create-alert", str(payload.senior_sync_id)' in source
