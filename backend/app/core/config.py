import os
import sys

_DEFAULT_SECRET_KEY = "dev-secret-change-me"


class Settings:
    database_url: str = os.environ.get(
        "DATABASE_URL",
        "postgresql+asyncpg://seenior:seenior@localhost:5432/seenior",
    )
    if database_url.startswith("postgres://"):
        database_url = database_url.replace("postgres://", "postgresql+asyncpg://", 1)
    elif database_url.startswith("postgresql://") and "+asyncpg" not in database_url:
        database_url = database_url.replace("postgresql://", "postgresql+asyncpg://", 1)
    secret_key: str = os.environ.get("SECRET_KEY", _DEFAULT_SECRET_KEY)
    jwt_algorithm: str = os.environ.get("JWT_ALGORITHM", "HS256")
    access_token_expire_minutes: int = int(
        os.environ.get("ACCESS_TOKEN_EXPIRE_MINUTES", "60")
    )
    # The "Web application" OAuth Client ID from Google Cloud Console — used both as
    # the audience Android requests an ID token for, and to verify that token here.
    # POST /auth/google 503s with a clear message if this isn't set.
    google_client_id: str | None = os.environ.get("GOOGLE_CLIENT_ID")

    # Firebase service-account credentials, used to send FCM pushes. Accepts EITHER the
    # raw JSON of the key (what you paste into a Render env var) or a filesystem path to
    # it (convenient locally) — see app/core/push.py, which sniffs which one it got.
    #
    # Deliberately NOT fatal when unset, unlike SECRET_KEY: a missing push credential
    # degrades the system to the pre-FCM behaviour (alerts still record, the family app
    # still polls), whereas refusing to boot would take the whole escalation chain down
    # over a notification channel. push.py logs loudly instead.
    firebase_credentials: str | None = os.environ.get("FIREBASE_CREDENTIALS")

    # --- Escalation clock (CLAUDE.md 7) ----------------------------------------
    # These run the server-side countdown that moves an unanswered alert up the
    # chain. They exist because no on-device timer can be trusted on this handset:
    # Transsion's "Hiber" layer freezes the app after the screen goes off and takes
    # its alarms out of AlarmManager entirely (measured 2026-08-20). A countdown
    # running here cannot be frozen by the phone it is counting down for.
    #
    # Short defaults so the whole three-tier chain can be demonstrated in one
    # sitting. A real pilot would raise family_response_seconds to 5-10 minutes.
    #
    # Grace: how much longer than the phone's own window the server waits before
    # stepping in. The phone is faster and works offline, so it should win whenever
    # it is actually running; the server only covers the case where it was frozen.
    escalation_grace_seconds: int = int(os.environ.get("ESCALATION_GRACE_SECONDS", "30"))
    family_response_seconds: int = int(os.environ.get("FAMILY_RESPONSE_SECONDS", "120"))
    escalation_sweep_seconds: int = int(os.environ.get("ESCALATION_SWEEP_SECONDS", "20"))

    # How long a senior's phone may go without checking in before the server pushes it
    # awake, and how long it then waits before pushing again.
    #
    # Both are generous on purpose. A phone that is awake and polling normally reports
    # every fifteen minutes and is therefore NEVER nudged -- the nudge exists only for a
    # handset the OS has frozen. Nudging harder would spend battery on the common case to
    # fix the rare one. A phone that is genuinely off (flat, or left at home switched off)
    # cannot be woken at all, so the second knob is what stops the server pushing at a
    # dead handset every twenty seconds forever.
    device_quiet_after_seconds: int = int(
        os.environ.get("DEVICE_QUIET_AFTER_SECONDS", "900")
    )
    device_nudge_every_seconds: int = int(
        os.environ.get("DEVICE_NUDGE_EVERY_SECONDS", "900")
    )

    # Browser origins allowed to call this API. The barangay dashboard is the first
    # part of SEENior that runs in a browser, so it is the first thing this applies
    # to - the Android apps were never subject to it.
    cors_origins: list[str] = [
        origin.strip()
        for origin in os.environ.get(
            "CORS_ORIGINS", "http://localhost:5173,http://127.0.0.1:5173"
        ).split(",")
        if origin.strip()
    ]


settings = Settings()

# The default key is public (it's in this file, in the repo). Refuse to start with it
# unless the dev opts in explicitly, so a pilot deployment can't silently ship with a
# JWT signing key anyone can read and forge tokens against.
if settings.secret_key == _DEFAULT_SECRET_KEY and os.environ.get("SEENIOR_ALLOW_DEV_SECRET") != "1":
    print(
        "FATAL: SECRET_KEY is unset - refusing to start with the default dev key. "
        "Set SECRET_KEY, or set SEENIOR_ALLOW_DEV_SECRET=1 for local development.",
        file=sys.stderr,
    )
    sys.exit(1)
