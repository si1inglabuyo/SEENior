"""Semaphore PH SMS delivery, the fallback for contacts with poor data but cell signal.

Like push.py, an SMS failure must never fail an alert: errors are logged and reported
through the return value, never raised. Messages carry alert context only (first name,
risk level, a plain reason, and for the barangay the registered address). No sensor data
or precise coordinates.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone

import httpx

from app.core.config import settings

logger = logging.getLogger(__name__)

SEMAPHORE_URL = "https://api.semaphore.co/api/v4/messages"

# Short plain-language reason per trigger. Kept short because SMS credits are billed per
# 153-character segment. Unknown values fall back to the raw code.
_TRIGGER_LABELS = {
    "sos": "SOS pressed",
    "fall_pattern": "possible fall",
    "inactivity": "prolonged inactivity",
    "movement": "low movement",
    "screen_idle": "phone unused too long",
    "charging": "unusual charging",
    "ml_flag": "unusual daily pattern",
}

# Filipino counterparts, for a family contact whose language preference is "fil".
_TRIGGER_LABELS_FIL = {
    "sos": "pinindot ang SOS",
    "fall_pattern": "posibleng pagkahulog",
    "inactivity": "matagal nang walang galaw",
    "movement": "kaunting galaw",
    "screen_idle": "matagal na hindi ginagamit ang telepono",
    "charging": "hindi karaniwang pag-charge",
    "ml_flag": "hindi karaniwang gawi ngayong araw",
}

# Shortened barangay escalation reasons for the SMS; the timeline keeps the full sentence.
_SHORT_REASONS = {
    "SOS pressed by the senior": "SOS pressed",
    "No family contact is linked to this senior": "no family contact",
    "No response from the senior or any family contact": "no response from senior/family",
}


def _trigger_label(trigger_type: str) -> str:
    return _TRIGGER_LABELS.get(trigger_type, trigger_type)


@dataclass(frozen=True)
class SmsResult:
    sent: int = 0
    failed: int = 0

    @property
    def attempted(self) -> int:
        return self.sent + self.failed


def is_configured() -> bool:
    """Whether SMS can be sent. Exposed so /health can report it."""
    return bool(settings.semaphore_api_key)


# Recipients texted so far on one Manila calendar day. Kept in memory, so a restart resets it;
# it exists to bound cost, not to be an exact ledger.
_budget_day: date | None = None
_budget_used = 0


def _reserve_budget(wanted: int) -> int:
    """Counts up to `wanted` recipients against today's limit and returns how many fit.

    Reserved when the send is attempted, whether or not Semaphore accepts it, since a
    failing gateway can still bill. Runs without awaiting, so asyncio tasks can't interleave.
    """
    global _budget_day, _budget_used
    today = datetime.now(_PH_TZ).date()
    if _budget_day != today:
        _budget_day, _budget_used = today, 0
    granted = max(0, min(wanted, settings.sms_daily_limit - _budget_used))
    _budget_used += granted
    return granted


def reset_budget() -> None:
    """Forget today's count. For tests only."""
    global _budget_day, _budget_used
    _budget_day, _budget_used = None, 0


async def send_sms(numbers: list[str], message: str) -> SmsResult:
    """Sends one message to every number supplied, in a single Semaphore call.

    Numbers are deduplicated so nobody is texted or billed twice. Once the daily limit
    (`SMS_DAILY_LIMIT`) is reached, the rest are not sent and count as failed; if only some
    fit, the earlier numbers in the list are texted first. Safe with an empty list. Never raises.
    """
    deduped = list(dict.fromkeys(n.strip() for n in numbers if n and n.strip()))
    if not deduped:
        return SmsResult()

    if not is_configured():
        logger.warning("SEMAPHORE_API_KEY is not set — SMS delivery is DISABLED.")
        return SmsResult(failed=len(deduped))

    allowed = _reserve_budget(len(deduped))
    if allowed < len(deduped):
        logger.warning(
            "SMS daily limit of %d reached; not sending to %d of %d recipient(s)",
            settings.sms_daily_limit, len(deduped) - allowed, len(deduped),
        )
        if allowed == 0:
            return SmsResult(failed=len(deduped))
        skipped = len(deduped) - allowed
        deduped = deduped[:allowed]
    else:
        skipped = 0

    payload = {
        "apikey": settings.semaphore_api_key,
        "number": ",".join(deduped),
        "message": message,
    }
    if settings.semaphore_sender_name:
        payload["sendername"] = settings.semaphore_sender_name

    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.post(SEMAPHORE_URL, data=payload)
        if response.status_code >= 400:
            # Read the body before raising, since Semaphore's real reason (bad sender,
            # no credits) is only there.
            logger.warning(
                "Semaphore returned %d: %s", response.status_code, response.text[:500]
            )
        response.raise_for_status()
    except Exception:
        # Logged, never raised: the alert is already committed.
        logger.exception("Semaphore SMS send failed for %d number(s)", len(deduped))
        return SmsResult(failed=len(deduped) + skipped)

    try:
        body = response.json()
    except Exception:
        logger.warning("Semaphore returned a non-JSON response: %s", response.text[:200])
        return SmsResult(failed=len(deduped) + skipped)

    # Success is a JSON array with one object per recipient; an error is a JSON object.
    # A 200 status alone doesn't tell them apart.
    if not isinstance(body, list):
        logger.warning("Semaphore rejected the request: %s", body)
        return SmsResult(failed=len(deduped) + skipped)

    sent = len(body)
    failed = len(deduped) - sent
    if failed:
        logger.warning("Semaphore accepted %d/%d number(s)", sent, len(deduped))
    return SmsResult(sent=sent, failed=failed + skipped)


_PH_TZ = timezone(timedelta(hours=8))  # Philippines has no DST, so a fixed offset is exact.


def _clock(at: datetime | None) -> str:
    """The alert time as a responder reads it, Manila time: '9:41 AM'."""
    moment = (at or datetime.now(timezone.utc)).astimezone(_PH_TZ)
    return f"{moment.hour % 12 or 12}:{moment.minute:02d} {'AM' if moment.hour < 12 else 'PM'}"


def family_alert_message(
    senior_name: str,
    risk_level: str,
    trigger_type: str,
    at: datetime | None = None,
    language: str = "en",
) -> str:
    """Family-tier text: "<KIND> ALERT (time): <what>. Acknowledge immediately in the SEENior
    app. <what happens if nobody does>." The window comes from settings so the text matches
    the real timer. `language` is the contact's language_preference ("en" / "fil"); anything
    else falls back to English."""
    minutes = max(1, settings.family_response_seconds // 60)
    when = _clock(at)
    if language == "fil":
        return _family_alert_message_fil(senior_name, risk_level, trigger_type, when, minutes)
    ack = "Acknowledge immediately in the SEENior app."
    if trigger_type == "sos":
        return (
            f"SOS ALERT ({when}): {senior_name} pressed the SOS button. {ack} "
            f"Barangay responders are being notified at the same time."
        )
    if trigger_type == "fall_pattern":
        return (
            f"FALL ALERT ({when}): A possible fall was detected for {senior_name}, who did not "
            f"respond to the safety check. {ack} If no action is taken within {minutes} "
            f"minutes, barangay responders will be notified."
        )
    return (
        f"{risk_level.upper()} RISK ALERT ({when}): {senior_name} did not respond to the "
        f"safety check ({_trigger_label(trigger_type)}). {ack} If no action is taken within "
        f"{minutes} minutes, barangay responders will be notified."
    )


def _family_alert_message_fil(
    senior_name: str, risk_level: str, trigger_type: str, when: str, minutes: int
) -> str:
    ack = "Paki-acknowledge agad sa SEENior app."
    tail = f"Kung walang tugon sa loob ng {minutes} minuto, aabisuhan ang barangay responders."
    if trigger_type == "sos":
        return (
            f"SOS ALERT ({when}): Pinindot ni {senior_name} ang SOS button. {ack} "
            f"Inabisuhan na rin ang barangay responders."
        )
    if trigger_type == "fall_pattern":
        return (
            f"ALERTO SA PAGKAHULOG ({when}): May na-detect na posibleng pagkahulog kay "
            f"{senior_name}, at hindi siya sumagot sa safety check. {ack} {tail}"
        )
    reason = _TRIGGER_LABELS_FIL.get(trigger_type, trigger_type)
    return (
        f"ALERTO ({risk_level.capitalize()} Risk) {when}: Hindi sumagot si {senior_name} sa "
        f"safety check ({reason}). {ack} {tail}"
    )


def barangay_alert_message(
    senior_name: str, barangay: str, address: str, risk_level: str, reason: str,
    at: datetime | None = None,
) -> str:
    short_reason = _SHORT_REASONS.get(reason, reason)
    return (
        f"{risk_level.upper()} RISK ALERT ({_clock(at)}): {senior_name} ({barangay}) - "
        f"{short_reason}. Respond immediately. Address: {address}"
    )
