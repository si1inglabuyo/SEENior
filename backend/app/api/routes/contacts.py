from datetime import datetime, timezone
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Request, status
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.api.deps import get_current_user
from app.core import ratelimit
from app.core.security import create_access_token
from app.db.models import Contact, ContactType, DeviceToken, Senior, UnlinkActor, User
from app.db.session import get_db
from app.schemas.auth import Token
from app.schemas.contact import (
    ContactOut,
    FamilyContactOut,
    PairRequest,
    PairResponse,
    VerifyCodeRequest,
    InviteSeniorOut,
    VerifyCodeResponse,
)
from app.schemas.senior import SeniorOut

router = APIRouter(tags=["contacts"])

MAX_FAMILY_CONTACTS_PER_SENIOR = 5
MAX_SENIORS_PER_FAMILY = 3


async def _senior_by_valid_code(code: str, db: AsyncSession) -> Senior:
    """Returns the senior owning a live, unexpired invite code, or 400."""
    result = await db.execute(select(Senior).where(Senior.invite_code == code))
    senior = result.scalar_one_or_none()
    # Naive UTC, matching the column type — see the same note in seniors.py.
    now = datetime.now(timezone.utc).replace(tzinfo=None)
    if (
        senior is None
        or senior.deleted_at is not None
        or senior.invite_code_expires_at is None
        or senior.invite_code_expires_at < now
    ):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid or expired invite code")
    return senior


@router.post("/contacts/verify", response_model=VerifyCodeResponse)
async def verify_code(
    payload: VerifyCodeRequest,
    request: Request,
    db: AsyncSession = Depends(get_db),
) -> VerifyCodeResponse:
    """Look-up only: lets the family's Link screen show the senior before committing.
    Nothing is created and the code is not consumed (POST /contacts/pair does that).

    Unauthenticated (the code is the credential), so it is rate-limited per IP and
    globally to make guessing the six-digit codes impractical.
    """
    await ratelimit.check("verify-ip", ratelimit.client_ip(request), limit=10, window_seconds=60)
    await ratelimit.check("verify-all", ratelimit.GLOBAL, limit=120, window_seconds=60)

    senior = await _senior_by_valid_code(payload.invite_code, db)
    # Redacted record (see InviteSeniorOut) so a correct guess doesn't reveal a home address.
    return VerifyCodeResponse(senior=InviteSeniorOut.redacted(senior))


@router.post("/contacts/pair", response_model=PairResponse, status_code=status.HTTP_201_CREATED)
async def pair_contact(
    payload: PairRequest,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> PairResponse:
    # The account already exists (POST /auth/register or /auth/google); this only pairs it.
    senior = await _senior_by_valid_code(payload.invite_code, db)

    # Counts and duplicate checks only look at active pairings, so removed contacts free
    # up their slot.
    count_result = await db.execute(
        select(func.count())
        .select_from(Contact)
        .where(
            Contact.senior_id == senior.id,
            Contact.contact_type == ContactType.FAMILY,
            Contact.is_active(),
        )
    )
    if count_result.scalar_one() >= MAX_FAMILY_CONTACTS_PER_SENIOR:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"This senior already has {MAX_FAMILY_CONTACTS_PER_SENIOR} family contacts",
        )

    user = current_user

    my_seniors_result = await db.execute(
        select(func.count())
        .select_from(Contact)
        .where(
            Contact.user_id == user.id,
            Contact.contact_type == ContactType.FAMILY,
            Contact.is_active(),
        )
    )
    if my_seniors_result.scalar_one() >= MAX_SENIORS_PER_FAMILY:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"You can only monitor up to {MAX_SENIORS_PER_FAMILY} seniors",
        )

    duplicate_result = await db.execute(
        select(Contact).where(
            Contact.senior_id == senior.id,
            Contact.user_id == user.id,
            Contact.is_active(),
        )
    )
    if duplicate_result.scalar_one_or_none() is not None:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="You're already linked to this senior")

    # An old unlinked pairing is left as is and a new row is inserted, so the history shows
    # link, unlink, link again. The partial unique index only covers active rows.

    contact = Contact(
        senior_id=senior.id,
        user_id=user.id,
        contact_type=ContactType.FAMILY,
        relationship_label=payload.relationship_label,
    )
    db.add(contact)

    # Single-use: clear the code now that it's redeemed.
    senior.invite_code = None
    senior.invite_code_expires_at = None

    await db.commit()
    await db.refresh(
        contact,
        attribute_names=["id", "contact_type", "relationship_label", "created_at", "senior"],
    )

    token = create_access_token(subject=str(user.id), role=user.role.value)
    return PairResponse(contact=ContactOut.model_validate(contact), token=Token(access_token=token))


@router.get("/contacts/me", response_model=list[ContactOut])
async def list_my_seniors(
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> list[Contact]:
    """Every senior the current family member is linked to (up to MAX_SENIORS_PER_FAMILY)."""
    result = await db.execute(
        select(Contact)
        .where(
            Contact.user_id == current_user.id,
            Contact.contact_type == ContactType.FAMILY,
            Contact.is_active(),
        )
        .options(selectinload(Contact.senior))
        .order_by(Contact.created_at.asc())
    )
    return list(result.scalars().all())


@router.delete("/contacts/{contact_id}", status_code=status.HTTP_204_NO_CONTENT)
async def unlink_senior(
    contact_id: int,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> None:
    """Family-side unlink, scoped to the caller's own contact row.

    Soft: the row is marked, not deleted, and both apps stop listing the pairing.
    """
    result = await db.execute(
        select(Contact).where(
            Contact.id == contact_id,
            Contact.user_id == current_user.id,
            Contact.is_active(),
        )
    )
    contact = result.scalar_one_or_none()
    if contact is None:
        # Same 404 for missing, someone else's and already-unlinked; a repeat is a no-op.
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Contact not found")

    # func.now() so it uses the database clock, same as created_at.
    contact.unlinked_at = func.now()
    contact.unlinked_by = UnlinkActor.FAMILY
    await db.commit()


@router.get("/seniors/{sync_id}/family-contacts", response_model=list[FamilyContactOut])
async def list_family_contacts(sync_id: UUID, db: AsyncSession = Depends(get_db)) -> list[FamilyContactOut]:
    # No auth: the senior app has no account and identifies by sync_id, like POST /alerts.
    result = await db.execute(select(Senior).where(Senior.sync_id == sync_id))
    senior = result.scalar_one_or_none()
    if senior is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Senior not found")

    contacts_result = await db.execute(
        select(Contact)
        .where(
            Contact.senior_id == senior.id,
            Contact.contact_type == ContactType.FAMILY,
            Contact.is_active(),
        )
        .options(selectinload(Contact.user))
        .order_by(Contact.created_at.asc())
    )
    contacts = contacts_result.scalars().all()

    # One grouped query for when each contact last opened the app (the family app
    # re-registers its token on every launch).
    user_ids = [c.user_id for c in contacts]
    last_active: dict[int, object] = {}
    if user_ids:
        rows = await db.execute(
            select(DeviceToken.user_id, func.max(DeviceToken.last_seen_at))
            .where(DeviceToken.user_id.in_(user_ids))
            .group_by(DeviceToken.user_id)
        )
        last_active = {user_id: seen for user_id, seen in rows.all()}

    return [
        FamilyContactOut(
            id=c.id,
            full_name=c.user.full_name,
            phone=c.user.phone,
            relationship_label=c.relationship_label,
            contact_type=c.contact_type,
            created_at=c.created_at,
            last_active_at=last_active.get(c.user_id),
        )
        for c in contacts
    ]


@router.delete("/seniors/{sync_id}/family-contacts/{contact_id}", status_code=status.HTTP_204_NO_CONTENT)
async def remove_family_contact(
    sync_id: UUID, contact_id: int, db: AsyncSession = Depends(get_db)
) -> None:
    # No auth: the senior's "Remove Contact" button, scoped to this senior's sync_id.
    # Soft, like the family-side unlink above.
    result = await db.execute(select(Senior).where(Senior.sync_id == sync_id))
    senior = result.scalar_one_or_none()
    if senior is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Senior not found")

    contact_result = await db.execute(
        select(Contact).where(
            Contact.id == contact_id,
            Contact.senior_id == senior.id,
            Contact.is_active(),
        )
    )
    contact = contact_result.scalar_one_or_none()
    if contact is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Contact not found")

    # Database clock, matching created_at — see the note in unlink_senior above.
    contact.unlinked_at = func.now()
    contact.unlinked_by = UnlinkActor.SENIOR
    await db.commit()


@router.get("/seniors/{sync_id}/contacts", response_model=list[ContactOut])
async def list_contacts(
    sync_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> list[Contact]:
    result = await db.execute(select(Senior).where(Senior.sync_id == sync_id))
    senior = result.scalar_one_or_none()
    if senior is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Senior not found")

    # Only a currently linked contact may view this list.
    link_result = await db.execute(
        select(Contact).where(
            Contact.senior_id == senior.id,
            Contact.user_id == current_user.id,
            Contact.is_active(),
        )
    )
    if link_result.scalar_one_or_none() is None:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Not linked to this senior")

    contacts_result = await db.execute(
        select(Contact)
        .where(Contact.senior_id == senior.id, Contact.is_active())
        .options(selectinload(Contact.senior))
    )
    return list(contacts_result.scalars().all())
