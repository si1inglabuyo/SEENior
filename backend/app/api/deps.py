from uuid import UUID

from fastapi import Depends, Header, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core import device_key
from app.core.config import settings
from app.core.security import decode_access_token
from app.db.models import Senior, User, UserRole
from app.db.session import get_db

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/auth/login")


async def get_current_user(
    token: str = Depends(oauth2_scheme),
    db: AsyncSession = Depends(get_db),
) -> User:
    credentials_error = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Could not validate credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )
    payload = decode_access_token(token)
    if payload is None or "sub" not in payload:
        raise credentials_error

    # Look up by immutable id, never username (see create_access_token). An older token
    # carrying a username fails to parse and is treated as invalid, so those users log in
    # again once.
    try:
        user_id = int(payload["sub"])
    except (TypeError, ValueError):
        raise credentials_error

    result = await db.execute(select(User).where(User.id == user_id))
    user = result.scalar_one_or_none()
    # Both are set together on soft delete; check both so it fails closed.
    if user is None or not user.is_active or user.deleted_at is not None:
        raise credentials_error
    return user


def require_role(role: UserRole):
    async def _check(user: User = Depends(get_current_user)) -> User:
        if user.role != role:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail=f"Requires role '{role.value}'",
            )
        return user

    return _check


def check_device_key(senior: Senior, presented: str | None) -> None:
    """Raises 401 unless `presented` is this senior's device key.

    A senior whose phone hasn't claimed a key yet is accepted unless REQUIRE_DEVICE_KEY is on.
    """
    stored = senior.device_key_hash
    if stored is None:
        if settings.require_device_key:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="This app version must be updated to keep working.",
                headers={"WWW-Authenticate": "DeviceKey"},
            )
        return
    if not presented or not device_key.matches(presented, stored):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid device key.",
            headers={"WWW-Authenticate": "DeviceKey"},
        )


async def get_authenticated_senior(
    sync_id: UUID,
    x_device_key: str | None = Header(default=None),
    db: AsyncSession = Depends(get_db),
) -> Senior:
    """The senior named in the path, once their phone has proved it holds the device key."""
    result = await db.execute(select(Senior).where(Senior.sync_id == sync_id))
    senior = result.scalar_one_or_none()
    if senior is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Senior not found")
    check_device_key(senior, x_device_key)
    return senior
