"""Turn SMS two-factor sign-in on or off for a barangay responder. Run by the OSCA officer.

Usage: python -m app.scripts.set_two_factor <username> on <phone>
       python -m app.scripts.set_two_factor <username> off

`on` also saves the number the code is texted to (Philippine mobile, e.g. 09171234567). It is
kept apart from the account's alert `phone`, so this number never receives alert texts.
`off` is the recovery path for a responder who has lost or changed their phone: turn it off,
then back on with the new number. Any code still pending for the account is discarded.
"""

import asyncio
import re
import sys

from sqlalchemy import delete, select

from app.db.models import LoginChallenge, User, UserRole
from app.db.session import SessionLocal

_PH_MOBILE = re.compile(r"^(09|\+639)\d{9}$")


async def set_two_factor(username: str, enabled: bool, phone: str | None) -> None:
    async with SessionLocal() as db:
        user = (await db.execute(select(User).where(User.username == username))).scalar_one_or_none()
        if user is None or user.role != UserRole.BARANGAY_RESPONDER:
            print(f"No barangay responder named '{username}'.")
            sys.exit(1)
        if enabled:
            user.two_factor_phone = phone
        user.two_factor_enabled = enabled
        if not enabled:
            user.two_factor_phone = None
        await db.execute(delete(LoginChallenge).where(LoginChallenge.user_id == user.id))
        await db.commit()
        state = f"ON, texting {phone}" if enabled else "OFF"
        print(f"Two-factor for '{username}' is now {state}.")


if __name__ == "__main__":
    args = sys.argv[1:]
    if len(args) == 3 and args[1] == "on" and _PH_MOBILE.match(args[2]):
        asyncio.run(set_two_factor(args[0], True, args[2]))
    elif len(args) == 2 and args[1] == "off":
        asyncio.run(set_two_factor(args[0], False, None))
    else:
        print(__doc__)
        sys.exit(1)
