import asyncio
import logging
from datetime import datetime, timezone
from uuid import UUID

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, status
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_current_user
from app.core import push
from app.db.models import (
    Alert,
    AlertStatus,
    Contact,
    ContactType,
    DeviceToken,
    Senior,
    User,
    UserRole,
)
from app.db.session import SessionLocal, get_db
from app.schemas.alert import AlertCreate, AlertDispatchRequest, AlertOut

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/alerts", tags=["alerts"])


async def _family_device_tokens(db: AsyncSession, senior_id: int) -> list[str]:
    """Every push token belonging to a currently-linked, active family contact.

    `Contact.is_active()` is as load-bearing here as it is on the read paths: a family
    member who was unlinked must stop receiving pushes about that senior immediately,
    and a push carries the senior's name (CLAUDE.md §11).

    distinct() guards the case where one account somehow holds two live links to the
    same senior — the partial unique index makes that unlikely, but a duplicate here
    would ring the same handset twice for one emergency.
    """
    result = await db.execute(
        select(DeviceToken.token)
        .join(Contact, Contact.user_id == DeviceToken.user_id)
        .join(User, User.id == DeviceToken.user_id)
        .where(
            Contact.senior_id == senior_id,
            Contact.contact_type == ContactType.FAMILY,
            Contact.is_active(),
            User.is_active,
        )
        .distinct()
    )
    return list(result.scalars().all())


async def _deliver_alert_push(tokens: list[str], payload: push.AlertPush) -> None:
    """Sends the push and prunes any token FCM declared permanently dead.

    Runs as a background task, after the response has gone back to the senior's phone.
    firebase-admin's send is blocking, so it goes to a worker thread rather than
    stalling the event loop for every other request in flight.

    Failures are logged, never raised: by the time this runs the alert is already
    committed, and there is no request left to fail.
    """
    try:
        result = await asyncio.to_thread(push.send_alert, tokens, payload)
    except Exception:
        logger.exception("Alert push task crashed for alert %s", payload.alert_sync_id)
        return

    if result.attempted:
        logger.info(
            "Alert %s pushed to %d/%d device(s)",
            payload.alert_sync_id,
            result.sent,
            result.attempted,
        )

    if not result.stale_tokens:
        return

    # A fresh session: the request's session is closed by now.
    try:
        async with SessionLocal() as session:
            await session.execute(
                delete(DeviceToken).where(DeviceToken.token.in_(result.stale_tokens))
            )
            await session.commit()
        logger.info("Pruned %d dead device token(s)", len(result.stale_tokens))
    except Exception:
        logger.exception("Failed to prune dead device tokens")


@router.post("", response_model=AlertOut, status_code=status.HTTP_201_CREATED)
async def create_alert(
    payload: AlertCreate,
    background_tasks: BackgroundTasks,
    db: AsyncSession = Depends(get_db),
) -> Alert:
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

    # Queued only after the commit succeeded, so a push can never announce an alert that
    # does not exist. Scheduling it as a background task keeps FCM off the critical path:
    # the senior's phone gets its 201 whether or not Google is reachable, and the family
    # app's polling remains the fallback it always was.
    tokens = await _family_device_tokens(db, senior.id)
    if tokens:
        background_tasks.add_task(
            _deliver_alert_push,
            tokens,
            push.AlertPush(
                alert_sync_id=str(alert.sync_id),
                senior_sync_id=str(senior.sync_id),
                senior_name=senior.first_name,
                risk_level=alert.risk_level.value,
                trigger_type=alert.trigger_type.value,
            ),
        )
    else:
        # Worth a log line: an alert for a senior with no reachable family device is the
        # exact scenario the barangay tier exists for, and it is otherwise invisible.
        logger.warning(
            "Alert %s raised for senior %s with no registered family devices",
            alert.sync_id,
            senior.sync_id,
        )

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
