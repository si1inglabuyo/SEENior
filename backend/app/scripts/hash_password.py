"""Prints a bcrypt hash for a new password, to paste into an UPDATE. Nothing is sent anywhere.

Usage (from the backend folder):
    venv\\Scripts\\python.exe -m app.scripts.hash_password

It asks for the password twice without echoing it, so it never lands in shell history, a chat or
a screenshot. Then run the printed UPDATE in the Supabase SQL Editor. Only the hash goes in the
database; the password stays with you.
"""

import getpass
import os
import sys

# Hashing needs nothing from the server's configuration, but importing the security module loads
# it, and it refuses to start on the public dev signing key. This script never signs anything.
os.environ.setdefault("SEENIOR_ALLOW_DEV_SECRET", "1")

from app.core.security import hash_password  # noqa: E402

MIN_LENGTH = 12
# bcrypt silently ignores everything after 72 bytes, so a longer password would be weaker than it looks.
MAX_BYTES = 72
COMMON = {
    "password", "password1", "password123", "password1234", "123456789012", "qwertyuiop12",
    "admin123456", "seenior12345", "barangay1234",
}


def check_new_password(password: str) -> str | None:
    """Why this password is not acceptable, or None if it is."""
    if len(password) < MIN_LENGTH:
        return f"Use at least {MIN_LENGTH} characters."
    if len(password.encode("utf-8")) > MAX_BYTES:
        return f"Use at most {MAX_BYTES} bytes (bcrypt ignores the rest)."
    if password.lower() in COMMON or password.isdigit() or password.isalpha():
        return "Too easy to guess: mix letters, digits and symbols, and avoid common words."
    return None


def main() -> int:
    password = getpass.getpass("New password: ")
    if password != getpass.getpass("Again: "):
        print("The two entries differ.")
        return 1
    problem = check_new_password(password)
    if problem:
        print(problem)
        return 1
    print("\nRun this in the Supabase SQL Editor (the hash is safe to paste):\n")
    print(
        "UPDATE users SET password_hash = '"
        + hash_password(password)
        + "' WHERE username = 'BARANGAY U.P. CAMPUS' AND role = 'barangay_responder';"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
