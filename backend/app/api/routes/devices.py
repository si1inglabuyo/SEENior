from datetime import datetime, timezone

from fastapi import APIRouter, Depends, Response, status
from sqlalchemy import delete, select
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_current_user
from app.db.models import DeviceToken, User
from app.db.session import get_db
from app.schemas.device import DeviceTokenOut, DeviceTokenRegister

router = APIRouter(prefix="/devices", tags=["devices"])


@router.post("/register", response_model=DeviceTokenOut, status_code=status.HTTP_200_OK)
async def register_device(
    payload: DeviceTokenRegister,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> DeviceToken:
    """Records this device's FCM token against the signed-in account.

    Called on every app start, not just after login: FCM rotates tokens on its own
    schedule, and a token the backend never heard about is a family member who silently
    stops receiving alerts with nothing on screen to suggest anything is wrong.

    Idempotent by design, and an UPSERT rather than a select-then-insert for two distinct
    reasons:

    * Re-registering the same token must not accumulate duplicate rows, or one alert
      produces N identical notifications on the same handset.
    * `token` is globally unique, so a device handed to a different person who signs in
      would otherwise collide. Reassigning `user_id` is the correct resolution — the
      token now belongs to whoever is actually holding the phone, and the previous owner
      must stop receiving that senior's alerts immediately.

    The select-then-insert version of this races: two app starts in the same second both
    see "not present" and both insert. ON CONFLICT resolves it in the database.
    """
    now = datetime.now(timezone.utc).replace(tzinfo=None)

    stmt = (
        pg_insert(DeviceToken)
        .values(
            user_id=current_user.id,
            token=payload.token,
            platform=payload.platform,
            created_at=now,
            last_seen_at=now,
        )
        .on_conflict_do_update(
            index_elements=[DeviceToken.token],
            set_={
                "user_id": current_user.id,
                "platform": payload.platform,
                "last_seen_at": now,
            },
        )
        .returning(DeviceToken)
    )

    result = await db.execute(stmt)
    device = result.scalar_one()
    await db.commit()
    return device


@router.delete("/{token}", status_code=status.HTTP_204_NO_CONTENT)
async def unregister_device(
    token: str,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> Response:
    """Drops this device's token, called on sign-out.

    Without it, signing out of a shared or handed-down phone leaves it receiving the
    previous account's alerts — a privacy leak of exactly the kind CLAUDE.md §11 rules
    out, since alert metadata names the senior.

    Scoped to the caller's own rows: a valid token is not authority to delete someone
    else's device. Deleting something already gone still returns 204 — sign-out must
    never fail because it was retried.
    """
    await db.execute(
        delete(DeviceToken).where(
            DeviceToken.token == token,
            DeviceToken.user_id == current_user.id,
        )
    )
    await db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.get("", response_model=list[DeviceTokenOut])
async def list_my_devices(
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> list[DeviceToken]:
    """The caller's registered devices. Exists mainly so "why am I not getting alerts?"
    is answerable without a database console."""
    result = await db.execute(
        select(DeviceToken)
        .where(DeviceToken.user_id == current_user.id)
        .order_by(DeviceToken.last_seen_at.desc())
    )
    return list(result.scalars().all())
