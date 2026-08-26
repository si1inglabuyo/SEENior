"""Everything the barangay responder's dashboard calls.

Every route here is gated twice: the JWT must belong to a barangay_responder account, and
the senior in question must sit in that responder's own barangay. CLAUDE.md §11 requires
both -- one responder must never see another barangay's seniors.
"""

import logging
from collections import Counter
from datetime import timedelta
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.api.deps import require_role
from app.api.escalation import append_step, db_now, sweep_overdue_alerts
from app.db.models import Alert, AlertStatus, Senior, User, UserRole
from app.db.session import get_db
from app.schemas.barangay import (
    BarangayAlertOut,
    BarangaySeniorOut,
    BarangayStats,
    DayCount,
    ResponderAction,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/barangay", tags=["barangay"])

# Built once at import time; FastAPI calls the inner check per request.
responder_only = require_role(UserRole.BARANGAY_RESPONDER)


def _assigned_barangay(responder: User) -> str:
    """The responder's barangay, refusing to continue if they have none.

    Failing closed matters here: `barangay` is nullable on users, and an account with a
    NULL barangay compared against seniors would either match nothing or -- worse, if a
    senior record were ever saved with a blank barangay -- match someone it should not.
    """
    if not responder.barangay:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="This responder account has no barangay assigned. Contact the OSCA officer.",
        )
    return responder.barangay


def _alert_out(alert: Alert) -> BarangayAlertOut:
    senior = alert.senior
    return BarangayAlertOut(
        sync_id=alert.sync_id,
        risk_level=alert.risk_level,
        trigger_type=alert.trigger_type,
        status=alert.status,
        escalation_steps=alert.escalation_steps,
        created_at=alert.created_at,
        resolved_at=alert.resolved_at,
        senior_sync_id=senior.sync_id,
        senior_name=f"{senior.first_name} {senior.last_name}",
        senior_age=senior.age,
        senior_address=senior.address,
        senior_mobile=senior.mobile_number,
    )


@router.get("/alerts", response_model=list[BarangayAlertOut])
async def list_barangay_alerts(
    scope: str = Query("active", pattern="^(active|history)$"),
    db: AsyncSession = Depends(get_db),
    responder: User = Depends(responder_only),
) -> list[BarangayAlertOut]:
    """The incident queue (`active`) and the incident log (`history`).

    `active` is the work queue: incidents that have reached this barangay and are not
    closed. `history` is everything already acted on, for the log view.

    Both deliberately exclude `pending`. An alert still inside the senior's own answer
    window, or one the family is in the middle of handling, has not reached the barangay
    yet -- showing it early would both leak an incident that is not theirs and train
    responders to react to alerts that resolve themselves a minute later.
    """
    # The same sweep the background loop runs. It is one indexed query, and running it
    # here means a responder opening the dashboard after the free-tier service was asleep
    # sees the incident immediately rather than up to a sweep-interval later.
    await sweep_overdue_alerts(db)

    barangay = _assigned_barangay(responder)

    query = (
        select(Alert)
        .join(Senior, Senior.id == Alert.senior_id)
        .where(Senior.barangay == barangay, Alert.status != AlertStatus.PENDING)
        .options(selectinload(Alert.senior))
        .order_by(Alert.created_at.desc())
    )
    if scope == "active":
        query = query.where(Alert.status == AlertStatus.ESCALATED)
    else:
        query = query.limit(100)

    result = await db.execute(query)
    return [_alert_out(alert) for alert in result.scalars().all()]


async def _responder_alert(sync_id: UUID, db: AsyncSession, responder: User) -> Alert:
    """Fetch one alert, confirming it belongs to this responder's barangay."""
    barangay = _assigned_barangay(responder)
    result = await db.execute(
        select(Alert).where(Alert.sync_id == sync_id).options(selectinload(Alert.senior))
    )
    alert = result.scalar_one_or_none()
    if alert is None or alert.senior is None or alert.senior.barangay != barangay:
        # One 404 for both "no such alert" and "not your barangay". Confirming that an
        # alert exists but belongs to a neighbouring barangay is itself a disclosure.
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Alert not found")
    return alert


def _responder_name(responder: User) -> str:
    # full_name is nullable -- barangay accounts are seeded without one -- so fall back to
    # the username, which is not. Better a login handle in the log than a blank space.
    return responder.full_name or responder.username


@router.patch("/alerts/{sync_id}/acknowledge", response_model=BarangayAlertOut)
async def acknowledge_incident(
    sync_id: UUID,
    payload: ResponderAction,
    db: AsyncSession = Depends(get_db),
    responder: User = Depends(responder_only),
) -> BarangayAlertOut:
    """"We have seen this and someone is going." Records who took it, without closing it.

    The status deliberately stays `escalated` rather than becoming something new. `status`
    is a native Postgres enum whose vocabulary cannot grow without an ALTER TYPE, and
    every reader of that column only needs to know the incident is open at the barangay
    tier. *Who picked it up* is an audit fact, and escalation_steps is where audit facts
    live (CLAUDE.md §8) -- so no migration is needed to say it.

    Keeping it in the active queue is also correct behaviour: an incident someone is
    driving to is still an open incident.
    """
    alert = await _responder_alert(sync_id, db, responder)
    if alert.status != AlertStatus.ESCALATED:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="This incident is not open at the barangay tier",
        )
    append_step(alert, "acknowledged_barangay", by=_responder_name(responder), notes=payload.notes)
    await db.commit()
    # Deliberately no db.refresh() here. SessionLocal is configured expire_on_commit=False,
    # so the values set above survive the commit in memory -- and refresh() would expire the
    # eagerly-loaded `senior` relationship, leaving _alert_out to trigger a lazy load that
    # raises MissingGreenlet on an async session.
    return _alert_out(alert)


@router.patch("/alerts/{sync_id}/resolve", response_model=BarangayAlertOut)
async def resolve_incident(
    sync_id: UUID,
    payload: ResponderAction,
    db: AsyncSession = Depends(get_db),
    responder: User = Depends(responder_only),
) -> BarangayAlertOut:
    """The welfare check happened and the incident is over."""
    alert = await _responder_alert(sync_id, db, responder)
    if alert.status in (AlertStatus.RESOLVED, AlertStatus.FALSE_POSITIVE):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Incident already closed")
    alert.status = AlertStatus.RESOLVED
    alert.resolved_at = await db_now(db)  # same clock as created_at -- see db_now()
    append_step(alert, "resolved_barangay", by=_responder_name(responder), notes=payload.notes)
    await db.commit()
    # Deliberately no db.refresh() here. SessionLocal is configured expire_on_commit=False,
    # so the values set above survive the commit in memory -- and refresh() would expire the
    # eagerly-loaded `senior` relationship, leaving _alert_out to trigger a lazy load that
    # raises MissingGreenlet on an async session.
    return _alert_out(alert)


@router.patch("/alerts/{sync_id}/false-positive", response_model=BarangayAlertOut)
async def mark_false_positive(
    sync_id: UUID,
    payload: ResponderAction,
    db: AsyncSession = Depends(get_db),
    responder: User = Depends(responder_only),
) -> BarangayAlertOut:
    """The senior was fine and the detection was wrong.

    Kept separate from resolve because these two answers mean opposite things about the
    detection engine. §10 targets a false-positive rate at or under 15%, and that number
    can only be measured if someone records which alerts were wrong.
    """
    alert = await _responder_alert(sync_id, db, responder)
    if alert.status in (AlertStatus.RESOLVED, AlertStatus.FALSE_POSITIVE):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Incident already closed")
    alert.status = AlertStatus.FALSE_POSITIVE
    alert.resolved_at = await db_now(db)  # same clock as created_at -- see db_now()
    append_step(alert, "false_positive_barangay", by=_responder_name(responder), notes=payload.notes)
    await db.commit()
    # Deliberately no db.refresh() here. SessionLocal is configured expire_on_commit=False,
    # so the values set above survive the commit in memory -- and refresh() would expire the
    # eagerly-loaded `senior` relationship, leaving _alert_out to trigger a lazy load that
    # raises MissingGreenlet on an async session.
    return _alert_out(alert)


@router.get("/seniors", response_model=list[BarangaySeniorOut])
async def list_barangay_seniors(
    db: AsyncSession = Depends(get_db),
    responder: User = Depends(responder_only),
) -> list[BarangaySeniorOut]:
    """The roster of seniors this barangay is responsible for."""
    barangay = _assigned_barangay(responder)

    seniors_result = await db.execute(
        select(Senior)
        .where(Senior.barangay == barangay)
        .order_by(Senior.last_name, Senior.first_name)
    )
    seniors = list(seniors_result.scalars().all())
    if not seniors:
        return []

    # One grouped count rather than a query per senior -- the roster is the screen most
    # likely to grow, and a per-row query is the classic way a list page gets slow.
    counts_result = await db.execute(
        select(Alert.senior_id, func.count(Alert.id))
        .where(
            Alert.senior_id.in_([senior.id for senior in seniors]),
            Alert.status == AlertStatus.ESCALATED,
        )
        .group_by(Alert.senior_id)
    )
    open_counts = dict(counts_result.all())

    return [
        BarangaySeniorOut(
            sync_id=senior.sync_id,
            first_name=senior.first_name,
            last_name=senior.last_name,
            age=senior.age,
            gender=senior.gender,
            address=senior.address,
            mobile_number=senior.mobile_number,
            last_seen_at=senior.last_seen_at,
            battery_percent=senior.battery_percent,
            is_charging=senior.is_charging,
            open_incidents=open_counts.get(senior.id, 0),
        )
        for senior in seniors
    ]


@router.get("/stats", response_model=BarangayStats)
async def barangay_stats(
    db: AsyncSession = Depends(get_db),
    responder: User = Depends(responder_only),
) -> BarangayStats:
    """Numbers for the analytics panel (CLAUDE.md §13, item 13)."""
    barangay = _assigned_barangay(responder)

    seniors_result = await db.execute(select(Senior.id).where(Senior.barangay == barangay))
    senior_ids = list(seniors_result.scalars().all())
    if not senior_ids:
        return BarangayStats(
            seniors_monitored=0, open_incidents=0, alerts_this_week=[], outcomes={}
        )

    # The database's clock again: this window is compared against created_at, which
    # Postgres wrote. Using Python's UTC here would slide the seven-day boundary by
    # whatever the database's zone offset happens to be.
    now = await db_now(db)
    week_start = (now - timedelta(days=6)).replace(hour=0, minute=0, second=0, microsecond=0)

    alerts_result = await db.execute(
        select(Alert.created_at, Alert.status).where(
            Alert.senior_id.in_(senior_ids),
            Alert.status != AlertStatus.PENDING,
            Alert.created_at >= week_start,
        )
    )
    rows = alerts_result.all()

    # Every one of the last seven days is filled in, including the empty ones. A bar chart
    # that silently omits quiet days makes a quiet week look like a busy one.
    per_day = Counter(created_at.date().isoformat() for created_at, _ in rows)
    days = [
        DayCount(
            day=(week_start + timedelta(days=offset)).date().isoformat(),
            count=per_day.get((week_start + timedelta(days=offset)).date().isoformat(), 0),
        )
        for offset in range(7)
    ]

    outcomes = Counter(alert_status.value for _, alert_status in rows)

    open_result = await db.execute(
        select(func.count(Alert.id)).where(
            Alert.senior_id.in_(senior_ids), Alert.status == AlertStatus.ESCALATED
        )
    )

    return BarangayStats(
        seniors_monitored=len(senior_ids),
        open_incidents=open_result.scalar_one(),
        alerts_this_week=days,
        outcomes=dict(outcomes),
    )
