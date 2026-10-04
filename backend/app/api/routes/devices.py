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

    Called on every app start, since FCM rotates tokens. An upsert, so re-registering
    doesn't create duplicates and a token handed to a new user moves to their account.
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
    """Drops this device's token on sign-out, so a shared phone stops receiving the
    previous account's alerts.

    Scoped to the caller's own rows. Returns 204 even if the token is already gone.
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
    """The caller's registered devices, for debugging missing alerts."""
    result = await db.execute(
        select(DeviceToken)
        .where(DeviceToken.user_id == current_user.id)
        .order_by(DeviceToken.last_seen_at.desc())
    )
    return list(result.scalars().all())
