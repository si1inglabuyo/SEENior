from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_current_user
from app.db.models import Alert, AlertStatus, Contact, ContactType, Senior, User, UserRole
from app.db.session import get_db
from app.schemas.alert import AlertCreate, AlertOut

router = APIRouter(prefix="/alerts", tags=["alerts"])


@router.post("", response_model=AlertOut, status_code=status.HTTP_201_CREATED)
async def create_alert(payload: AlertCreate, db: AsyncSession = Depends(get_db)) -> Alert:
    # No auth — the senior's phone has no Users account (CLAUDE.md §2) and identifies
    # itself only by its own sync_id. Known simplification: nothing here verifies the
    # caller genuinely owns that sync_id. Acceptable for the demo; a per-device secret
    # issued alongside sync_id in POST /seniors would close this if hardened later.
    result = await db.execute(select(Senior).where(Senior.sync_id == payload.senior_sync_id))
    senior = result.scalar_one_or_none()
    if senior is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Senior not found")

    alert = Alert(
        senior_id=senior.id,
        risk_level=payload.risk_level,
        trigger_type=payload.trigger_type,
        status=AlertStatus.PENDING,
        location_cluster_id=payload.location_cluster_id,
        escalation_steps=payload.escalation_steps,
    )
    db.add(alert)
    await db.commit()
    await db.refresh(alert)
    return alert


@router.get("", response_model=list[AlertOut])
async def list_alerts(
    senior_sync_id: UUID,
    status_filter: AlertStatus | None = None,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> list[Alert]:
    result = await db.execute(select(Senior).where(Senior.sync_id == senior_sync_id))
    senior = result.scalar_one_or_none()
    if senior is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Senior not found")

    if current_user.role == UserRole.BARANGAY_RESPONDER:
        allowed = current_user.barangay == senior.barangay
    else:
        link_result = await db.execute(
            select(Contact).where(
                Contact.senior_id == senior.id,
                Contact.user_id == current_user.id,
                Contact.contact_type == ContactType.FAMILY,
            )
        )
        allowed = link_result.scalar_one_or_none() is not None

    if not allowed:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Not authorized for this senior")

    query = select(Alert).where(Alert.senior_id == senior.id)
    if status_filter is not None:
        query = query.where(Alert.status == status_filter)
    query = query.order_by(Alert.created_at.desc())

    alerts_result = await db.execute(query)
    return list(alerts_result.scalars().all())
