import random
import string
from datetime import datetime, timedelta, timezone
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import require_role
from app.db.models import Alert, AlertStatus, Contact, Senior, SeniorStatus, UnlinkActor, User, UserRole
from app.db.session import get_db
from app.schemas.contact import InviteCodeOut
from app.schemas.senior import (
    ClosedAlertOut,
    SeniorCreate,
    SeniorDeletionRequest,
    SeniorHeartbeat,
    SeniorOut,
    SeniorStatusOut,
    SeniorStatusUpdate,
    SeniorUpdate,
)

router = APIRouter(prefix="/seniors", tags=["seniors"])

INVITE_CODE_LIFETIME = timedelta(minutes=5)


@router.post("", response_model=SeniorOut, status_code=status.HTTP_201_CREATED)
async def create_senior(payload: SeniorCreate, db: AsyncSession = Depends(get_db)) -> Senior:
    # No auth: the senior has no account, and the returned sync_id becomes their cloud identity.
    senior = Senior(**payload.model_dump())
    db.add(senior)
    await db.commit()
    await db.refresh(senior)
    return senior


async def _get_senior_or_404(sync_id: UUID, db: AsyncSession) -> Senior:
    result = await db.execute(select(Senior).where(Senior.sync_id == sync_id))
    senior = result.scalar_one_or_none()
    if senior is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Senior not found")
    return senior


@router.patch("/{sync_id}", response_model=SeniorOut)
async def update_senior(
    sync_id: UUID, payload: SeniorUpdate, db: AsyncSession = Depends(get_db)
) -> Senior:
    # No auth: the sync_id is the credential. Lets an Edit Profile save reach the cloud copy
    # the family app shows.
    senior = await _get_senior_or_404(sync_id, db)
    for field, value in payload.model_dump().items():
        setattr(senior, field, value)
    await db.commit()
    await db.refresh(senior)
    return senior


@router.post("/{sync_id}/heartbeat", response_model=SeniorOut)
async def heartbeat(
    sync_id: UUID, payload: SeniorHeartbeat, db: AsyncSession = Depends(get_db)
) -> Senior:
    """Records that the senior's phone is still running, with its battery level.

    The sync_id is the credential. The timestamp is the point: it shows monitoring hasn't
    stopped. Each call overwrites the last values, since a battery history would reveal
    when the senior sleeps.
    """
    senior = await _get_senior_or_404(sync_id, db)

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
    sync_id: UUID, payload: SeniorDeletionRequest, db: AsyncSession = Depends(get_db)
) -> None:
    """Soft-deletes a senior's cloud record.

    No auth; the sync_id is the credential. The row is kept for audit, contacts are
    soft-unlinked, and push_token / last_nudge_at / invite_code are cleared. The phone
    wipes its own database separately. Idempotent.
    """
    senior = await _get_senior_or_404(sync_id, db)
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
async def closed_alerts(sync_id: UUID, db: AsyncSession = Depends(get_db)) -> list[Alert]:
    """Which of this senior's recent alerts a family contact or the barangay has closed.

    Lets the phone stop showing a closed alert as open. Uses the sync_id as the
    credential and returns only sync ids and a status.
    """
    senior = await _get_senior_or_404(sync_id, db)
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
async def generate_invite(sync_id: UUID, db: AsyncSession = Depends(get_db)) -> InviteCodeOut:
    senior = await _get_senior_or_404(sync_id, db)

    # Naive UTC, to match the column type.
    now = datetime.now(timezone.utc).replace(tzinfo=None)
    if senior.invite_code_expires_at is not None and senior.invite_code_expires_at > now:
        # Still-active code — this IS the 5-minute cooldown, no separate field needed.
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="An invite code is still active. Wait for it to expire before generating a new one.",
        )

    code = "".join(random.choices(string.digits, k=6))
    senior.invite_code = code
    senior.invite_code_expires_at = now + INVITE_CODE_LIFETIME
    await db.commit()

    return InviteCodeOut(code=code, expires_at=senior.invite_code_expires_at)
