import asyncio
import logging
from datetime import datetime, timezone
from uuid import UUID

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, status
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.api.deps import get_current_user
from app.core import push, ratelimit, sms
from app.core.config import settings
from app.db.models import (
    Alert,
    AlertStatus,
    Contact,
    ContactType,
    DeviceToken,
    RiskLevel,
    Senior,
    User,
    UserRole,
)
from app.db.session import SessionLocal, get_db
from app.schemas.alert import (
    AlertCancel,
    AlertCreate,
    AlertDispatchRequest,
    AlertLocationUpdate,
    AlertOut,
    AlertSeverityUpdate,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/alerts", tags=["alerts"])


async def family_device_tokens(db: AsyncSession, senior_id: int) -> list[str]:
    """Push tokens for every active, currently-linked family contact of this senior."""
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


async def family_phone_numbers(db: AsyncSession, senior_id: int) -> list[str]:
    """Phone numbers of active, currently-linked family contacts.

    Contacts without a phone number are skipped.
    """
    result = await db.execute(
        select(User.phone)
        .join(Contact, Contact.user_id == User.id)
        .where(
            Contact.senior_id == senior_id,
            Contact.contact_type == ContactType.FAMILY,
            Contact.is_active(),
            User.is_active,
            User.phone.is_not(None),
        )
        .distinct()
    )
    return list(result.scalars().all())


async def family_phone_numbers_by_language(db: AsyncSession, senior_id: int) -> dict[str, list[str]]:
    """family_phone_numbers grouped by language ("en" / "fil"), so the SMS is written once per language."""
    result = await db.execute(
        select(User.phone, User.language_preference)
        .join(Contact, Contact.user_id == User.id)
        .where(
            Contact.senior_id == senior_id,
            Contact.contact_type == ContactType.FAMILY,
            Contact.is_active(),
            User.is_active,
            User.phone.is_not(None),
        )
        .distinct()
    )
    grouped: dict[str, list[str]] = {}
    for phone, language in result.all():
        grouped.setdefault(language if language == "fil" else "en", []).append(phone)
    return grouped


# Step written when a family phone confirms the push arrived. Its `user_id` lets the delayed
# SMS skip contacts who already got the push.
PUSH_RECEIVED_STEP = "push_received_family"


def push_received_user_ids(alert: Alert) -> set[int]:
    ids: set[int] = set()
    for entry in alert.escalation_steps or []:
        if (entry or {}).get("step") == PUSH_RECEIVED_STEP:
            try:
                ids.add(int(entry["user_id"]))
            except (KeyError, TypeError, ValueError):
                continue
    return ids


async def deliver_family_sms_after_grace(
    alert_sync_id: UUID,
    senior_id: int,
    senior_name: str,
    risk_level: str,
    trigger_type: str,
) -> None:
    """Texts the family contacts whose phones never confirmed the push.

    Waits `family_sms_grace_seconds`, then re-reads everything from the database, since
    contacts may have confirmed or the alert may have closed meanwhile. Never raises.
    """
    try:
        await asyncio.sleep(settings.family_sms_grace_seconds)
        async with SessionLocal() as db:
            alert = (
                await db.execute(select(Alert).where(Alert.sync_id == alert_sync_id))
            ).scalar_one_or_none()
            # Skip if family acknowledged or the alert is closed. ESCALATED is not skipped,
            # because an SOS reaches that state immediately and family still need the text.
            if alert is None or alert.status in (
                AlertStatus.ACKNOWLEDGED,
                AlertStatus.RESOLVED,
                AlertStatus.FALSE_POSITIVE,
            ):
                return

            received = push_received_user_ids(alert)
            rows = await db.execute(
                select(User.id, User.phone, User.language_preference)
                .join(Contact, Contact.user_id == User.id)
                .where(
                    Contact.senior_id == senior_id,
                    Contact.contact_type == ContactType.FAMILY,
                    Contact.is_active(),
                    User.is_active,
                    User.phone.is_not(None),
                )
                .distinct()
            )
            grouped: dict[str, list[str]] = {}
            for user_id, phone, language in rows.all():
                if user_id in received:
                    continue
                grouped.setdefault(language if language == "fil" else "en", []).append(phone)

        for language, numbers in grouped.items():
            await deliver_alert_sms(
                numbers,
                sms.family_alert_message(senior_name, risk_level, trigger_type, language=language),
                context=f"family/{alert_sync_id}",
            )
    except Exception:
        logger.exception("Delayed family SMS task crashed for alert %s", alert_sync_id)


async def barangay_phone_numbers(db: AsyncSession, barangay: str) -> list[str]:
    """Phone numbers of active barangay responders assigned to this barangay."""
    result = await db.execute(
        select(User.phone).where(
            User.role == UserRole.BARANGAY_RESPONDER,
            User.barangay == barangay,
            User.is_active,
            User.deleted_at.is_(None),
            User.phone.is_not(None),
        )
    )
    return list(result.scalars().all())


async def deliver_alert_sms(numbers: list[str], message: str, *, context: str) -> None:
    """Sends the SMS fallback as a background task. Failures are logged, never raised."""
    try:
        result = await sms.send_sms(numbers, message)
    except Exception:
        logger.exception("Alert SMS task crashed (%s)", context)
        return

    if result.attempted:
        logger.info(
            "Alert SMS (%s) sent to %d/%d number(s)", context, result.sent, result.attempted
        )


async def deliver_alert_push(tokens: list[str], payload: push.AlertPush) -> None:
    """Sends the push and prunes any tokens FCM says are permanently dead.

    Runs as a background task; the blocking send goes to a worker thread. Failures are
    logged, never raised.
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
    # No auth: the senior's phone has no account and identifies itself by sync_id.
    # Known simplification: nothing checks that the caller owns that sync_id.
    result = await db.execute(
        select(Senior)
        .where(Senior.sync_id == payload.senior_sync_id)
        .options(selectinload(Senior.contacts))
    )
    senior = result.scalar_one_or_none()
    if senior is None or senior.deleted_at is not None:
        # A deleted senior's phone is wiped, so a stale queued alert belongs to no one.
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Senior not found")

    # Per senior, not per address: a real phone raises far fewer alerts than this, and a
    # per-IP cap could block a genuine emergency from a shared network.
    await ratelimit.check("create-alert", str(payload.senior_sync_id), limit=30, window_seconds=3600)

    # Convert to naive UTC to match the timestamp columns. A value with no offset is
    # treated as already UTC.
    triggered_at = payload.triggered_at
    if triggered_at is not None and triggered_at.tzinfo is not None:
        triggered_at = triggered_at.astimezone(timezone.utc).replace(tzinfo=None)

    alert = Alert(
        senior_id=senior.id,
        risk_level=payload.risk_level,
        trigger_type=payload.trigger_type,
        status=AlertStatus.PENDING,
        location_cluster_id=payload.location_cluster_id,
        escalation_steps=payload.escalation_steps,
        triggered_at=triggered_at,
    )

    # A POST /alerts is always the phone escalating to the family tier, so record that step
    # now. The sweep in api/escalation.py treats these steps as "family tier handled";
    # without one it would notify family a second time. The "Lives alone" badge also reads
    # the `no_family_contact` step.
    has_family = any(
        c.contact_type == ContactType.FAMILY and c.unlinked_at is None
        for c in senior.contacts
    )
    if has_family:
        append_step(alert, "escalated_family", reason="Senior did not answer the wellness prompt")
    else:
        append_step(
            alert,
            "no_family_contact",
            reason="No family contact is linked; escalating straight to the barangay",
        )

    db.add(alert)
    await db.commit()
    await db.refresh(alert)

    # Queued after the commit, so a push never announces an alert that wasn't saved.
    tokens = await family_device_tokens(db, senior.id)
    if tokens:
        background_tasks.add_task(
            deliver_alert_push,
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
        # Worth logging: this is the case the barangay tier exists for.
        logger.warning(
            "Alert %s raised for senior %s with no registered family devices",
            alert.sync_id,
            senior.sync_id,
        )

    # SMS fallback only for family phones that don't confirm the push within the grace
    # period. Seniors with no family go straight to the barangay (texted by the sweep).
    if has_family:
        background_tasks.add_task(
            deliver_family_sms_after_grace,
            alert.sync_id,
            senior.id,
            senior.first_name,
            alert.risk_level.value,
            alert.trigger_type.value,
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

    # Family only see alerts raised since they paired; barangay responders see the full
    # history within their barangay.
    linked_since: datetime | None = None
    if current_user.role == UserRole.BARANGAY_RESPONDER:
        allowed = current_user.barangay == senior.barangay
    else:
        # is_active() stops an unlinked family member from reading old alerts.
        link_result = await db.execute(
            select(Contact).where(
                Contact.senior_id == senior.id,
                Contact.user_id == current_user.id,
                Contact.contact_type == ContactType.FAMILY,
                Contact.is_active(),
            )
        )
        link = link_result.scalar_one_or_none()
        allowed = link is not None
        if link is not None:
            # created_at is when this pairing began, so earlier alerts stay private.
            linked_since = link.created_at

    if not allowed:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Not authorized for this senior")

    query = select(Alert).where(Alert.senior_id == senior.id)
    if linked_since is not None:
        query = query.where(Alert.created_at >= linked_since)
    if status_filter is not None:
        query = query.where(Alert.status == status_filter)
    query = query.order_by(Alert.created_at.desc())

    alerts_result = await db.execute(query)
    return list(alerts_result.scalars().all())


async def _family_alert(sync_id: UUID, db: AsyncSession, current_user: User) -> Alert:
    """Fetches an alert and checks the user is a currently linked family contact for it."""
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


def append_step(alert: Alert, step: str, **extra: str | None) -> None:
    steps = list(alert.escalation_steps or [])
    # Naive UTC, matching Alert.created_at/resolved_at's column type.
    at = datetime.now(timezone.utc).replace(tzinfo=None).isoformat()
    steps.append({"step": step, "at": at, **extra})
    alert.escalation_steps = steps


@router.patch("/{sync_id}/cancel", response_model=AlertOut)
async def cancel_alert(
    sync_id: UUID, payload: AlertCancel, db: AsyncSession = Depends(get_db)
) -> Alert:
    """The senior answers the wellness prompt, closing the incident.

    No JWT, since the senior has no account; both sync_ids are required instead.
    Idempotent, so a phone retrying after a lost connection gets the closed alert back.
    """
    result = await db.execute(
        select(Alert).where(Alert.sync_id == sync_id).options(selectinload(Alert.senior))
    )
    alert = result.scalar_one_or_none()
    if alert is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Alert not found")

    if alert.senior is None or alert.senior.sync_id != payload.senior_sync_id:
        # Same 404 as above so we don't reveal that the alert exists.
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Alert not found")

    if alert.status in (AlertStatus.RESOLVED, AlertStatus.FALSE_POSITIVE):
        return alert

    alert.status = AlertStatus.RESOLVED
    alert.resolved_at = datetime.now(timezone.utc).replace(tzinfo=None)
    # RESOLVED, with the timeline recording that the senior closed it, so no new enum value.
    append_step(alert, "self_cancelled_senior", by=alert.senior.first_name)
    await db.commit()
    await db.refresh(alert)
    return alert


# Severity order, so the endpoint below can raise a level but never lower it.
RISK_ORDER = [RiskLevel.LOW, RiskLevel.MEDIUM, RiskLevel.HIGH]


@router.patch("/{sync_id}/severity", response_model=AlertOut)
async def update_alert_severity(
    sync_id: UUID, payload: AlertSeverityUpdate, db: AsyncSession = Depends(get_db)
) -> Alert:
    """Raises an open alert's risk level after the phone re-classified it.

    Upgrade-only and idempotent. Closed alerts are left alone. No JWT; the pair of
    sync_ids is the credential.
    """
    result = await db.execute(
        select(Alert).where(Alert.sync_id == sync_id).options(selectinload(Alert.senior))
    )
    alert = result.scalar_one_or_none()
    if alert is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Alert not found")

    if alert.senior is None or alert.senior.sync_id != payload.senior_sync_id:
        # Same 404 as the cancel route so we don't reveal that the alert exists.
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Alert not found")

    if alert.status in (AlertStatus.RESOLVED, AlertStatus.FALSE_POSITIVE):
        return alert

    if RISK_ORDER.index(payload.risk_level) <= RISK_ORDER.index(alert.risk_level):
        return alert

    previous = alert.risk_level.value
    alert.risk_level = payload.risk_level
    append_step(alert, "severity_raised", was=previous, now=payload.risk_level.value)
    await db.commit()
    await db.refresh(alert)
    return alert



@router.patch("/{sync_id}/location", response_model=AlertOut)
async def update_alert_location(
    sync_id: UUID, payload: AlertLocationUpdate, db: AsyncSession = Depends(get_db)
) -> Alert:
    """Fills in the location of an alert posted before its GPS fix arrived.

    Set-once and idempotent: a second value is ignored. Closed alerts are still accepted,
    since an SOS can be resolved before its location arrives. No JWT; the pair of
    sync_ids is the credential.
    """
    result = await db.execute(
        select(Alert).where(Alert.sync_id == sync_id).options(selectinload(Alert.senior))
    )
    alert = result.scalar_one_or_none()
    if alert is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Alert not found")

    if alert.senior is None or alert.senior.sync_id != payload.senior_sync_id:
        # Same 404 as the routes above so we don't reveal that the alert exists.
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Alert not found")

    if alert.location_cluster_id is not None:
        return alert

    alert.location_cluster_id = payload.location_cluster_id
    append_step(alert, "location_added")
    await db.commit()
    await db.refresh(alert)
    return alert


@router.patch("/{sync_id}/acknowledge", response_model=AlertOut)
async def acknowledge_alert(
    sync_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> Alert:
    """Family taps "Acknowledge Alert", which halts the barangay escalation clock."""
    alert = await _family_alert(sync_id, db, current_user)
    if alert.status != AlertStatus.PENDING:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Alert is not pending")
    alert.status = AlertStatus.ACKNOWLEDGED
    append_step(alert, "acknowledged_family")
    await db.commit()
    await db.refresh(alert)
    return alert


@router.post("/{sync_id}/received", response_model=AlertOut)
async def push_received(
    sync_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> Alert:
    """A family phone confirms the push reached it, so the server doesn't text them.

    Idempotent per user. The row is locked so two contacts confirming at once don't
    overwrite each other.
    """
    alert = await _family_alert(sync_id, db, current_user)
    await db.refresh(alert, with_for_update=True)
    if current_user.id not in push_received_user_ids(alert):
        append_step(alert, PUSH_RECEIVED_STEP, user_id=str(current_user.id))
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
    """Family requests a barangay welfare check, same as the automatic escalation."""
    alert = await _family_alert(sync_id, db, current_user)
    if alert.status in (AlertStatus.RESOLVED, AlertStatus.FALSE_POSITIVE):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Alert already closed")
    alert.status = AlertStatus.ESCALATED
    append_step(alert, "escalated_barangay", reason=payload.reason, notes=payload.notes)
    await db.commit()
    await db.refresh(alert)
    return alert


@router.patch("/{sync_id}/false-positive", response_model=AlertOut)
async def mark_false_positive(
    sync_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> Alert:
    """Family says the alert was raised in error.

    Separate from `resolve` because it counts toward the false-positive rate, and the
    senior's phone uses it to loosen that time block's trigger.
    """
    alert = await _family_alert(sync_id, db, current_user)
    if alert.status in (AlertStatus.RESOLVED, AlertStatus.FALSE_POSITIVE):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Alert already closed")
    alert.status = AlertStatus.FALSE_POSITIVE
    alert.resolved_at = datetime.now(timezone.utc).replace(tzinfo=None)
    # Record who closed it (the login handle if there is no full name).
    append_step(alert, "false_positive_family", by=current_user.full_name or current_user.username)
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
    # Record who closed it (the login handle if there is no full name).
    append_step(alert, "resolved_family", by=current_user.full_name or current_user.username)
    await db.commit()
    await db.refresh(alert)
    return alert
