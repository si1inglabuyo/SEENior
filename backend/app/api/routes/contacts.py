from datetime import datetime, timezone
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.api.deps import get_current_user
from app.core.security import create_access_token, hash_password
from app.db.models import Contact, ContactType, Senior, User, UserRole
from app.db.session import get_db
from app.schemas.auth import Token
from app.schemas.contact import (
    ContactOut,
    FamilyContactOut,
    PairRequest,
    PairResponse,
    VerifyCodeRequest,
    VerifyCodeResponse,
)
from app.schemas.senior import SeniorOut

router = APIRouter(tags=["contacts"])

MAX_FAMILY_CONTACTS_PER_SENIOR = 5


async def _senior_by_valid_code(code: str, db: AsyncSession) -> Senior:
    """Returns the senior owning a live, unexpired invite code, or 400."""
    result = await db.execute(select(Senior).where(Senior.invite_code == code))
    senior = result.scalar_one_or_none()
    # Naive UTC, matching the column type — see the same note in seniors.py.
    now = datetime.now(timezone.utc).replace(tzinfo=None)
    if (
        senior is None
        or senior.invite_code_expires_at is None
        or senior.invite_code_expires_at < now
    ):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid or expired invite code")
    return senior


@router.post("/contacts/verify", response_model=VerifyCodeResponse)
async def verify_code(payload: VerifyCodeRequest, db: AsyncSession = Depends(get_db)) -> VerifyCodeResponse:
    # Look-up only: the family's Link screen calls this to show the senior on the
    # Connected screen before committing. Nothing is created and the code is NOT
    # consumed here — it's consumed by POST /contacts/pair.
    senior = await _senior_by_valid_code(payload.invite_code, db)
    return VerifyCodeResponse(senior=SeniorOut.model_validate(senior))


@router.post("/contacts/pair", response_model=PairResponse, status_code=status.HTTP_201_CREATED)
async def pair_contact(payload: PairRequest, db: AsyncSession = Depends(get_db)) -> PairResponse:
    senior = await _senior_by_valid_code(payload.invite_code, db)

    count_result = await db.execute(
        select(func.count())
        .select_from(Contact)
        .where(Contact.senior_id == senior.id, Contact.contact_type == ContactType.FAMILY)
    )
    if count_result.scalar_one() >= MAX_FAMILY_CONTACTS_PER_SENIOR:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"This senior already has {MAX_FAMILY_CONTACTS_PER_SENIOR} family contacts",
        )

    existing = await db.execute(select(User).where(User.username == payload.username))
    if existing.scalar_one_or_none() is not None:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Username already taken")

    user = User(
        username=payload.username,
        password_hash=hash_password(payload.password),
        role=UserRole.FAMILY_CONTACT,
        full_name=payload.full_name,
        phone=payload.phone,
    )
    db.add(user)
    await db.flush()  # assigns user.id without committing yet

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

    token = create_access_token(subject=user.username, role=user.role.value)
    return PairResponse(contact=ContactOut.model_validate(contact), token=Token(access_token=token))


@router.get("/seniors/{sync_id}/family-contacts", response_model=list[FamilyContactOut])
async def list_family_contacts(sync_id: UUID, db: AsyncSession = Depends(get_db)) -> list[FamilyContactOut]:
    # No auth: senior-facing (the senior app has no Users account, identifies by sync_id) —
    # same trust model as POST /alerts and POST /seniors.
    result = await db.execute(select(Senior).where(Senior.sync_id == sync_id))
    senior = result.scalar_one_or_none()
    if senior is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Senior not found")

    contacts_result = await db.execute(
        select(Contact)
        .where(Contact.senior_id == senior.id, Contact.contact_type == ContactType.FAMILY)
        .options(selectinload(Contact.user))
        .order_by(Contact.created_at.asc())
    )
    contacts = contacts_result.scalars().all()
    return [
        FamilyContactOut(
            id=c.id,
            full_name=c.user.full_name,
            phone=c.user.phone,
            relationship_label=c.relationship_label,
            contact_type=c.contact_type,
            created_at=c.created_at,
        )
        for c in contacts
    ]


@router.delete("/seniors/{sync_id}/family-contacts/{contact_id}", status_code=status.HTTP_204_NO_CONTENT)
async def remove_family_contact(
    sync_id: UUID, contact_id: int, db: AsyncSession = Depends(get_db)
) -> None:
    # No auth: senior-facing "Remove Contact" button. Scoped to this senior's own
    # sync_id so one senior can't delete another's links.
    result = await db.execute(select(Senior).where(Senior.sync_id == sync_id))
    senior = result.scalar_one_or_none()
    if senior is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Senior not found")

    contact_result = await db.execute(
        select(Contact).where(Contact.id == contact_id, Contact.senior_id == senior.id)
    )
    contact = contact_result.scalar_one_or_none()
    if contact is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Contact not found")

    await db.delete(contact)
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

    # Role-based access (CLAUDE.md §11): only a linked contact may view this list.
    link_result = await db.execute(
        select(Contact).where(Contact.senior_id == senior.id, Contact.user_id == current_user.id)
    )
    if link_result.scalar_one_or_none() is None:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Not linked to this senior")

    contacts_result = await db.execute(
        select(Contact)
        .where(Contact.senior_id == senior.id)
        .options(selectinload(Contact.senior))
    )
    return list(contacts_result.scalars().all())
