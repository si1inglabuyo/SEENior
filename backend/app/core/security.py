from datetime import datetime, timedelta, timezone

import bcrypt
from jose import JWTError, jwt

from app.core.config import settings
from app.db.models import UserRole


def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")


def verify_password(plain_password: str, password_hash: str) -> bool:
    try:
        return bcrypt.checkpw(plain_password.encode("utf-8"), password_hash.encode("utf-8"))
    except ValueError:
        # Malformed hash: treat as no match instead of a 500.
        return False


# Dummy hash so login timing doesn't depend on whether the username exists.
_DUMMY_PASSWORD_HASH = hash_password("not-a-real-password-used-only-for-timing")


def token_lifetime(role: str) -> timedelta:
    """How long a token for `role` stays valid: days for family contacts, minutes for responders."""
    if role == UserRole.FAMILY_CONTACT.value:
        return timedelta(days=settings.family_token_expire_days)
    return timedelta(minutes=settings.access_token_expire_minutes)


def create_access_token(subject: str, role: str) -> str:
    """Mints a bearer token for `subject`.

    `subject` must be the user's immutable database id, never a username or email, which
    can be reassigned after deletion.
    """
    expire = datetime.now(timezone.utc) + token_lifetime(role)
    payload = {"sub": subject, "role": role, "exp": expire}
    return jwt.encode(payload, settings.secret_key, algorithm=settings.jwt_algorithm)


def decode_access_token(token: str) -> dict | None:
    try:
        return jwt.decode(token, settings.secret_key, algorithms=[settings.jwt_algorithm])
    except JWTError:
        return None
