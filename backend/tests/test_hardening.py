"""Tests for the production hardening: API docs off by default, security headers on every
response, and a minimum password length."""

import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app.main import app
from app.schemas.auth import (
    MIN_PASSWORD_LENGTH,
    PasswordChangeRequest,
    PasswordSetRequest,
    RegisterRequest,
)

client = TestClient(app)


@pytest.mark.parametrize("path", ["/docs", "/redoc", "/openapi.json"])
def test_api_docs_are_not_published(path):
    assert client.get(path).status_code == 404


def test_every_response_carries_security_headers():
    response = client.get("/health")
    assert response.headers["x-content-type-options"] == "nosniff"
    assert response.headers["x-frame-options"] == "DENY"
    assert response.headers["referrer-policy"] == "no-referrer"
    assert "max-age=" in response.headers["strict-transport-security"]
    assert response.headers["cache-control"] == "no-store"


def test_error_responses_get_the_headers_too():
    response = client.get("/auth/me")  # no token
    assert response.status_code == 401
    assert response.headers["x-content-type-options"] == "nosniff"


def test_short_passwords_are_rejected_everywhere_a_password_is_set():
    short = "x" * (MIN_PASSWORD_LENGTH - 1)
    with pytest.raises(ValidationError):
        RegisterRequest(full_name="A", phone="0917", email="a@example.com", password=short)
    with pytest.raises(ValidationError):
        PasswordSetRequest(new_password=short)
    with pytest.raises(ValidationError):
        PasswordChangeRequest(current_password="whatever", new_password=short)


def test_a_password_at_the_minimum_is_accepted():
    ok = "x" * MIN_PASSWORD_LENGTH
    assert RegisterRequest(full_name="A", phone="0917", email="a@example.com", password=ok).password == ok


# ------------------------------------------------------------------ token lifetime

def test_family_tokens_last_days_and_responder_tokens_last_minutes():
    from datetime import timedelta

    from app.core.config import settings
    from app.core.security import token_lifetime

    assert token_lifetime("family_contact") == timedelta(days=settings.family_token_expire_days)
    assert token_lifetime("barangay_responder") == timedelta(minutes=settings.access_token_expire_minutes)
    assert token_lifetime("family_contact") > token_lifetime("barangay_responder")


def test_the_expiry_inside_the_token_follows_the_role():
    import time

    from app.core.security import create_access_token, decode_access_token

    now = time.time()
    family = decode_access_token(create_access_token("1", "family_contact"))["exp"] - now
    responder = decode_access_token(create_access_token("2", "barangay_responder"))["exp"] - now
    assert family > 7 * 24 * 3600
    assert responder < 24 * 3600
