from datetime import datetime, timedelta, timezone

import bcrypt
from jose import JWTError, jwt

from app.core.config import settings


def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")


def verify_password(plain_password: str, password_hash: str) -> bool:
    try:
        return bcrypt.checkpw(plain_password.encode("utf-8"), password_hash.encode("utf-8"))
    except ValueError:
        # Malformed salt/hash (truncated DB value, wrong scheme) - treat as no match
        # instead of letting it 500 out of the login endpoint.
        return False


# Fixed dummy hash so callers can run verify_password on a constant-shape input when
# there's no real user to compare against, keeping login response time independent of
# whether the username exists.
_DUMMY_PASSWORD_HASH = hash_password("not-a-real-password-used-only-for-timing")


def create_access_token(subject: str, role: str) -> str:
    """Mints a bearer token for `subject`.

    **`subject` must be the user's immutable database id, never their username or email.**
    Those are recyclable: `POST /auth/me/delete` tombstones a departing account's username
    with a `+del<id>.<ts>` tag specifically to free the original value for a fresh sign-up,
    and a barangay responder's username is issued by the OSCA officer and reassigned when
    staff change. A token that names an account by a recyclable string keeps resolving
    after the string changes hands, so the departing holder's still-valid token would
    authenticate as whoever claimed it next -- with their role. An id is never reissued.
    """
    expire = datetime.now(timezone.utc) + timedelta(
        minutes=settings.access_token_expire_minutes
    )
    payload = {"sub": subject, "role": role, "exp": expire}
    return jwt.encode(payload, settings.secret_key, algorithm=settings.jwt_algorithm)


def decode_access_token(token: str) -> dict | None:
    try:
        return jwt.decode(token, settings.secret_key, algorithms=[settings.jwt_algorithm])
    except JWTError:
        return None
