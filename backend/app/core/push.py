"""Firebase Cloud Messaging delivery for alerts.

A push failure must never fail the alert: every entry point swallows its own errors and
reports them through the return value and the log. Payloads carry alert metadata only
(no sensor readings or coordinates), plus the senior's first name.
"""

from __future__ import annotations

import json
import logging
import os
from dataclasses import dataclass
from datetime import timedelta
from threading import Lock

from app.core.config import settings

logger = logging.getLogger(__name__)

_init_lock = Lock()
_app = None
_init_attempted = False


@dataclass(frozen=True)
class AlertPush:
    """The metadata a family device needs to render an alert notification."""

    alert_sync_id: str
    senior_sync_id: str
    senior_name: str
    risk_level: str
    trigger_type: str


@dataclass(frozen=True)
class PushResult:
    sent: int = 0
    failed: int = 0
    # Tokens FCM reported as permanently dead. The caller deletes these.
    stale_tokens: tuple[str, ...] = ()

    @property
    def attempted(self) -> int:
        return self.sent + self.failed


def _credentials():
    """Builds Firebase credentials from FIREBASE_CREDENTIALS, which may be the raw
    service-account JSON (Render env var) or a path to the file (local)."""
    from firebase_admin import credentials

    raw = (settings.firebase_credentials or "").strip()
    if not raw:
        return None
    if raw.startswith("{"):
        return credentials.Certificate(json.loads(raw))
    if os.path.isfile(raw):
        return credentials.Certificate(raw)
    raise ValueError(
        "FIREBASE_CREDENTIALS is set but is neither service-account JSON "
        "(it does not start with '{') nor a path to an existing file."
    )


def _get_app():
    """Initialises the Firebase app once, on first use.

    Lazy so the API still boots without push credentials. The failure is logged once.
    """
    global _app, _init_attempted

    if _app is not None:
        return _app

    with _init_lock:
        if _app is not None:
            return _app
        if _init_attempted:
            return None
        _init_attempted = True

        try:
            import firebase_admin

            cred = _credentials()
            if cred is None:
                logger.warning(
                    "FIREBASE_CREDENTIALS is not set — push notifications are DISABLED. "
                    "Alerts will still be recorded and the family app will still see them "
                    "by polling, but a closed app will not be woken."
                )
                return None
            _app = firebase_admin.initialize_app(cred)
            logger.info("Firebase Cloud Messaging initialised.")
        except Exception:
            logger.exception("Firebase init failed — push notifications are DISABLED.")
            return None

    return _app


def is_configured() -> bool:
    """Whether pushes can be sent. Exposed so /health can report it."""
    return _get_app() is not None


def send_alert(tokens: list[str], alert: AlertPush) -> PushResult:
    """Delivers one alert to every supplied device token.

    Returns what happened instead of raising. Safe with an empty token list.
    """
    if not tokens:
        return PushResult()

    app = _get_app()
    if app is None:
        return PushResult(failed=len(tokens))

    from firebase_admin import messaging

    # Data-only: a `notification` payload would be shown by the system tray and skip our
    # own code (full-screen intent, alarm sound). A high-priority data message always
    # reaches onMessageReceived, even in Doze. All values must be strings.
    data = {
        "type": "alert",
        "alert_sync_id": alert.alert_sync_id,
        "senior_sync_id": alert.senior_sync_id,
        "senior_name": alert.senior_name,
        "risk_level": alert.risk_level,
        "trigger_type": alert.trigger_type,
    }

    android = messaging.AndroidConfig(
        priority="high",
        # Drop it after an hour: a late alert about an incident that's already handled is
        # worse than none. Longer than the longest response window (600s).
        ttl=timedelta(hours=1),
    )

    # One Message per token (MulticastMessage.tokens is deprecated). `token=` is deprecated
    # in favour of `fid=`, but they are not aliases: `fid` expects a Firebase installation
    # ID, and the app sends a registration token. The warning is harmless.
    messages = [
        messaging.Message(token=token, data=data, android=android) for token in tokens
    ]

    try:
        response = messaging.send_each(messages)
    except Exception:
        # Network trouble, revoked key, disabled API. Logged, never raised: the alert is
        # already committed.
        logger.exception("FCM send failed for alert %s", alert.alert_sync_id)
        return PushResult(failed=len(tokens))

    stale: list[str] = []
    for token, result in zip(tokens, response.responses):
        if result.success:
            continue
        exception = result.exception
        if _is_dead_token(messaging, exception):
            stale.append(token)
        else:
            logger.warning(
                "FCM delivery failed for alert %s: %s",
                alert.alert_sync_id,
                exception,
            )

    return PushResult(
        sent=response.success_count,
        failed=response.failure_count,
        stale_tokens=tuple(stale),
    )


def send_wake(token: str) -> PushResult:
    """Wakes one senior's phone so it can take a sensor sample.

    Phones can freeze background work, and a high-priority data message is the one thing
    Android doesn't defer in Doze. The payload is just "wake": no name, alert or reading.
    Returns instead of raising, since a failed nudge is just a degraded sweep.
    """
    app = _get_app()
    if app is None:
        return PushResult(failed=1)

    from firebase_admin import messaging

    message = messaging.Message(
        token=token,
        data={"type": "wake"},
        android=messaging.AndroidConfig(
            priority="high",
            # Short TTL: a late nudge is useless because the next sweep sends a fresh one.
            ttl=timedelta(seconds=settings.device_nudge_every_seconds),
        ),
    )

    try:
        response = messaging.send_each([message])
    except Exception:
        logger.exception("FCM wake send failed")
        return PushResult(failed=1)

    result = response.responses[0]
    if result.success:
        return PushResult(sent=1)

    if _is_dead_token(messaging, result.exception):
        return PushResult(failed=1, stale_tokens=(token,))

    logger.warning("FCM wake delivery failed: %s", result.exception)
    return PushResult(failed=1)


def _is_dead_token(messaging, exception: Exception | None) -> bool:
    """Whether a per-token failure means the token is dead and its row should be deleted.

    UnregisteredError and SenderIdMismatchError describe the token. INVALID_ARGUMENT can
    also mean a bad message, which fails for every recipient, so the message text is
    checked too, to avoid wiping every token over one bad payload.
    """
    if exception is None:
        return False

    definitely_dead = tuple(
        cls
        for name in ("UnregisteredError", "SenderIdMismatchError")
        if (cls := getattr(messaging, name, None)) is not None
    )
    if definitely_dead and isinstance(exception, definitely_dead):
        return True

    # The code is SCREAMING_SNAKE ("INVALID_ARGUMENT"), unlike google-api-core's kebab-case.
    if getattr(exception, "code", None) != "INVALID_ARGUMENT":
        return False
    return "registration token" in str(exception).lower()
