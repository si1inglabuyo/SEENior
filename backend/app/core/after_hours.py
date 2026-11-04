"""What the apps show when the barangay hall is closed: its duty hours and who to call instead.

Served from the server (`GET /config/after-hours`) so a number or the hours can change with a
deploy instead of an app release. The apps cache the last copy and also bundle these same
defaults, so the notice still works with no signal (see AfterHoursConfig.kt on the phone, which
must carry the same list).

The numbers come from the barangay's posted hotline sheet. They have not been dialled or
confirmed with each office; confirm them with the barangay before a real pilot. The fire
bureau's number is printed as `(02) 833-02344` on that sheet.

`dial` is what the phone's dialler gets (digits only, with the 02 area code on landlines, which
a mobile phone needs); `display` is what the person reads.
"""

from dataclasses import dataclass


@dataclass(frozen=True)
class Number:
    display: str
    dial: str


@dataclass(frozen=True)
class Hotline:
    name: str
    numbers: tuple[Number, ...]


# Most general first. The order is the order the apps list them in.
HOTLINES: tuple[Hotline, ...] = (
    Hotline("National Emergency Hotline", (Number("911", "911"),)),
    Hotline("Q.C. Emergency Hotline", (Number("122", "122"),)),
    Hotline(
        "Brgy. UP Campus (Amorsolo)",
        (Number("0995-721-9867", "09957219867"), Number("8-426-9779", "0284269779")),
    ),
    Hotline(
        "Police Station 9",
        (Number("8-434-3687", "0284343687"), Number("0998-598-7958", "09985987958")),
    ),
    Hotline("QC Bureau of Fire Protection", (Number("(02) 833-02344", "0283302344"),)),
    Hotline(
        "QCDRRMO",
        (Number("0999-228-7362", "09992287362"), Number("0919-067-1170", "09190671170")),
    ),
)

# Hall duty hours, Manila time. Weekdays are Python's (Monday = 0 ... Sunday = 6). The dashboard
# shows the same hours (barangay-dashboard/src/dispatch.js); keep the two in step.
TIMEZONE = "Asia/Manila"
OPEN_HOUR = 8
CLOSE_HOUR = 17
OPEN_WEEKDAYS = (0, 1, 2, 3, 4)

# Bump when the list or the hours change, so a phone can tell its cached copy is old.
VERSION = 1
