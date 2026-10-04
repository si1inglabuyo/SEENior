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
    # Family contacts stay signed in this long, so they can still act on an alert days after
    # logging in. Barangay responders keep the short lifetime above, since the dashboard runs on
    # shared computers. A deactivated or deleted account is refused on every request regardless.
    family_token_expire_days: int = int(os.environ.get("FAMILY_TOKEN_EXPIRE_DAYS", "14"))

    # The "Web application" OAuth Client ID from Google Cloud Console. Used as the audience
    # for the Android ID token and to verify it. POST /auth/google returns 503 if unset.
    google_client_id: str | None = os.environ.get("GOOGLE_CLIENT_ID")

    # Firebase service-account credentials for FCM pushes: either the raw JSON or a file
    # path (see app/core/push.py). Not fatal when unset; pushes are skipped and logged.
    firebase_credentials: str | None = os.environ.get("FIREBASE_CREDENTIALS")

    # Semaphore PH SMS fallback. Not fatal when unset; SMS is skipped and logged by
    # app/core/sms.py.
    semaphore_api_key: str | None = os.environ.get("SEMAPHORE_API_KEY")
    # Optional. A custom sender name must be approved by Semaphore first.
    semaphore_sender_name: str | None = os.environ.get("SEMAPHORE_SENDER_NAME")

    # --- Escalation clock ---
    # Server-side countdown that moves an unanswered alert up the chain, because phone
    # timers can be frozen by the OS. Defaults are short so the demo runs quickly; a real
    # pilot would use 5-10 minutes for family_response_seconds.
    #
    # Grace: how much longer than the phone's own window the server waits, so the phone
    # wins whenever it is running.
    escalation_grace_seconds: int = int(os.environ.get("ESCALATION_GRACE_SECONDS", "30"))
    family_response_seconds: int = int(os.environ.get("FAMILY_RESPONSE_SECONDS", "120"))
    escalation_sweep_seconds: int = int(os.environ.get("ESCALATION_SWEEP_SECONDS", "20"))

    # How long a family phone has to confirm the push before the server texts them instead.
    # Kept inside the 30-second alert delivery target.
    family_sms_grace_seconds: int = int(os.environ.get("FAMILY_SMS_GRACE_SECONDS", "30"))

    # How long a senior's phone may go without checking in before it is pushed awake, and
    # how long to wait before pushing again. Both are generous, so a normally polling phone
    # is never nudged and a switched-off phone isn't pushed constantly.
    device_quiet_after_seconds: int = int(
        os.environ.get("DEVICE_QUIET_AFTER_SECONDS", "900")
    )
    device_nudge_every_seconds: int = int(
        os.environ.get("DEVICE_NUDGE_EVERY_SECONDS", "900")
    )

    # Most SMS recipients the server will text in one Manila day. A safety limit on cost if the
    # API key is misused or a bug loops; set well above normal use and raise it for a pilot.
    sms_daily_limit: int = int(os.environ.get("SMS_DAILY_LIMIT", "300"))

    # When set, a senior with no device key is refused instead of accepted the old way. Turn on
    # (REQUIRE_DEVICE_KEY=1) once every senior's phone has claimed a key.
    require_device_key: bool = os.environ.get("REQUIRE_DEVICE_KEY") == "1"

    # Interactive API docs (/docs, /redoc, /openapi.json) are off unless ENABLE_API_DOCS=1,
    # so production doesn't publish a map of every endpoint.
    enable_api_docs: bool = os.environ.get("ENABLE_API_DOCS") == "1"

    # Browser origins allowed to call this API (the barangay dashboard).
    cors_origins: list[str] = [
        origin.strip()
        for origin in os.environ.get(
            "CORS_ORIGINS", "http://localhost:5173,http://127.0.0.1:5173"
        ).split(",")
        if origin.strip()
    ]


settings = Settings()

# The default key is public, so refuse to start with it unless the dev opts in. This stops
# a deployment from running with a signing key anyone can read.
if settings.secret_key == _DEFAULT_SECRET_KEY and os.environ.get("SEENIOR_ALLOW_DEV_SECRET") != "1":
    print(
        "FATAL: SECRET_KEY is unset - refusing to start with the default dev key. "
        "Set SECRET_KEY, or set SEENIOR_ALLOW_DEV_SECRET=1 for local development.",
        file=sys.stderr,
    )
    sys.exit(1)
