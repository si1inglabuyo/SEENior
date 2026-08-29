"""The server-side escalation clock: what moves an unanswered alert up the chain.

Why this is here and not on the senior's phone
----------------------------------------------
Three on-device alarm mechanisms were measured failing on the Infinix test handset
(WorkManager, setExactAndAllowWhileIdle, setAlarmClock). The cause was not Android's own
Doze but Transsion's "Hiber" layer, which freezes the app seconds after the screen goes
off and removes its alarms from AlarmManager -- the alarm is not delayed, it is gone.

Nothing running on that phone can be relied on to fire on time. This can: it executes in
the API process, which no handset can freeze. FCM delivery was never the problem (Play
Services holds an exemption the app cannot get); the *timer* was.

The phone's own timer is deliberately kept as well. It is faster and works with no signal,
which is what CLAUDE.md §1 promises. This module waits a grace period longer than the
phone does, so it only acts when the phone did not.
"""

import asyncio
import logging
from datetime import datetime, timedelta, timezone

from sqlalchemy import select, text
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.api.routes.alerts import (
    append_step,
    deliver_alert_push,
    family_device_tokens,
)
from app.core import push
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

# Mirrors AlertEscalator.windowSecondsFor() in the Android app -- how long the senior gets
# to answer the wellness prompt, which depends on what raised the alert. If these two ever
# disagree, the server could escalate while the senior's own countdown is still visibly
# running: exactly the bug that table was written to prevent on the device side.
SENIOR_WINDOW_SECONDS = {
    "sos": 10,           # long enough to catch a pocket press, no longer (CLAUDE.md §7)
    "fall_pattern": 60,  # Layer 0 gets a compressed window (CLAUDE.md §5)
}
DEFAULT_SENIOR_WINDOW_SECONDS = 600


def utc_now() -> datetime:
    """Naive UTC. Every timestamp column in this schema is TIMESTAMP WITHOUT TIME ZONE,
    and asyncpg refuses a timezone-aware value against one."""
    return datetime.now(timezone.utc).replace(tzinfo=None)


async def db_now(db: AsyncSession) -> datetime:
    """"Now" as the database itself reckons it.

    Not the same thing as utc_now(), and the difference is load-bearing. `alerts.created_at`
    is filled in by Postgres (server_default=func.now()) and lands in a TIMESTAMP WITHOUT
    TIME ZONE column, which keeps the server's wall-clock reading and throws the zone away.
    Comparing that against Python's UTC clock only works if the database happens to run in
    UTC. Render's does; a development machine set to Manila time does not, and there the
    stored value reads eight hours ahead -- so every deadline computed from it sits eight
    hours in the future and nothing ever escalates. That is not a failure anyone would
    notice quickly: the sweep runs, finds nothing overdue, and says nothing.

    Asking the database for its own clock puts both sides of the comparison on the same one,
    whatever zone either machine is set to.
    """
    result = await db.execute(text("SELECT localtimestamp"))
    return result.scalar_one()


def senior_window(alert: Alert) -> int:
    return SENIOR_WINDOW_SECONDS.get(alert.trigger_type.value, DEFAULT_SENIOR_WINDOW_SECONDS)


def family_deadline(alert: Alert) -> datetime:
    """When the server gives up on the senior answering and notifies family itself."""
    return alert.created_at + timedelta(
        seconds=senior_window(alert) + settings.escalation_grace_seconds
    )


def has_family_tier(senior: Senior) -> bool:
    """Whether this senior has anyone on the family tier at all.

    Derived from the contact rows on every pass rather than read from a flag the senior set
    once. A senior who lives alone today may have a daughter pair with them next month, and
    the reverse; a stored answer would have to be kept in step with that by hand, and the
    cost of it being stale is an alert routed to a tier that cannot answer. The contact rows
    are the thing that is actually true.

    `unlinked_at is None` is the same soft-unlink filter every other read path applies -- a
    family member who was removed is not a tier, and counting them would make the system
    wait out a response window on someone who no longer receives anything.
    """
    return any(
        contact.contact_type == ContactType.FAMILY and contact.unlinked_at is None
        for contact in senior.contacts
    )


def barangay_deadline(alert: Alert, has_family: bool) -> datetime:
    """When the barangay is told.

    In the ordinary case this is one family-response window after the family were notified:
    a detected anomaly is the system's *guess* that something is wrong, so it is walked up
    the tiers, giving the family their turn and sparing the barangay a call-out for
    something a daughter two streets away can handle.

    Two cases skip that wait, for the same underlying reason -- there is nothing left for
    the middle tier to add:

    An SOS is not a guess. The senior has consciously said they need help, so once the
    pocket-press window closes, everyone is told at once (CLAUDE.md §7). Making someone who
    has already pressed the button wait another two minutes while the system re-establishes
    what they just told it is the opposite of what the button is for.

    A senior with no family contact has no tier 2. Waiting `family_response_seconds` for an
    acknowledgement that cannot arrive delays the only responder who can actually come, by
    exactly the length of a window held open for nobody. The chain compresses to senior ->
    barangay, and the barangay tier is what makes that safe: it is always present, so a
    senior living alone is never left with a chain that runs out of people.
    """
    if alert.trigger_type == TriggerType.SOS or not has_family:
        return family_deadline(alert)
    return family_deadline(alert) + timedelta(seconds=settings.family_response_seconds)


def has_step(alert: Alert, *steps: str) -> bool:
    return any((entry or {}).get("step") in steps for entry in (alert.escalation_steps or []))


# Both of these mean "the family tier has already been told". The first is what the phone
# writes when its own timer wins; the second is what this sweep writes when it does not.
#
# Checking only the phone's name was a real bug, caught the first time this ran against a
# database: the sweep could never see its own step, so it re-fired on every pass -- twenty
# duplicate timeline entries in ninety seconds, and, for a senior who actually has family
# devices registered, twenty duplicate pushes. An emergency notification that arrives
# twenty times is worse than one that arrives once; people turn those off.
FAMILY_NOTIFIED_STEPS = ("escalated_family", "escalated_family_server")

# ...and this means "the family rung has been dealt with", which is not the same claim.
# A senior with nobody on tier 2 still passes the family deadline, and the sweep still has
# to record that it got there -- but recording it as a notification would put a line in the
# audit trail saying family were told when no message was addressed to anyone. The
# timeline is evidence; it has to say what actually happened.
NO_FAMILY_STEP = "no_family_contact"
FAMILY_TIER_STEPS = FAMILY_NOTIFIED_STEPS + (NO_FAMILY_STEP,)


async def sweep_overdue_alerts(db: AsyncSession) -> tuple[int, int]:
    """One pass of the clock. Returns (families notified, barangays escalated).

    Only `pending` alerts are considered, and that single check is the entire test for
    "nobody has answered". Every possible response already moves the row off pending --
    the senior's own "I'm safe" resolves it; family acknowledge/dispatch/resolve all move
    it -- so there is no separate flag to keep in step with reality.

    Low-risk alerts are skipped. CLAUDE.md §5 says low risk is logged only, and sending a
    responder to someone's house over an alert the system itself judged not worth a
    notification is how a barangay learns to stop reading the dashboard.

    Both deadlines are computed from `created_at` rather than stored in a column. That
    means no migration, and it means changing a window in the environment immediately
    changes the behaviour of alerts already in flight -- convenient while demonstrating,
    and one less field for the two databases to have to agree about.
    """
    # The database's clock, not Python's -- see db_now(). These deadlines are measured
    # against created_at, which Postgres wrote, so Postgres has to be the one asked what
    # time it is now.
    now = await db_now(db)
    result = await db.execute(
        select(Alert)
        .where(Alert.status == AlertStatus.PENDING, Alert.risk_level != RiskLevel.LOW)
        .options(selectinload(Alert.senior).selectinload(Senior.contacts))
    )

    notified_family = 0
    escalated_barangay = 0
    pushes: list[tuple[list[str], push.AlertPush]] = []

    for alert in result.scalars().all():
        if alert.senior is None:
            continue

        # Recomputed per alert rather than cached: a family contact can pair while an alert
        # is mid-flight, and when they do this senior stops being alone from the very next
        # pass -- the barangay deadline slides back out to the full window and the family
        # get their turn after all. Nothing has to be migrated for that to happen.
        has_family = has_family_tier(alert.senior)

        # The two tiers are evaluated independently, and neither one short-circuits the
        # other. That matters for SOS, where both deadlines are the same instant: an
        # `elif`, or the `continue` this loop used to have, would fire the barangay and
        # leave the family never told at all -- the exact opposite of the "notify everyone
        # at once" the button promises.
        #
        # It also means a backlog alert -- one that sat through a restart and is now past
        # both deadlines -- notifies both tiers in a single pass rather than silently
        # skipping the family. When the system has already been late, telling more people
        # is the safer failure.

        # Tier 2, as a backstop: if the phone's own timer already did this, the step is on
        # the timeline and there is nothing to do. This is the rung Hiber eats.
        if now >= family_deadline(alert) and not has_step(alert, *FAMILY_TIER_STEPS):
            if not has_family:
                # Recorded, not notified. The senior lives alone (or every pairing has been
                # unlinked), so this rung is passed through rather than acted on -- and the
                # barangay deadline above is already the same instant, so tier 3 fires in
                # this very pass. Written before that so the timeline reads in order.
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

        # Tier 3.
        if now >= barangay_deadline(alert, has_family):
            alert.status = AlertStatus.ESCALATED
            append_step(
                alert,
                "escalated_barangay_auto",
                reason=(
                    "SOS pressed by the senior"
                    if alert.trigger_type == TriggerType.SOS
                    else "No family contact is linked to this senior"
                    if not has_family
                    else "No response from the senior or any family contact"
                ),
            )
            escalated_barangay += 1
            logger.info("Auto-escalated alert %s to barangay", alert.sync_id)

    if notified_family or escalated_barangay:
        await db.commit()

    # Pushes go out only after the commit, so a notification can never announce a state
    # change that failed to save. Same rule create_alert already follows.
    for tokens, payload in pushes:
        await deliver_alert_push(tokens, payload)

    return notified_family, escalated_barangay


async def nudge_quiet_devices(db: AsyncSession) -> int:
    """Pushes a wake to every senior phone that has stopped checking in.

    The problem this solves is not Android's Doze but what the handset does inside it.
    Measured on the Infinix X6885 on 2026-08-29: the sensor service's own five-minute
    coroutine loop produced ONE sample in twenty-four minutes, and `dumpsys jobscheduler`
    showed the persisted fifteen-minute watchdog running three times in thirteen and a
    half hours -- nothing at all between 00:30 and 13:00. Passive monitoring was therefore
    not running overnight, which is precisely when a senior living alone is least observed
    and the claim in CLAUDE.md 1 matters most.

    It is the same answer the escalation deadline reached at the top of this module, applied
    to sampling instead of to escalation: the phone cannot hold a clock, so the server holds
    it, and FCM is the one channel Play Services can still deliver into a frozen app.

    **A healthy phone is never nudged.** The condition is silence, not a schedule -- a
    handset checking in every fifteen minutes never crosses `device_quiet_after_seconds`
    and costs nothing. That is what keeps this inside the CLAUDE.md 10 battery budget: the
    push is spent only on the case where monitoring has already stopped.

    Returns how many were nudged, so a caller can log it. Never raises: this shares a loop
    with the escalation sweep, and failing to wake a phone must not stop an overdue alert
    from being escalated.
    """
    now = db_now()
    quiet_before = now - timedelta(seconds=settings.device_quiet_after_seconds)
    nudge_before = now - timedelta(seconds=settings.device_nudge_every_seconds)

    result = await db.execute(
        select(Senior).where(
            Senior.push_token.is_not(None),
            # NULL last_seen_at is a phone that has NEVER checked in -- it has no token
            # either, so it cannot match the clause above and is not a case to handle here.
            Senior.last_seen_at.is_not(None),
            Senior.last_seen_at < quiet_before,
            # NULL means never nudged, which is due by definition.
            (Senior.last_nudge_at.is_(None)) | (Senior.last_nudge_at < nudge_before),
        )
    )
    seniors = result.scalars().all()
    if not seniors:
        return 0

    nudged = 0
    for senior in seniors:
        # Stamped whether or not the push lands. A phone that is genuinely off -- flat, or
        # switched off and left at home -- fails every time, and stamping only on success
        # would retry it on every pass forever.
        senior.last_nudge_at = now

        outcome = await asyncio.to_thread(push.send_wake, senior.push_token)

        if outcome.stale_tokens:
            # FCM says this token is dead for good (reinstall, cleared data, rotation).
            # Clearing it stops the sweep pushing at nothing until the phone next checks
            # in and registers a fresh one.
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

    A plain background task rather than APScheduler or a second Render service: no new
    dependency, nothing extra to deploy or pay for.

    The tradeoff is real and worth stating in the defense rather than hiding: Render's
    free tier spins an idle web service down, and this loop stops when it does. Two things
    cover that. The dashboard polls every ten seconds, so the service stays awake as long
    as anyone is watching it; and the barangay alert list runs this same sweep on every
    request, so even after a cold start an incident is never staler than one refresh.
    """
    while True:
        try:
            async with SessionLocal() as db:
                await sweep_overdue_alerts(db)
                # Same pass, same session. An overdue alert is the more urgent of the
                # two and goes first; a phone that has been quiet for fifteen minutes
                # can wait the milliseconds that takes.
                await nudge_quiet_devices(db)
        except Exception:
            # One bad pass must never kill the loop. It is the only thing standing
            # between tier 2 and tier 3.
            logger.exception("Escalation sweep failed")
        await asyncio.sleep(settings.escalation_sweep_seconds)
