from datetime import datetime, timezone
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_current_user
from app.db.models import Alert, AlertStatus, Contact, ContactType, Senior, User, UserRole
from app.db.session import get_db
from app.schemas.alert import AlertCreate, AlertDispatchRequest, AlertOut

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
        # is_active() is load-bearing, not tidiness: without it a soft-unlinked family
        # member would keep reading their former senior's alerts forever.
        link_result = await db.execute(
            select(Contact).where(
                Contact.senior_id == senior.id,
                Contact.user_id == current_user.id,
                Contact.contact_type == ContactType.FAMILY,
                Contact.is_active(),
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


async def _family_alert(sync_id: UUID, db: AsyncSession, current_user: User) -> Alert:
    """Fetches an alert and confirms current_user is a CURRENTLY linked family contact for
    its senior — the write-side counterpart of list_alerts' read-side authorization.
    Unlinking revokes the right to acknowledge/dispatch/resolve, not just to read."""
    result = await db.execute(select(Alert).where(Alert.sync_id == sync_id))
    alert = result.scalar_one_or_none()
    if alert is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Alert not found")

    link_result = await db.execute(
        select(Contact).where(
            Contact.senior_id == alert.senior_id,
            Contact.user_id == current_user.id,
            Contact.contact_type == ContactType.FAMILY,
            Contact.is_active(),
        )
    )
    if link_result.scalar_one_or_none() is None:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Not authorized for this alert")
    return alert


def _append_step(alert: Alert, step: str, **extra: str | None) -> None:
    steps = list(alert.escalation_steps or [])
    # Naive UTC, matching Alert.created_at/resolved_at's column type.
    at = datetime.now(timezone.utc).replace(tzinfo=None).isoformat()
    steps.append({"step": step, "at": at, **extra})
    alert.escalation_steps = steps


@router.patch("/{sync_id}/acknowledge", response_model=AlertOut)
async def acknowledge_alert(
    sync_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> Alert:
    """First family response tier (CLAUDE.md §7): family taps "Acknowledge Alert",
    halting the barangay escalation clock while they follow up directly."""
    alert = await _family_alert(sync_id, db, current_user)
    if alert.status != AlertStatus.PENDING:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Alert is not pending")
    alert.status = AlertStatus.ACKNOWLEDGED
    _append_step(alert, "acknowledged_family")
    await db.commit()
    await db.refresh(alert)
    return alert


@router.patch("/{sync_id}/dispatch", response_model=AlertOut)
async def dispatch_barangay(
    sync_id: UUID,
    payload: AlertDispatchRequest,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> Alert:
    """Final escalation tier (CLAUDE.md §7): family requests an official barangay
    welfare check, same as the automatic no-family-response escalation would."""
    alert = await _family_alert(sync_id, db, current_user)
    if alert.status in (AlertStatus.RESOLVED, AlertStatus.FALSE_POSITIVE):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Alert already closed")
    alert.status = AlertStatus.ESCALATED
    _append_step(alert, "escalated_barangay", reason=payload.reason, notes=payload.notes)
    await db.commit()
    await db.refresh(alert)
    return alert


@router.patch("/{sync_id}/resolve", response_model=AlertOut)
async def resolve_alert(
    sync_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> Alert:
    """Family confirms the senior is safe, closing the incident."""
    alert = await _family_alert(sync_id, db, current_user)
    if alert.status in (AlertStatus.RESOLVED, AlertStatus.FALSE_POSITIVE):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Alert already closed")
    alert.status = AlertStatus.RESOLVED
    alert.resolved_at = datetime.now(timezone.utc).replace(tzinfo=None)
    _append_step(alert, "resolved_family")
    await db.commit()
    await db.refresh(alert)
    return alert
