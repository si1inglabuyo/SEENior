"""Semaphore PH SMS delivery (CLAUDE.md §7/§9) — the fallback channel that reaches a
phone with poor data but a live cellular signal, and currently the ONLY channel a
barangay responder gets at all: the dashboard is pull (someone has to be looking at
it), and nothing has sent the SMS half of "web dashboard + SMS" until this module.

Same two rules as app/core/push.py, and for the same reason: this always runs mid
escalation, on an alert that is already committed, never on a request whose failure
the caller can still act on.

**An SMS failure must never fail an alert.** Every entry point below swallows its own
errors and reports them through the return value and the log, never by raising.

**The message body carries alert context only** — for the family tier, the senior's
first name, risk level and a plain-language reason; for the barangay tier, that plus
the senior's already-registered address, which CLAUDE.md §11 already permits sharing
with the barangay during an active alert under RA 10173 §12(c). No raw sensor data,
no precise GPS coordinates — the same restraint push.py's payload already keeps.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass

import httpx

from app.core.config import settings

logger = logging.getLogger(__name__)

SEMAPHORE_URL = "https://api.semaphore.co/api/v4/messages"

# Plain-language reason per trigger, for a text a senior's family or a responder reads
# in a few seconds with no app open. Kept deliberately short (CLAUDE.md's SMS credits
# are billed per 153-char segment) -- mirrors the trigger_type vocabulary in CLAUDE.md
# §8; an unrecognised value (there shouldn't be one) falls back to the raw code rather
# than failing the message.
_TRIGGER_LABELS = {
    "sos": "SOS pressed",
    "fall_pattern": "possible fall",
    "inactivity": "prolonged inactivity",
    "movement": "low movement",
    "screen_idle": "phone unused too long",
    "charging": "unusual charging",
    "ml_flag": "unusual daily pattern",
}

# Same idea for the barangay tier's escalation reason (escalation.py's three fixed
# strings) -- the audit-log timeline keeps the full sentence; only the SMS gets the
# shortened one.
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
    """Whether SMS can actually be sent. Exposed so /health can report it, same reason
    push.is_configured() exists — a silently SMS-less deployment must not look healthy."""
    return bool(settings.semaphore_api_key)


async def send_sms(numbers: list[str], message: str) -> SmsResult:
    """Sends one message to every number supplied, in a single Semaphore call.

    Numbers are deduped (order preserved) before sending — a family member linked
    twice, or sharing a number with another contact, must not be billed or texted
    twice for one alert. Safe to call with an empty list. Never raises; see the
    module docstring for why.
    """
    deduped = list(dict.fromkeys(n.strip() for n in numbers if n and n.strip()))
    if not deduped:
        return SmsResult()

    if not is_configured():
        logger.warning("SEMAPHORE_API_KEY is not set — SMS delivery is DISABLED.")
        return SmsResult(failed=len(deduped))

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
            # Read the body before raising -- Semaphore's actual reason (bad sender
            # name, insufficient credits, etc.) lives here, and raise_for_status()'s
            # exception message never includes it, which cost real diagnostic time
            # the first time this fired (2026-09-14: logs only showed a generic 500).
            logger.warning(
                "Semaphore returned %d: %s", response.status_code, response.text[:500]
            )
        response.raise_for_status()
    except Exception:
        # Network trouble, a bad key, Semaphore down. Logged, never raised: the alert
        # this SMS was about has already been committed and must not be rolled back.
        logger.exception("Semaphore SMS send failed for %d number(s)", len(deduped))
        return SmsResult(failed=len(deduped))

    try:
        body = response.json()
    except Exception:
        logger.warning("Semaphore returned a non-JSON response: %s", response.text[:200])
        return SmsResult(failed=len(deduped))

    # A successful send returns a JSON array, one object per recipient. An error
    # response (bad key, insufficient credits, invalid number) is a JSON object
    # instead, never a list — that shape difference is the success signal, since a
    # 200 status alone does not tell the two apart.
    if not isinstance(body, list):
        logger.warning("Semaphore rejected the request: %s", body)
        return SmsResult(failed=len(deduped))

    sent = len(body)
    failed = len(deduped) - sent
    if failed:
        logger.warning("Semaphore accepted %d/%d number(s)", sent, len(deduped))
    return SmsResult(sent=sent, failed=failed)


def family_alert_message(senior_name: str, risk_level: str, trigger_type: str) -> str:
    minutes = max(1, settings.family_response_seconds // 60)
    return (
        f"SEENior: {senior_name} - {_trigger_label(trigger_type)} ({risk_level} risk), "
        f"no response. Ack in app within {minutes} min or barangay is notified."
    )


def barangay_alert_message(senior_name: str, barangay: str, address: str, risk_level: str, reason: str) -> str:
    short_reason = _SHORT_REASONS.get(reason, reason)
    return f"SEENior: {senior_name} ({barangay}), {risk_level} risk - {short_reason}. {address}"
