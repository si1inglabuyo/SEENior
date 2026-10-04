"""Tests for the SMS fallback (app/core/sms.py). The HTTP call to Semaphore is replaced with a
stand-in, so these run offline and check what would be sent and how failures are reported."""

import asyncio
from datetime import datetime, timezone

import pytest

from app.core import sms
from app.core.config import settings


class _Response:
    def __init__(self, body, status=200):
        self._body, self.status_code, self.text = body, status, str(body)

    def raise_for_status(self):
        if self.status_code >= 400:
            raise RuntimeError(f"HTTP {self.status_code}")

    def json(self):
        if isinstance(self._body, Exception):
            raise self._body
        return self._body


@pytest.fixture(autouse=True)
def _fresh_daily_count():
    sms.reset_budget()
    yield
    sms.reset_budget()


@pytest.fixture
def semaphore(monkeypatch):
    """Configures an API key and captures each request sent to the gateway."""
    monkeypatch.setattr(settings, "semaphore_api_key", "test-key")
    monkeypatch.setattr(settings, "semaphore_sender_name", None)
    calls = []
    reply = {"response": _Response([])}

    class _Client:
        def __init__(self, *args, **kwargs):
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, *exc):
            return False

        async def post(self, url, data):
            calls.append(data)
            return reply["response"]

    monkeypatch.setattr(sms.httpx, "AsyncClient", _Client)
    return calls, reply


def _send(numbers, message="hi"):
    return asyncio.run(sms.send_sms(numbers, message))


def test_numbers_are_deduplicated_and_blank_ones_dropped(semaphore):
    calls, reply = semaphore
    reply["response"] = _Response([{}, {}])
    result = _send(["0917", " 0917 ", "0918", "", None])

    assert calls[0]["number"] == "0917,0918"
    assert (result.sent, result.failed) == (2, 0)


def test_an_empty_list_sends_nothing(semaphore):
    calls, _ = semaphore
    assert _send([]).attempted == 0 and calls == []


def test_without_an_api_key_everything_counts_as_failed(monkeypatch):
    monkeypatch.setattr(settings, "semaphore_api_key", None)
    result = _send(["0917", "0918"])
    assert (result.sent, result.failed) == (0, 2)


def test_a_rejection_object_counts_as_failure_even_with_http_200(semaphore):
    _, reply = semaphore
    reply["response"] = _Response({"apikey": ["Invalid"]})
    assert _send(["0917"]).failed == 1


def test_partial_acceptance_is_reported(semaphore):
    _, reply = semaphore
    reply["response"] = _Response([{}])
    result = _send(["0917", "0918", "0919"])
    assert (result.sent, result.failed) == (1, 2)


@pytest.mark.parametrize("response", [_Response("err", status=500), _Response(ValueError("not json"))])
def test_gateway_errors_never_raise(semaphore, response):
    _, reply = semaphore
    reply["response"] = response
    assert _send(["0917"]).failed == 1


# --------------------------------------------------------------- daily limit

def test_nothing_is_sent_once_the_daily_limit_is_used_up(semaphore, monkeypatch):
    calls, reply = semaphore
    monkeypatch.setattr(settings, "sms_daily_limit", 2)
    reply["response"] = _Response([{}, {}])

    assert _send(["0917", "0918"]).sent == 2
    blocked = _send(["0919"])

    assert (blocked.sent, blocked.failed) == (0, 1)
    assert len(calls) == 1  # the blocked send never reached the gateway


def test_a_send_that_only_partly_fits_texts_the_first_numbers(semaphore, monkeypatch):
    calls, reply = semaphore
    monkeypatch.setattr(settings, "sms_daily_limit", 2)
    reply["response"] = _Response([{}, {}])

    result = _send(["0917", "0918", "0919"])

    assert calls[0]["number"] == "0917,0918"
    assert (result.sent, result.failed) == (2, 1)


def test_the_count_starts_over_on_a_new_manila_day(semaphore, monkeypatch):
    calls, reply = semaphore
    monkeypatch.setattr(settings, "sms_daily_limit", 1)
    reply["response"] = _Response([{}])
    days = iter([datetime(2026, 10, 1, 9, tzinfo=sms._PH_TZ), datetime(2026, 10, 2, 9, tzinfo=sms._PH_TZ)])

    class _Clock(datetime):
        @classmethod
        def now(cls, tz=None):
            return next(days)

    monkeypatch.setattr(sms, "datetime", _Clock)
    assert _send(["0917"]).sent == 1
    assert _send(["0918"]).sent == 1  # next day, fresh allowance
    assert len(calls) == 2


def test_the_default_limit_is_well_above_normal_use():
    assert settings.sms_daily_limit >= 100


# ------------------------------------------------------------------- wording

AT = datetime(2026, 10, 1, 1, 41, tzinfo=timezone.utc)  # 9:41 AM in Manila


def test_family_sms_names_the_senior_and_the_kind_of_alert():
    sos = sms.family_alert_message("Reviman", "high", "sos", at=AT)
    assert "SOS ALERT (9:41 AM)" in sos and "Reviman" in sos

    fall = sms.family_alert_message("Reviman", "high", "fall_pattern", at=AT)
    assert fall.startswith("FALL ALERT")

    other = sms.family_alert_message("Reviman", "medium", "inactivity", at=AT)
    assert other.startswith("MEDIUM RISK ALERT") and "minutes" in other


def test_family_sms_has_a_filipino_version_and_unknown_languages_fall_back_to_english():
    assert "Pinindot ni Reviman" in sms.family_alert_message("Reviman", "high", "sos", language="fil")
    assert "pressed the SOS button" in sms.family_alert_message("Reviman", "high", "sos", language="xx")


def test_barangay_sms_carries_the_address_and_what_to_do():
    text = sms.barangay_alert_message("Reviman", "Barangay 123", "14 Sampaguita St", "high", "SOS pressed by the senior", at=AT)
    assert "Barangay 123" in text and "14 Sampaguita St" in text and "Respond immediately" in text


def test_messages_fit_a_small_number_of_sms_segments():
    # Credits are billed per 153-character segment, so keep the common messages short.
    for kind in ("sos", "fall_pattern", "inactivity"):
        assert len(sms.family_alert_message("Reviman", "high", kind, at=AT)) <= 306
