"""Firebase Cloud Messaging delivery for alerts (CLAUDE.md §13 step 11).

The family tier of the escalation chain used to depend on the family app asking the
backend whether anything had happened. That only works while someone is holding the
phone with the app open — which is precisely not the situation this system exists for.
This module inverts it: the backend tells Google, and Google wakes the app.

Two rules shape everything here.

**A push failure must never fail the alert.** POST /alerts is a senior's phone reporting
an emergency. If FCM is slow, misconfigured, or down, the alert must still commit and
still be visible to the family app's polling. Every entry point below therefore swallows
its own errors and reports them through the return value and the log, never by raising.

**The payload carries alert metadata only** (CLAUDE.md §11) — no sensor readings, no
coordinates. It is the same privacy-stripped subset the cloud `alerts` table already
holds, plus the senior's first name so the family knows who it is about.
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
    # Tokens FCM reported as permanently dead. The caller deletes these; retrying them
    # forever is how a push backlog quietly turns into a rate-limit problem.
    stale_tokens: tuple[str, ...] = ()

    @property
    def attempted(self) -> int:
        return self.sent + self.failed


def _credentials():
    """Builds Firebase credentials from FIREBASE_CREDENTIALS, which may hold either the
    raw service-account JSON or a path to it.

    Both forms exist because the two deployment targets want different things: Render
    takes a pasted env var, while locally it is far easier to point at a file you
    downloaded. Sniffing the value beats making the operator set a second "which mode"
    variable and get it wrong.
    """
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

    Lazy rather than at import time so the API still boots — and every non-push endpoint
    still works — on a machine with no push credentials at all, which is the normal state
    of a fresh clone. The failure is logged once, not once per alert.
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
    """Whether pushes can actually be sent. Exposed so /health can report it rather than
    leaving a silently push-less deployment looking identical to a healthy one."""
    return _get_app() is not None


def send_alert(tokens: list[str], alert: AlertPush) -> PushResult:
    """Delivers one alert to every supplied device token.

    Returns what happened instead of raising; the caller decides what to do with stale
    tokens. Safe to call with an empty token list.
    """
    if not tokens:
        return PushResult()

    app = _get_app()
    if app is None:
        return PushResult(failed=len(tokens))

    from firebase_admin import messaging

    # DATA-ONLY, deliberately. A `notification` payload is handled by the Android system
    # tray whenever the app is backgrounded, which means our own code never runs — no
    # full-screen intent, no alarm-category sound, none of the treatment AlertNotifier
    # already gives an alert. A data message with priority=high always reaches
    # onMessageReceived, even in Doze, so the app builds the notification itself.
    #
    # Every value must be a string; FCM rejects non-string data fields.
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
        # An alert that arrives the next morning is worse than one that never arrives:
        # by then the chain has moved on — the barangay tier has been dispatched, or the
        # family already resolved it — and a stale "possible fall detected" banner sends
        # someone into a panic about a handled incident. An hour is comfortably longer
        # than the longest response window (600s).
        ttl=timedelta(hours=1),
    )

    # One Message per token rather than a MulticastMessage: firebase-admin 7.x deprecates
    # MulticastMessage.tokens, and send_each takes the same round trip anyway.
    #
    # `token=` is itself deprecated in favour of `fid=`, and that is deliberate, not an
    # oversight. They are NOT aliases — the encoder writes them to different wire fields
    # and rejects a message carrying both — so `fid` expects a Firebase *installation* ID
    # (Android: FirebaseInstallations.getId()), whereas every FCM client guide, and this
    # app, produce a registration token (FirebaseMessaging.getToken()). Switching would
    # mean changing what the Android side sends, for no behavioural gain. Deprecated is
    # not removed, requirements.txt pins the version, and the warning is noise.
    messages = [
        messaging.Message(token=token, data=data, android=android) for token in tokens
    ]

    try:
        response = messaging.send_each(messages)
    except Exception:
        # Network trouble, a revoked key, a disabled API. Logged, never raised: the alert
        # itself has already been committed and must not be rolled back over this.
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


def _is_dead_token(messaging, exception: Exception | None) -> bool:
    """Whether a per-token failure means that token is dead for good, so its row should be
    deleted rather than retried forever.

    UnregisteredError (app uninstalled, data cleared, token rotated) and
    SenderIdMismatchError (token belongs to another Firebase project) are unambiguous:
    they can only describe the token.

    INVALID_ARGUMENT needs more care, and getting it wrong is destructive. FCM raises it
    both for a malformed *token* and for a malformed *message* — and in the second case
    EVERY recipient fails with it, so treating the code alone as "token is dead" would
    wipe the whole device_tokens table the first time a bad payload shipped. The message
    text is therefore checked as well, so only a token-specific complaint prunes anything.
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

    # Note the code is SCREAMING_SNAKE ("INVALID_ARGUMENT"), not the kebab-case that
    # google-api-core uses elsewhere — comparing against the wrong casing silently never
    # matches, which is exactly how dead tokens accumulate unnoticed.
    if getattr(exception, "code", None) != "INVALID_ARGUMENT":
        return False
    return "registration token" in str(exception).lower()
