from fastapi import Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.security import decode_access_token
from app.db.models import User, UserRole
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

    # Resolved by immutable id, never by username -- see create_access_token's docstring for
    # why that distinction is a security boundary rather than a style preference.
    #
    # A token minted before this change carries a username here and will not parse as an
    # int, which is deliberately treated as invalid rather than fallen back on: keeping the
    # old lookup alive "just for the transition" would keep the hole open for exactly as
    # long as it existed. The cost is that everyone signed in at deploy time is asked to log
    # in once more, which the sixty-minute token lifetime was already doing to them anyway.
    try:
        user_id = int(payload["sub"])
    except (TypeError, ValueError):
        raise credentials_error

    result = await db.execute(select(User).where(User.id == user_id))
    user = result.scalar_one_or_none()
    # deleted_at is checked alongside is_active because they are set together on soft delete
    # and a row where they ever disagreed must still fail closed.
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
