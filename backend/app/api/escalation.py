"""Server-side escalation clock: moves unanswered alerts up the chain.

Some phones (Transsion "Hiber") freeze the app and remove its alarms, so on-device timers
can't be trusted. This runs in the API process instead. The phone's own timer is kept too
(it is faster and works offline); the server waits a grace period longer and only acts when
the phone did not.
"""

import asyncio
import logging
from datetime import datetime, timedelta, timezone
from uuid import UUID

from sqlalchemy import select, text
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.api.routes.alerts import (
    append_step,
    barangay_phone_numbers,
    deliver_alert_push,
    deliver_alert_sms,
    deliver_family_sms_after_grace,
    family_device_tokens,
)
from app.core import push, sms
from app.core.config import settings
from app.db.models import (
    Alert,
    AlertStatus,
    ContactType,
    RiskLevel,
    Senior,
    TriggerType,
)
from app.db.session import SessionLocal

logger = logging.getLogger(__name__)

_family_sms_tasks: set[asyncio.Task] = set()

# Keep in sync with AlertEscalator.windowSecondsFor() in the Android app, so the server
# never escalates while the senior's countdown is still running.
SENIOR_WINDOW_SECONDS = {
    "sos": 10,           # short window, just to catch an accidental press
    "fall_pattern": 60,  # compressed window for falls
}
DEFAULT_SENIOR_WINDOW_SECONDS = 600


def utc_now() -> datetime:
    """Naive UTC, because the timestamp columns are TIMESTAMP WITHOUT TIME ZONE."""
    return datetime.now(timezone.utc).replace(tzinfo=None)


async def db_now(db: AsyncSession) -> datetime:
    """The database's own clock.

    `alerts.created_at` is written by Postgres, so deadlines must be compared against
    Postgres time. Python's clock can differ if the database runs in another time zone.
    """
    result = await db.execute(text("SELECT localtimestamp"))
    return result.scalar_one()


def senior_window(alert: Alert) -> int:
    return SENIOR_WINDOW_SECONDS.get(alert.trigger_type.value, DEFAULT_SENIOR_WINDOW_SECONDS)


def family_deadline(alert: Alert) -> datetime:
    """When the server stops waiting for the senior and notifies family."""
    return alert.created_at + timedelta(
        seconds=senior_window(alert) + settings.escalation_grace_seconds
    )


def has_family_tier(senior: Senior) -> bool:
    """True if the senior has at least one active family contact.

    Read from the contact rows each time, not from a stored flag, so pairing or
    unlinking a contact takes effect immediately.
    """
    return any(
        contact.contact_type == ContactType.FAMILY and contact.unlinked_at is None
        for contact in senior.contacts
    )


def barangay_deadline(alert: Alert, has_family: bool) -> datetime:
    """When the barangay is told.

    Normally one family-response window after family were notified. SOS, and seniors
    with no family contact, skip that wait.
    """
    if alert.trigger_type == TriggerType.SOS or not has_family:
        return family_deadline(alert)
    return family_deadline(alert) + timedelta(seconds=settings.family_response_seconds)


def has_step(alert: Alert, *steps: str) -> bool:
    return any((entry or {}).get("step") in steps for entry in (alert.escalation_steps or []))


# Steps meaning family were already notified (by the phone's timer or by this sweep).
# The sweep must see its own step, or it would re-fire on every pass.
FAMILY_NOTIFIED_STEPS = ("escalated_family", "escalated_family_server")

# Recorded when there is no family to notify, so the timeline doesn't claim a
# notification that was never sent.
NO_FAMILY_STEP = "no_family_contact"
FAMILY_TIER_STEPS = FAMILY_NOTIFIED_STEPS + (NO_FAMILY_STEP,)


async def sweep_overdue_alerts(db: AsyncSession) -> tuple[int, int]:
    """One pass of the clock. Returns (families notified, barangays escalated).

    Only `pending` alerts count; any response moves the alert off pending. Low-risk
    alerts are skipped. Deadlines are computed from `created_at`, so changing a window
    in the environment applies to alerts already in flight.
    """
    now = await db_now(db)
    result = await db.execute(
        select(Alert)
        .join(Senior, Senior.id == Alert.senior_id)
        .where(
            Alert.status == AlertStatus.PENDING,
            Alert.risk_level != RiskLevel.LOW,
            # Skip seniors who deleted their account.
            Senior.deleted_at.is_(None),
        )
        .options(selectinload(Alert.senior).selectinload(Senior.contacts))
    )

    notified_family = 0
    escalated_barangay = 0
    pushes: list[tuple[list[str], push.AlertPush]] = []
    # (numbers, message, log context)
    smses: list[tuple[list[str], str, str]] = []
    family_sms: list[tuple[UUID, int, str, str, str]] = []

    for alert in result.scalars().all():
        if alert.senior is None:
            continue

        has_family = has_family_tier(alert.senior)

        # The two tiers are checked independently so SOS (same deadline for both) and
        # overdue backlog alerts notify everyone in one pass.

        # Tier 2 backstop: skipped if the phone's own timer already did this.
        if now >= family_deadline(alert) and not has_step(alert, *FAMILY_TIER_STEPS):
            if not has_family:
                append_step(
                    alert,
                    NO_FAMILY_STEP,
                    reason="No family contact is linked; escalating straight to the barangay",
                )
                logger.info("Alert %s has no family tier; skipping to barangay", alert.sync_id)
            else:
                append_step(
                    alert,
                    "escalated_family_server",
                    reason="Senior did not answer; phone timer did not report in",
                )
                notified_family += 1
                logger.info("Server-side family escalation for alert %s", alert.sync_id)

                tokens = await family_device_tokens(db, alert.senior_id)
                if tokens:
                    pushes.append((
                        tokens,
                        push.AlertPush(
                            alert_sync_id=str(alert.sync_id),
                            senior_sync_id=str(alert.senior.sync_id),
                            senior_name=alert.senior.first_name,
                            risk_level=alert.risk_level.value,
                            trigger_type=alert.trigger_type.value,
                        ),
                    ))
                # Family SMS waits for push receipts, so it is launched after the commit.
                family_sms.append((
                    alert.sync_id,
                    alert.senior_id,
                    alert.senior.first_name,
                    alert.risk_level.value,
                    alert.trigger_type.value,
                ))

        # Tier 3.
        if now >= barangay_deadline(alert, has_family):
            alert.status = AlertStatus.ESCALATED
            reason = (
                "SOS pressed by the senior"
                if alert.trigger_type == TriggerType.SOS
                else "No family contact is linked to this senior"
                if not has_family
                else "No response from the senior or any family contact"
            )
            append_step(alert, "escalated_barangay_auto", reason=reason)
            escalated_barangay += 1
            logger.info("Auto-escalated alert %s to barangay", alert.sync_id)

            barangay_numbers = await barangay_phone_numbers(db, alert.senior.barangay)
            if barangay_numbers:
                smses.append((
                    barangay_numbers,
                    sms.barangay_alert_message(
                        alert.senior.first_name,
                        alert.senior.barangay,
                        alert.senior.address,
                        alert.risk_level.value,
                        reason,
                    ),
                    f"barangay/{alert.sync_id}",
                ))

    if notified_family or escalated_barangay:
        await db.commit()

    # Send only after the commit, so nothing announces a change that failed to save.
    for tokens, payload in pushes:
        await deliver_alert_push(tokens, payload)
    for numbers, message, context in smses:
        await deliver_alert_sms(numbers, message, context=context)
    for args in family_sms:
        # Own task so the grace-period sleep doesn't stall the sweep. Held in a set so it
        # isn't garbage collected.
        task = asyncio.create_task(deliver_family_sms_after_grace(*args))
        _family_sms_tasks.add(task)
        task.add_done_callback(_family_sms_tasks.discard)

    return notified_family, escalated_barangay


async def nudge_quiet_devices(db: AsyncSession) -> int:
    """Sends a wake push to senior phones that have stopped checking in.

    Some phones freeze background work, which stops passive monitoring. FCM can still
    reach a frozen app, so the server wakes it. Only phones that went quiet are nudged,
    which keeps battery cost near zero. Returns how many were nudged.
    """
    now = await db_now(db)
    quiet_before = now - timedelta(seconds=settings.device_quiet_after_seconds)
    nudge_before = now - timedelta(seconds=settings.device_nudge_every_seconds)

    result = await db.execute(
        select(Senior).where(
            Senior.push_token.is_not(None),
            Senior.deleted_at.is_(None),
            # A phone that never checked in is not handled here.
            Senior.last_seen_at.is_not(None),
            Senior.last_seen_at < quiet_before,
            # Never nudged counts as due.
            (Senior.last_nudge_at.is_(None)) | (Senior.last_nudge_at < nudge_before),
        )
    )
    seniors = result.scalars().all()
    if not seniors:
        return 0

    nudged = 0
    for senior in seniors:
        # Stamp even if the push fails, so a switched-off phone isn't retried every pass.
        senior.last_nudge_at = now

        outcome = await asyncio.to_thread(push.send_wake, senior.push_token)

        if outcome.stale_tokens:
            # Token is dead; clear it until the phone registers a new one.
            senior.push_token = None
            logger.info("Cleared dead push token for senior %s", senior.sync_id)
        elif outcome.sent:
            nudged += 1

    await db.commit()

    if nudged:
        logger.info("Nudged %d quiet device(s) awake", nudged)
    return nudged


async def escalation_sweep_loop() -> None:
    """Runs the sweep forever inside the API process.

    A plain background task, so no extra dependency or service. On Render's free tier the
    service can sleep and stop this loop; the dashboard polling and the health pinger keep
    it awake.
    """
    while True:
        try:
            async with SessionLocal() as db:
                await sweep_overdue_alerts(db)
                await nudge_quiet_devices(db)
        except Exception:
            # One bad pass must not kill the loop.
            logger.exception("Escalation sweep failed")
        await asyncio.sleep(settings.escalation_sweep_seconds)
