import os
import sys

_DEFAULT_SECRET_KEY = "dev-secret-change-me"


class Settings:
    database_url: str = os.environ.get(
        "DATABASE_URL",
        "postgresql+asyncpg://seenior:seenior@localhost:5432/seenior",
    )
    secret_key: str = os.environ.get("SECRET_KEY", _DEFAULT_SECRET_KEY)
    jwt_algorithm: str = os.environ.get("JWT_ALGORITHM", "HS256")
    access_token_expire_minutes: int = int(
        os.environ.get("ACCESS_TOKEN_EXPIRE_MINUTES", "60")
    )


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
