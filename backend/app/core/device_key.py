"""The per-device key that proves a request comes from a senior's own phone.

A senior has no login, and their `sync_id` is also shown to linked family members and
barangay responders, so it can't be a credential. Instead the phone gets a random key when it
registers, the server stores only its hash, and the phone sends the key in `X-Device-Key`.

The key is 256 bits of randomness, so a plain SHA-256 is enough to store it (there is nothing
to brute-force); a slow password hash would only add load to every request.
"""

import hashlib
import hmac
import secrets

HEADER_NAME = "X-Device-Key"


def generate() -> str:
    """A new key to hand to the phone once."""
    return secrets.token_urlsafe(32)


def hash_key(key: str) -> str:
    return hashlib.sha256(key.encode("utf-8")).hexdigest()


def matches(presented: str, stored_hash: str) -> bool:
    """Constant-time comparison, so response timing reveals nothing about the stored hash."""
    return hmac.compare_digest(hash_key(presented), stored_hash)
