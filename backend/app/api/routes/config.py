"""Public settings the apps read: who to call when the barangay hall is closed.

The GET needs no login and carries no personal data, so a phone that has not registered yet, or a
family app that has not signed in, can still fetch it. The "Hall closed" days are set by a signed-in
barangay responder for their own barangay; they change what the apps say, never escalation.
"""

from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import require_role
from app.core import after_hours
from app.db.models import HallClosedDay, User, UserRole
from app.db.session import get_db
from app.schemas.after_hours import AfterHoursOut, ClosedDaysOut, HallHoursOut, HotlineOut, NumberOut

router = APIRouter(prefix="/config", tags=["config"])

# A closure can be marked ahead (an announced holiday) but not indefinitely.
MAX_DAYS_AHEAD = 60


def manila_today() -> date:
    return datetime.now(ZoneInfo(after_hours.TIMEZONE)).date()


async def _closed_days(db: AsyncSession, barangay: str | None) -> list[str]:
    query = select(HallClosedDay.day).where(HallClosedDay.day >= manila_today()).distinct()
    if barangay:
        query = query.where(HallClosedDay.barangay == barangay)
    rows = await db.execute(query.order_by(HallClosedDay.day))
    return [d.isoformat() for d in rows.scalars()]


async def _safe_closed_days(db: AsyncSession, barangay: str | None) -> list[str]:
    # The hotlines are the part that matters at night; a database hiccup must not take them away.
    try:
        return await _closed_days(db, barangay)
    except Exception:
        return []


@router.get("/after-hours", response_model=AfterHoursOut)
async def get_after_hours(
    barangay: str | None = Query(default=None, max_length=128),
    db: AsyncSession = Depends(get_db),
) -> AfterHoursOut:
    # With no barangay named (today's apps), every barangay's days apply. That is correct for the
    # one-barangay pilot; send ?barangay= once there are several halls.
    return AfterHoursOut(
        version=after_hours.VERSION,
        hall=HallHoursOut(
            timezone=after_hours.TIMEZONE,
            open_hour=after_hours.OPEN_HOUR,
            close_hour=after_hours.CLOSE_HOUR,
            open_weekdays=list(after_hours.OPEN_WEEKDAYS),
        ),
        hotlines=[
            HotlineOut(
                name=h.name,
                numbers=[NumberOut(display=n.display, dial=n.dial) for n in h.numbers],
            )
            for h in after_hours.HOTLINES
        ],
        closed_days=await _safe_closed_days(db, barangay),
    )


def _own_barangay(user: User) -> str:
    if not user.barangay:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "This account has no barangay assigned.")
    return user.barangay


@router.get("/hall-closed-days", response_model=ClosedDaysOut)
async def list_closed_days(
    user: User = Depends(require_role(UserRole.BARANGAY_RESPONDER)),
    db: AsyncSession = Depends(get_db),
) -> ClosedDaysOut:
    return ClosedDaysOut(closed_days=await _closed_days(db, _own_barangay(user)))


@router.put("/hall-closed-days/{day}", response_model=ClosedDaysOut)
async def close_hall_on(
    day: date,
    user: User = Depends(require_role(UserRole.BARANGAY_RESPONDER)),
    db: AsyncSession = Depends(get_db),
) -> ClosedDaysOut:
    barangay = _own_barangay(user)
    today = manila_today()
    if day < today or day > today + timedelta(days=MAX_DAYS_AHEAD):
        raise HTTPException(
            status.HTTP_422_UNPROCESSABLE_ENTITY,
            f"Pick today or a day within the next {MAX_DAYS_AHEAD} days.",
        )
    exists = await db.execute(
        select(HallClosedDay.id).where(HallClosedDay.barangay == barangay, HallClosedDay.day == day)
    )
    if exists.first() is None:  # idempotent: marking a closed day again is not an error
        db.add(HallClosedDay(barangay=barangay, day=day, set_by_user_id=user.id))
        await db.commit()
    return ClosedDaysOut(closed_days=await _closed_days(db, barangay))


@router.delete("/hall-closed-days/{day}", response_model=ClosedDaysOut)
async def reopen_hall_on(
    day: date,
    user: User = Depends(require_role(UserRole.BARANGAY_RESPONDER)),
    db: AsyncSession = Depends(get_db),
) -> ClosedDaysOut:
    barangay = _own_barangay(user)
    await db.execute(
        delete(HallClosedDay).where(HallClosedDay.barangay == barangay, HallClosedDay.day == day)
    )
    await db.commit()
    return ClosedDaysOut(closed_days=await _closed_days(db, barangay))
