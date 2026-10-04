"""Routes for the barangay responder's dashboard.

Every route requires a barangay_responder JWT, and the senior must be in that responder's
own barangay.
"""

import logging
from collections import Counter
from datetime import date, datetime, time, timedelta
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.api.deps import require_role
from app.api.escalation import append_step, db_now, has_family_tier, sweep_overdue_alerts
from app.db.models import (
    Alert,
    AlertStatus,
    Contact,
    ContactType,
    Senior,
    TriggerType,
    User,
    UserRole,
)
from app.db.session import get_db
from app.schemas.barangay import (
    BarangayAlertOut,
    BarangayContactOut,
    BarangaySeniorDetail,
    BarangaySeniorOut,
    BarangayStats,
    DayCount,
    ResponderAction,
)

# Server timestamps are UTC but responders read them in Philippine time. Calendar-day
# boundaries (today, the weekly buckets, a clicked day) are computed in Manila time and
# shifted back to UTC only when compared against stored timestamps.
PH_OFFSET = timedelta(hours=8)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/barangay", tags=["barangay"])

# Built once at import time; FastAPI calls the inner check per request.
responder_only = require_role(UserRole.BARANGAY_RESPONDER)


def _assigned_barangay(responder: User) -> str:
    """The responder's barangay. Refuses to continue if they have none, so a missing
    barangay can never match some other senior."""
    if not responder.barangay:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="This responder account has no barangay assigned. Contact the OSCA officer.",
        )
    return responder.barangay


def _display_gender(gender: str | None) -> str | None:
    """Gender for display, or None if the senior never gave one."""
    if not gender or gender.strip().lower() in {"unknown", "unspecified", "n/a"}:
        return None
    return gender.strip().capitalize()


def _alert_category(trigger_type: TriggerType, escalation_steps: list | None) -> str:
    """The category the dashboard groups alerts by. Mirrors alertCategory() in labels.js."""
    if trigger_type == TriggerType.SOS:
        return "sos"
    if trigger_type == TriggerType.FALL_PATTERN:
        return "potential_fall"
    for step in escalation_steps or []:
        if isinstance(step, dict) and step.get("step") == "escalated_barangay":
            return "dispatch_family"
    return "anomaly"


def _is_attending(status: AlertStatus, escalation_steps: list | None) -> bool:
    """An escalated alert a responder has already acknowledged, read off the timeline.
    Mirrors isAttending() in labels.js."""
    return status == AlertStatus.ESCALATED and any(
        isinstance(step, dict) and step.get("step") == "acknowledged_barangay"
        for step in escalation_steps or []
    )


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
        location_cluster_id=alert.location_cluster_id,
        senior_sync_id=senior.sync_id,
        senior_name=f"{senior.first_name} {senior.last_name}",
        senior_age=senior.age,
        senior_gender=_display_gender(senior.gender),
        senior_address=senior.address,
        senior_mobile=senior.mobile_number,
        senior_has_family_contact=has_family_tier(senior),
    )


@router.get("/alerts", response_model=list[BarangayAlertOut])
async def list_barangay_alerts(
    scope: str = Query("active", pattern="^(active|history|today|all)$"),
    q: str | None = Query(None, max_length=100),
    date_from: date | None = Query(None),
    date_to: date | None = Query(None),
    full: bool = Query(False),
    db: AsyncSession = Depends(get_db),
    responder: User = Depends(responder_only),
) -> list[BarangayAlertOut]:
    """The incident queue (`active`), the incident log (`history`), today's feed (`today`),
    and every non-pending alert (`all`).

    `history` is closed incidents only, floored at the last 30 days unless `full=true` or
    `date_from` is given (RA 10173 data minimisation). `q`, `date_from` and `date_to` filter
    in SQL. All scopes exclude `pending`, since those alerts haven't reached the barangay yet.
    """
    # Same sweep as the background loop, so a cold-started service shows incidents at once.
    await sweep_overdue_alerts(db)

    barangay = _assigned_barangay(responder)

    query = (
        select(Alert)
        .join(Senior, Senior.id == Alert.senior_id)
        .where(
            Senior.barangay == barangay,
            Senior.deleted_at.is_(None),  # deleted their own account (spec §11a)
            Alert.status != AlertStatus.PENDING,
        )
        .options(selectinload(Alert.senior).selectinload(Senior.contacts))
        .order_by(Alert.created_at.desc())
    )
    if scope == "active":
        query = query.where(Alert.status == AlertStatus.ESCALATED)
    elif scope == "today":
        # Midnight is Manila midnight (PH_OFFSET), reckoned on the database's clock.
        now = await db_now(db)
        start_of_today = (now + PH_OFFSET).replace(
            hour=0, minute=0, second=0, microsecond=0
        ) - PH_OFFSET
        query = query.where(Alert.created_at >= start_of_today).limit(50)
    elif scope == "all":
        query = query.limit(500)
    else:  # history
        query = query.where(
            Alert.status.in_((AlertStatus.RESOLVED, AlertStatus.FALSE_POSITIVE))
        ).limit(500)
        # Default log view is the last 30 days; `full=true` or an explicit date_from lifts it.
        if not full and date_from is None:
            now = await db_now(db)
            query = query.where(Alert.created_at >= now - timedelta(days=30))

    if q:
        needle = f"%{q.strip()}%"
        query = query.where(
            or_(
                Senior.first_name.ilike(needle),
                Senior.last_name.ilike(needle),
                (Senior.first_name + " " + Senior.last_name).ilike(needle),
            )
        )
    # date_from / date_to are Manila dates; shift by PH_OFFSET to compare with UTC created_at.
    if date_from is not None:
        query = query.where(
            Alert.created_at >= datetime.combine(date_from, time.min) - PH_OFFSET
        )
    if date_to is not None:
        query = query.where(
            Alert.created_at
            < datetime.combine(date_to, time.min) + timedelta(days=1) - PH_OFFSET
        )

    result = await db.execute(query)
    return [_alert_out(alert) for alert in result.scalars().all()]


async def _responder_alert(sync_id: UUID, db: AsyncSession, responder: User) -> Alert:
    """Fetch one alert, confirming it belongs to this responder's barangay."""
    barangay = _assigned_barangay(responder)
    result = await db.execute(
        select(Alert)
        .where(Alert.sync_id == sync_id)
        .options(selectinload(Alert.senior).selectinload(Senior.contacts))
    )
    alert = result.scalar_one_or_none()
    if (
        alert is None
        or alert.senior is None
        or alert.senior.barangay != barangay
        or alert.senior.deleted_at is not None  # deleted their own account (spec §11a)
    ):
        # Same 404 for "no such alert" and "not your barangay", so existence isn't revealed.
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Alert not found")
    return alert


def _responder_name(responder: User) -> str:
    # full_name can be null for barangay accounts, so fall back to the username.
    return responder.full_name or responder.username


@router.patch("/alerts/{sync_id}/acknowledge", response_model=BarangayAlertOut)
async def acknowledge_incident(
    sync_id: UUID,
    payload: ResponderAction,
    db: AsyncSession = Depends(get_db),
    responder: User = Depends(responder_only),
) -> BarangayAlertOut:
    """Records who is attending an escalated alert, without closing it.

    The status stays `escalated` (the enum can't grow without a migration); who picked it
    up is recorded in escalation_steps.
    """
    alert = await _responder_alert(sync_id, db, responder)
    if alert.status != AlertStatus.ESCALATED:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="This incident is not open at the barangay tier",
        )
    # Once someone is attending, a second acknowledge would only duplicate the step.
    if _is_attending(alert.status, alert.escalation_steps):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="A responder is already attending this incident",
        )
    append_step(alert, "acknowledged_barangay", by=_responder_name(responder), notes=payload.notes)
    await db.commit()
    # No db.refresh() here: it would expire the loaded `senior` and make _alert_out lazy-load,
    # which fails on an async session.
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
    # No db.refresh() here: it would expire the loaded `senior` and make _alert_out lazy-load,
    # which fails on an async session.
    return _alert_out(alert)


@router.patch("/alerts/{sync_id}/false-positive", response_model=BarangayAlertOut)
async def mark_false_positive(
    sync_id: UUID,
    payload: ResponderAction,
    db: AsyncSession = Depends(get_db),
    responder: User = Depends(responder_only),
) -> BarangayAlertOut:
    """The senior was fine and the detection was wrong.

    Separate from resolve so the false-positive rate can be measured.
    """
    alert = await _responder_alert(sync_id, db, responder)
    if alert.status in (AlertStatus.RESOLVED, AlertStatus.FALSE_POSITIVE):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Incident already closed")
    alert.status = AlertStatus.FALSE_POSITIVE
    alert.resolved_at = await db_now(db)  # same clock as created_at -- see db_now()
    append_step(alert, "false_positive_barangay", by=_responder_name(responder), notes=payload.notes)
    await db.commit()
    # No db.refresh() here: it would expire the loaded `senior` and make _alert_out lazy-load,
    # which fails on an async session.
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
        .where(Senior.barangay == barangay, Senior.deleted_at.is_(None))
        .order_by(Senior.last_name, Senior.first_name)
    )
    seniors = list(seniors_result.scalars().all())
    if not seniors:
        return []

    # One grouped count instead of a query per senior.
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
            status=senior.status,
            last_seen_at=senior.last_seen_at,
            battery_percent=senior.battery_percent,
            is_charging=senior.is_charging,
            open_incidents=open_counts.get(senior.id, 0),
        )
        for senior in seniors
    ]


@router.get("/seniors/{sync_id}", response_model=BarangaySeniorDetail)
async def barangay_senior_detail(
    sync_id: UUID,
    db: AsyncSession = Depends(get_db),
    responder: User = Depends(responder_only),
) -> BarangaySeniorDetail:
    """One senior's full record: profile, family contacts and alert history."""
    barangay = _assigned_barangay(responder)

    result = await db.execute(
        select(Senior)
        .where(Senior.sync_id == sync_id)
        .options(
            selectinload(Senior.contacts).selectinload(Contact.user),
            selectinload(Senior.alerts),
        )
    )
    senior = result.scalar_one_or_none()
    if senior is None or senior.barangay != barangay or senior.deleted_at is not None:
        # Same 404 for "no such senior" and "not your barangay".
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Senior not found")

    family = [
        contact
        for contact in senior.contacts
        if contact.contact_type == ContactType.FAMILY and contact.unlinked_at is None
    ]
    contacts_out = [
        BarangayContactOut(
            name=contact.user.full_name or contact.user.username,
            relationship_label=contact.relationship_label,
            phone=contact.user.phone,
            email=contact.user.email,
        )
        for contact in family
    ]

    # Same "responders never see pending" rule as the alert queue, and newest first.
    alerts = sorted(senior.alerts, key=lambda a: a.created_at, reverse=True)
    for alert in alerts:
        alert.senior = senior  # selectinload(Senior.alerts) doesn't backfill alert.senior
    alerts_out = [_alert_out(a) for a in alerts if a.status != AlertStatus.PENDING]

    return BarangaySeniorDetail(
        sync_id=senior.sync_id,
        first_name=senior.first_name,
        last_name=senior.last_name,
        age=senior.age,
        gender=senior.gender,
        address=senior.address,
        mobile_number=senior.mobile_number,
        status=senior.status,
        living_arrangement="With Family" if family else "Lives alone",
        has_family_contact=bool(family),
        last_seen_at=senior.last_seen_at,
        battery_percent=senior.battery_percent,
        is_charging=senior.is_charging,
        contacts=contacts_out,
        alerts=alerts_out,
    )


@router.get("/stats", response_model=BarangayStats)
async def barangay_stats(
    db: AsyncSession = Depends(get_db),
    responder: User = Depends(responder_only),
) -> BarangayStats:
    """Numbers for the analytics panel (spec §13, item 13)."""
    barangay = _assigned_barangay(responder)

    seniors_result = await db.execute(
        select(Senior.id).where(Senior.barangay == barangay, Senior.deleted_at.is_(None))
    )
    senior_ids = list(seniors_result.scalars().all())
    if not senior_ids:
        return BarangayStats(
            seniors_monitored=0, open_incidents=0, alerts_this_week=[], outcomes={}
        )

    # Query bound on the database's clock; calendar-day figures are in Manila time.
    now_utc = await db_now(db)
    now_local = now_utc + PH_OFFSET
    today = now_local.date()
    yesterday = today - timedelta(days=1)
    week_start_local = (now_local - timedelta(days=6)).replace(
        hour=0, minute=0, second=0, microsecond=0
    )
    week_start_utc = week_start_local - PH_OFFSET
    month_start_local = now_local.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
    month_start_utc = month_start_local - PH_OFFSET

    alerts_result = await db.execute(
        select(
            Alert.created_at,
            Alert.status,
            Alert.trigger_type,
            Alert.resolved_at,
            Alert.escalation_steps,
        ).where(
            Alert.senior_id.in_(senior_ids),
            Alert.status != AlertStatus.PENDING,
            Alert.created_at >= week_start_utc,
        )
    )
    rows = alerts_result.all()

    # Fill in all seven days, including empty ones, bucketed by Manila calendar day.
    per_day = Counter((row.created_at + PH_OFFSET).date().isoformat() for row in rows)
    days = [
        DayCount(
            day=(week_start_local + timedelta(days=offset)).date().isoformat(),
            count=per_day.get(
                (week_start_local + timedelta(days=offset)).date().isoformat(), 0
            ),
        )
        for offset in range(7)
    ]

    # `attending` is split out of `escalated` so the donut can show claimed incidents.
    outcomes = Counter(
        "attending"
        if _is_attending(row.status, row.escalation_steps)
        else row.status.value
        for row in rows
    )
    alert_categories = Counter(
        _alert_category(row.trigger_type, row.escalation_steps) for row in rows
    )

    # Stat-card figures come from the same week window, so no extra query is needed.
    resolved_today = sum(
        1
        for row in rows
        if row.status == AlertStatus.RESOLVED
        and row.resolved_at is not None
        and (row.resolved_at + PH_OFFSET).date() == today
    )
    sos_today_times = sorted(
        row.created_at
        for row in rows
        if row.trigger_type == TriggerType.SOS and (row.created_at + PH_OFFSET).date() == today
    )
    alerts_today_total = sum(1 for row in rows if (row.created_at + PH_OFFSET).date() == today)
    alerts_yesterday_total = sum(
        1 for row in rows if (row.created_at + PH_OFFSET).date() == yesterday
    )

    open_result = await db.execute(
        select(func.count(Alert.id)).where(
            Alert.senior_id.in_(senior_ids), Alert.status == AlertStatus.ESCALATED
        )
    )
    seniors_month_result = await db.execute(
        select(func.count(Senior.id)).where(
            Senior.barangay == barangay,
            Senior.deleted_at.is_(None),
            Senior.created_at >= month_start_utc,
        )
    )

    return BarangayStats(
        seniors_monitored=len(senior_ids),
        open_incidents=open_result.scalar_one(),
        alerts_this_week=days,
        outcomes=dict(outcomes),
        alert_categories=dict(alert_categories),
        resolved_today=resolved_today,
        sos_today=len(sos_today_times),
        sos_last_at=sos_today_times[-1] if sos_today_times else None,
        seniors_added_this_month=seniors_month_result.scalar_one(),
        alerts_today_total=alerts_today_total,
        alerts_yesterday_total=alerts_yesterday_total,
    )
