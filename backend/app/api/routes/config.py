"""Public settings the apps read: who to call when the barangay hall is closed.

No login and no personal data, so a phone that has not registered yet, or a family app that has
not signed in, can still fetch it. It is the same list for everybody.
"""

from fastapi import APIRouter

from app.core import after_hours
from app.schemas.after_hours import AfterHoursOut, HallHoursOut, HotlineOut, NumberOut

router = APIRouter(prefix="/config", tags=["config"])


@router.get("/after-hours", response_model=AfterHoursOut)
async def get_after_hours() -> AfterHoursOut:
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
    )
