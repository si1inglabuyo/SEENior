"""SMS sign-in codes for accounts with two-factor on (barangay responders).

A 6-digit code is texted after a correct password and must be typed back within minutes.
Only an HMAC of the code is stored, keyed with the server secret and bound to the challenge
id, so a copy of the table does not reveal live codes and a hash cannot be replayed against
another challenge. The code is single-use, expires, and wrong guesses are counted per
challenge (see api/routes/auth.py), which is what keeps a one-in-a-million guess impractical.
"""

from __future__ import annotations

import hashlib
import hmac
import secrets

from app.core.config import settings

CODE_LENGTH = 6
CODE_TTL_SECONDS = 300          # a code is good for 5 minutes
MAX_WRONG_GUESSES = 5           # across every resend of one challenge
MAX_SENDS = 3                   # the first text plus two resends
RESEND_COOLDOWN_SECONDS = 30
CHALLENGE_MAX_AGE_SECONDS = 900  # a challenge can't be resent into after 15 minutes


def new_code() -> str:
    """A uniformly random 6-digit code, leading zeros kept."""
    return f"{secrets.randbelow(10 ** CODE_LENGTH):0{CODE_LENGTH}d}"


def hash_code(challenge_id: str, code: str) -> str:
    key = settings.secret_key.encode("utf-8")
    return hmac.new(key, f"{challenge_id}:{code}".encode("utf-8"), hashlib.sha256).hexdigest()


def code_matches(challenge_id: str, code: str, stored_hash: str) -> bool:
    return hmac.compare_digest(hash_code(challenge_id, code.strip()), stored_hash)


def mask_phone(phone: str) -> str:
    """'09171234567' -> '*******4567'. Shown on the code screen so the responder knows where
    the text went without the number being readable by someone who only knows the password."""
    digits = phone.strip()
    return "*" * max(0, len(digits) - 4) + digits[-4:]


def sms_message(code: str) -> str:
    minutes = CODE_TTL_SECONDS // 60
    return f"SEENior login code: {code}. Valid for {minutes} min. Do not share it with anyone."
