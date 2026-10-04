import secrets
from datetime import datetime, timedelta, timezone
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_authenticated_senior, require_role
from app.core import device_key, ratelimit
from app.db.models import Alert, AlertStatus, Contact, Senior, SeniorStatus, UnlinkActor, User, UserRole
from app.db.session import get_db
from app.schemas.contact import InviteCodeOut
from app.schemas.senior import (
    ClosedAlertOut,
    DeviceKeyOut,
    SeniorCreate,
    SeniorDeletionRequest,
    SeniorHeartbeat,
    SeniorOut,
    SeniorRegistered,
    SeniorStatusOut,
    SeniorStatusUpdate,
    SeniorUpdate,
)

router = APIRouter(prefix="/seniors", tags=["seniors"])

INVITE_CODE_LIFETIME = timedelta(minutes=5)


@router.post(
    "",
    response_model=SeniorRegistered,
    status_code=status.HTTP_201_CREATED,
    dependencies=[Depends(ratelimit.per_ip("create-senior", 10, 3600))],
)
async def create_senior(payload: SeniorCreate, db: AsyncSession = Depends(get_db)) -> SeniorRegistered:
    """Registers a senior. The device key in the response is shown once and only its hash is kept."""
    key = device_key.generate()
    senior = Senior(**payload.model_dump(), device_key_hash=device_key.hash_key(key))
    db.add(senior)
    await db.commit()
    await db.refresh(senior)
    return SeniorRegistered(**SeniorOut.model_validate(senior).model_dump(), device_key=key)


@router.post(
    "/{sync_id}/device-key",
    response_model=DeviceKeyOut,
    status_code=status.HTTP_201_CREATED,
    dependencies=[Depends(ratelimit.per_ip("claim-device-key", 30, 3600))],
)
async def claim_device_key(sync_id: UUID, db: AsyncSession = Depends(get_db)) -> DeviceKeyOut:
    """Issues a device key to a senior registered before keys existed.

    Works once, while the senior has no key; after that it returns 409 and the existing key
    is needed for everything. The updated app calls this on first use.
    """
    senior = await _get_senior_or_404(sync_id, db)
    if senior.device_key_hash is not None or senior.deleted_at is not None:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="A device key was already issued.")
    key = device_key.generate()
    senior.device_key_hash = device_key.hash_key(key)
    await db.commit()
    return DeviceKeyOut(device_key=key)


async def _get_senior_or_404(sync_id: UUID, db: AsyncSession) -> Senior:
    result = await db.execute(select(Senior).where(Senior.sync_id == sync_id))
    senior = result.scalar_one_or_none()
    if senior is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Senior not found")
    return senior


@router.patch("/{sync_id}", response_model=SeniorOut)
async def update_senior(
    payload: SeniorUpdate,
    senior: Senior = Depends(get_authenticated_senior),
    db: AsyncSession = Depends(get_db),
) -> Senior:
    # Lets an Edit Profile save reach the cloud copy the family app shows.
    for field, value in payload.model_dump().items():
        setattr(senior, field, value)
    await db.commit()
    await db.refresh(senior)
    return senior


@router.post("/{sync_id}/heartbeat", response_model=SeniorOut)
async def heartbeat(
    payload: SeniorHeartbeat,
    senior: Senior = Depends(get_authenticated_senior),
    db: AsyncSession = Depends(get_db),
) -> Senior:
    """Records that the senior's phone is still running, with its battery level.

    The timestamp is the point: it shows monitoring hasn't stopped. Each call overwrites
    the last values, since a battery history would reveal when the senior sleeps.
    """
    # Naive UTC to match the column type, same convention as generate_invite below.
    senior.last_seen_at = datetime.now(timezone.utc).replace(tzinfo=None)
    # Only overwrite a reading the phone actually sent.
    if payload.battery_percent is not None:
        senior.battery_percent = payload.battery_percent
    if payload.is_charging is not None:
        senior.is_charging = payload.is_charging
    # Same rule: don't erase the token that is the only way to wake the phone.
    if payload.push_token:
        senior.push_token = payload.push_token

    await db.commit()
    await db.refresh(senior)
    return senior


@router.post("/{sync_id}/delete", status_code=status.HTTP_204_NO_CONTENT)
async def delete_senior(
    payload: SeniorDeletionRequest,
    senior: Senior = Depends(get_authenticated_senior),
    db: AsyncSession = Depends(get_db),
) -> None:
    """Soft-deletes a senior's cloud record.

    The row is kept for audit, contacts are soft-unlinked, and push_token / last_nudge_at /
    invite_code are cleared. The phone wipes its own database separately. Idempotent.
    """
    if senior.deleted_at is not None:
        return

    now = datetime.now(timezone.utc).replace(tzinfo=None)
    senior.deleted_at = now
    senior.deletion_reason = payload.reason
    senior.deletion_note = payload.note
    senior.push_token = None
    senior.last_nudge_at = None
    senior.invite_code = None
    senior.invite_code_expires_at = None

    links = await db.execute(
        select(Contact).where(Contact.senior_id == senior.id, Contact.is_active())
    )
    for contact in links.scalars().all():
        contact.unlinked_at = now
        contact.unlinked_by = UnlinkActor.SENIOR

    await db.commit()


# Built once at import time, like barangay.py's own; FastAPI runs the check per request.
_responder_only = require_role(UserRole.BARANGAY_RESPONDER)


# How far back the phone is told about closed alerts.
_CLOSED_ALERTS_WINDOW = timedelta(days=7)


@router.get("/{sync_id}/closed-alerts", response_model=list[ClosedAlertOut])
async def closed_alerts(
    senior: Senior = Depends(get_authenticated_senior), db: AsyncSession = Depends(get_db)
) -> list[Alert]:
    """Which of this senior's recent alerts a family contact or the barangay has closed.

    Lets the phone stop showing a closed alert as open. Returns only sync ids and a status.
    """
    since = datetime.now(timezone.utc).replace(tzinfo=None) - _CLOSED_ALERTS_WINDOW
    result = await db.execute(
        select(Alert)
        .where(
            Alert.senior_id == senior.id,
            Alert.status.in_([AlertStatus.RESOLVED, AlertStatus.FALSE_POSITIVE]),
            Alert.created_at >= since,
        )
        .order_by(Alert.created_at.desc())
        .limit(50)
    )
    return list(result.scalars().all())


@router.patch("/{sync_id}/status", response_model=SeniorStatusOut)
async def set_senior_status(
    sync_id: UUID,
    payload: SeniorStatusUpdate,
    db: AsyncSession = Depends(get_db),
    responder: User = Depends(_responder_only),
) -> Senior:
    """Moves a senior between the barangay's active roster and inactive list.

    Needs a barangay_responder JWT for that responder's own barangay. It does not stop
    monitoring or stop the senior's alerts reaching the barangay. It lives here rather than
    in barangay.py because that file belongs to the dashboard lane. Idempotent.
    """
    senior = await _get_senior_or_404(sync_id, db)

    # A senior who deleted their account is not a roster entry.
    if senior.deleted_at is not None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Senior not found")

    # Same barangay scoping and 404-not-403 as barangay.py.
    if not responder.barangay or senior.barangay != responder.barangay:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Senior not found")

    if senior.status == payload.status:
        return senior

    senior.status = payload.status
    senior.status_changed_at = datetime.now(timezone.utc).replace(tzinfo=None)
    senior.status_changed_by = responder.id
    await db.commit()
    await db.refresh(senior)
    return senior


@router.post("/{sync_id}/invite", response_model=InviteCodeOut)
async def generate_invite(
    senior: Senior = Depends(get_authenticated_senior), db: AsyncSession = Depends(get_db)
) -> InviteCodeOut:
    # Naive UTC, to match the column type.
    now = datetime.now(timezone.utc).replace(tzinfo=None)
    if senior.invite_code_expires_at is not None and senior.invite_code_expires_at > now:
        # Still-active code — this IS the 5-minute cooldown, no separate field needed.
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="An invite code is still active. Wait for it to expire before generating a new one.",
        )

    code = f"{secrets.randbelow(10**6):06d}"
    senior.invite_code = code
    senior.invite_code_expires_at = now + INVITE_CODE_LIFETIME
    await db.commit()

    return InviteCodeOut(code=code, expires_at=senior.invite_code_expires_at)
