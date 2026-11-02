"""Tests for the SMS second factor on sign-in (app/core/twofactor.py, api/routes/auth.py).

The routes are called directly with an in-memory stand-in for the database and a recorder in
place of Semaphore, so these check the decisions (who gets a token, when a code dies) without
Postgres or sending a real text.
"""

import asyncio
from datetime import datetime, timedelta, timezone
from types import SimpleNamespace

import pytest
from fastapi import HTTPException
from sqlalchemy.sql.dml import Delete

from app.api.routes import auth
from app.core import ratelimit, sms, twofactor
from app.core.security import decode_access_token, hash_password
from app.db.models import LoginChallenge, UserRole
from app.schemas.auth import Token, TwoFactorChallenge, TwoFactorResendRequest, TwoFactorVerifyRequest

PASSWORD = "correct horse"


class _FakeDb:
    def __init__(self, user):
        self.user = user
        self.rows: dict[str, LoginChallenge] = {}
        self._next = 0

    async def execute(self, statement):
        if isinstance(statement, Delete):
            self.rows.clear()
            return SimpleNamespace()
        return SimpleNamespace(scalar_one_or_none=lambda: self.user)

    def add(self, row):
        self._next += 1
        row.id = f"challenge-{self._next}"
        row.attempts = 0
        row.sends = 1
        row.consumed = False
        row.created_at = datetime.now(timezone.utc).replace(tzinfo=None)
        row.last_sent_at = row.created_at
        self.rows[row.id] = row

    async def flush(self):
        pass

    async def commit(self):
        pass

    async def rollback(self):
        self.rows.clear()

    async def get(self, model, key):
        if model is LoginChallenge:
            return self.rows.get(key)
        return self.user


def _user(**over):
    fields = dict(
        id=7, username="osca1", password_hash=hash_password(PASSWORD),
        role=UserRole.BARANGAY_RESPONDER, phone=None, two_factor_phone="09171234567", is_active=True,
        two_factor_enabled=True,
    )
    fields.update(over)
    return SimpleNamespace(**fields)


def _request():
    return SimpleNamespace(headers={}, client=SimpleNamespace(host="10.0.0.1"))


@pytest.fixture(autouse=True)
def texts(monkeypatch):
    ratelimit.reset()
    log = SimpleNamespace(sent=[], ok=True)

    async def fake_send(numbers, message):
        log.sent.append((numbers, message))
        return sms.SmsResult(sent=len(numbers)) if log.ok else sms.SmsResult(failed=len(numbers))

    monkeypatch.setattr(auth.sms, "send_sms", fake_send)
    return log


def _code_sent(texts):
    return texts.sent[-1][1].split("code: ")[1][:6]


def _wrong_for(texts):
    return "000000" if _code_sent(texts) != "000000" else "111111"


def _login(db, password=PASSWORD):
    form = SimpleNamespace(username="osca1", password=password)
    return asyncio.run(auth.login(_request(), form, db))


def _verify(db, challenge_id, code):
    body = TwoFactorVerifyRequest(challenge_id=challenge_id, code=code)
    return asyncio.run(auth.verify_login_code(body, _request(), db))


def _resend(db, challenge_id):
    body = TwoFactorResendRequest(challenge_id=challenge_id)
    return asyncio.run(auth.resend_login_code(body, _request(), db))


def _let_cooldown_pass(db, challenge_id):
    db.rows[challenge_id].last_sent_at -= timedelta(seconds=31)


# ------------------------------------------------------------------ helpers

def test_code_is_six_digits_and_hash_is_bound_to_its_challenge():
    code = twofactor.new_code()
    assert len(code) == 6 and code.isdigit()
    stored = twofactor.hash_code("a", code)
    assert twofactor.code_matches("a", code, stored)
    assert twofactor.code_matches("a", f" {code} ", stored)
    assert not twofactor.code_matches("b", code, stored)  # not replayable on another challenge
    assert code not in stored


def test_phone_is_masked_to_its_last_four_digits():
    assert twofactor.mask_phone("09171234567") == "*******4567"


def test_text_fits_one_sms_segment():
    assert len(twofactor.sms_message("123456")) <= 153


# ------------------------------------------------------------------ login

def test_account_without_two_factor_still_gets_a_token_straight_away(texts):
    db = _FakeDb(_user(two_factor_enabled=False))
    result = _login(db)
    assert isinstance(result, Token) and decode_access_token(result.access_token)["sub"] == "7"
    assert texts.sent == []


def test_wrong_password_sends_no_text(texts):
    db = _FakeDb(_user())
    with pytest.raises(HTTPException) as err:
        _login(db, "wrong")
    assert err.value.status_code == 401 and texts.sent == []


def test_two_factor_account_gets_a_challenge_and_a_text_not_a_token(texts):
    db = _FakeDb(_user())
    result = _login(db)
    assert isinstance(result, TwoFactorChallenge)
    assert result.phone_hint == "*******4567"
    assert texts.sent[0][0] == ["09171234567"]
    assert len(_code_sent(texts)) == 6
    assert "access_token" not in result.model_dump()


def test_two_factor_account_with_no_code_phone_is_refused_not_let_in_on_password_alone(texts):
    db = _FakeDb(_user(two_factor_phone=None))
    with pytest.raises(HTTPException) as err:
        _login(db)
    assert err.value.status_code == 403 and texts.sent == []


def test_if_the_text_cannot_be_sent_nobody_gets_in_and_no_challenge_remains(texts):
    texts.ok = False
    db = _FakeDb(_user())
    with pytest.raises(HTTPException) as err:
        _login(db)
    assert err.value.status_code == 503 and db.rows == {}


# ------------------------------------------------------------------ verify

def test_the_right_code_gives_a_token_and_works_only_once(texts):
    db = _FakeDb(_user())
    challenge = _login(db)
    code = _code_sent(texts)
    token = _verify(db, challenge.challenge_id, code)
    assert decode_access_token(token.access_token)["sub"] == "7"
    with pytest.raises(HTTPException) as err:
        _verify(db, challenge.challenge_id, code)
    assert err.value.status_code == 401


def test_five_wrong_guesses_kill_the_challenge_even_for_the_right_code(texts):
    db = _FakeDb(_user())
    challenge = _login(db)
    for _ in range(twofactor.MAX_WRONG_GUESSES):
        with pytest.raises(HTTPException):
            _verify(db, challenge.challenge_id, _wrong_for(texts))
    with pytest.raises(HTTPException):
        _verify(db, challenge.challenge_id, _code_sent(texts))


def test_an_expired_code_is_refused(texts):
    db = _FakeDb(_user())
    challenge = _login(db)
    db.rows[challenge.challenge_id].expires_at = datetime.now(timezone.utc).replace(tzinfo=None) - timedelta(seconds=1)
    with pytest.raises(HTTPException) as err:
        _verify(db, challenge.challenge_id, _code_sent(texts))
    assert err.value.status_code == 401


def test_unknown_challenge_and_deactivated_account_are_refused(texts):
    db = _FakeDb(_user())
    with pytest.raises(HTTPException):
        _verify(db, "no-such-challenge", "123456")
    challenge = _login(db)
    db.user.is_active = False
    with pytest.raises(HTTPException):
        _verify(db, challenge.challenge_id, _code_sent(texts))


def test_starting_a_new_sign_in_discards_the_old_challenge(texts):
    db = _FakeDb(_user())
    first = _login(db)
    first_code = _code_sent(texts)
    second = _login(db)
    assert first.challenge_id != second.challenge_id
    with pytest.raises(HTTPException):
        _verify(db, first.challenge_id, first_code)


# ------------------------------------------------------------------ resend

def test_resend_is_held_back_for_thirty_seconds(texts):
    db = _FakeDb(_user())
    challenge = _login(db)
    with pytest.raises(HTTPException) as err:
        _resend(db, challenge.challenge_id)
    assert err.value.status_code == 429 and len(texts.sent) == 1


def test_resend_after_the_wait_texts_again_and_the_new_code_signs_in(texts):
    db = _FakeDb(_user())
    challenge = _login(db)
    _let_cooldown_pass(db, challenge.challenge_id)
    _resend(db, challenge.challenge_id)
    assert len(texts.sent) == 2
    token = _verify(db, challenge.challenge_id, _code_sent(texts))
    assert decode_access_token(token.access_token)["sub"] == "7"


def test_resend_stops_after_three_texts_in_total(texts):
    db = _FakeDb(_user())
    challenge = _login(db)
    for _ in range(twofactor.MAX_SENDS - 1):
        _let_cooldown_pass(db, challenge.challenge_id)
        _resend(db, challenge.challenge_id)
    _let_cooldown_pass(db, challenge.challenge_id)
    with pytest.raises(HTTPException) as err:
        _resend(db, challenge.challenge_id)
    assert err.value.status_code == 429 and len(texts.sent) == twofactor.MAX_SENDS


def test_resending_does_not_reset_the_wrong_guess_count(texts):
    db = _FakeDb(_user())
    challenge = _login(db)
    for _ in range(twofactor.MAX_WRONG_GUESSES - 1):
        with pytest.raises(HTTPException):
            _verify(db, challenge.challenge_id, _wrong_for(texts))
    _let_cooldown_pass(db, challenge.challenge_id)
    _resend(db, challenge.challenge_id)
    assert db.rows[challenge.challenge_id].attempts == twofactor.MAX_WRONG_GUESSES - 1


def test_the_code_goes_to_the_code_phone_never_the_alert_phone(texts):
    db = _FakeDb(_user(phone="09990000000", two_factor_phone="09171234567"))
    _login(db)
    assert texts.sent[0][0] == ["09171234567"]
