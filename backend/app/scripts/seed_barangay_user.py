"""Create a barangay_responder login. Run by the OSCA officer, not exposed via API.

Usage: python -m app.scripts.seed_barangay_user <username> <password> <barangay>
"""

import asyncio
import sys

from app.core.security import hash_password
from app.db.models import User, UserRole
from app.db.session import SessionLocal


async def create_barangay_user(username: str, password: str, barangay: str) -> None:
    async with SessionLocal() as db:
        user = User(
            username=username,
            password_hash=hash_password(password),
            role=UserRole.BARANGAY_RESPONDER,
            barangay=barangay,
        )
        db.add(user)
        await db.commit()
        print(f"Created barangay_responder '{username}' for barangay '{barangay}'")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        print(__doc__)
        sys.exit(1)
    asyncio.run(create_barangay_user(sys.argv[1], sys.argv[2], sys.argv[3]))
