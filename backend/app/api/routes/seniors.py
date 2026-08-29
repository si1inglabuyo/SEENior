import random
import string
from datetime import datetime, timedelta, timezone
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models import Senior
from app.db.session import get_db
from app.schemas.contact import InviteCodeOut
from app.schemas.senior import SeniorCreate, SeniorHeartbeat, SeniorOut, SeniorUpdate

router = APIRouter(prefix="/seniors", tags=["seniors"])

INVITE_CODE_LIFETIME = timedelta(minutes=5)


@router.post("", response_model=SeniorOut, status_code=status.HTTP_201_CREATED)
async def create_senior(payload: SeniorCreate, db: AsyncSession = Depends(get_db)) -> Senior:
    # No auth: this is the senior app's one-time "register myself with the cloud"
    # call during onboarding. Seniors never get a Users account (CLAUDE.md §2) —
    # the returned sync_id becomes their permanent cloud identity, stored locally.
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
    # No auth, same as every other senior-side endpoint here: the senior has no Users
    # account (CLAUDE.md §2), so the sync_id itself is the credential. Exists so an
    # Edit Profile save on the phone also reaches the cloud copy — otherwise the family
    # app keeps rendering the name/age/gender captured at registration.
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
    """Records that this senior's phone is still running, and how much charge it has left.

    The senior has no account to sign in with (CLAUDE.md 2), so the sync_id is the
    credential here exactly as it is for update_senior and generate_invite.

    The *timestamp* is the point of this endpoint. Between alerts nothing in the system
    could distinguish a phone quietly monitoring from one that was flat, switched off, or
    no longer running the app after a reboot -- a failure mode measured on the test
    handset, where a reboot left monitoring off until somebody opened the app by hand. A
    check-in that carries no readings at all is therefore still worth recording.

    Each call overwrites the previous values. Nothing accumulates: a series of battery
    readings would describe when the senior charges their phone, and therefore roughly
    when they sleep, which is the behavioural data 11 keeps on the device. A single
    current reading only describes whether the device can keep working.
    """
    senior = await _get_senior_or_404(sync_id, db)

    # Naive UTC to match the column type, same convention as generate_invite below.
    senior.last_seen_at = datetime.now(timezone.utc).replace(tzinfo=None)
    # Only overwrite a reading the phone actually sent. A check-in that could not read the
    # battery must not erase the last figure the family saw and replace it with nothing.
    if payload.battery_percent is not None:
        senior.battery_percent = payload.battery_percent
    if payload.is_charging is not None:
        senior.is_charging = payload.is_charging
    # Same rule as the readings above -- only overwrite what the phone actually sent. A
    # check-in from a handset that could not obtain a token must not erase the token that
    # is currently the only way to wake it.
    if payload.push_token:
        senior.push_token = payload.push_token

    await db.commit()
    await db.refresh(senior)
    return senior


@router.post("/{sync_id}/invite", response_model=InviteCodeOut)
async def generate_invite(sync_id: UUID, db: AsyncSession = Depends(get_db)) -> InviteCodeOut:
    senior = await _get_senior_or_404(sync_id, db)

    # Naive UTC, matching the column type (TIMESTAMP WITHOUT TIME ZONE, same
    # convention as created_at elsewhere in this schema) — asyncpg rejects a
    # tz-aware datetime.now(timezone.utc) against that column type.
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
