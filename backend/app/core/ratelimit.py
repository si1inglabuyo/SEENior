"""A small in-process rate limiter, for the handful of endpoints that need one.

Why not slowapi or redis
------------------------
SEENior runs as a single Render web service against one database. A dependency and a
second piece of infrastructure to hold counters that fit in a dict would be paid for on
every deploy, forever, to solve a problem this file solves in eighty lines.

**The limits below are therefore per process, and they reset when the service restarts.**
On Render's free tier that happens on every deploy and after every idle spin-down, so a
patient attacker gets a fresh allowance each time. That is an accepted trade: these limits
exist to make online guessing impractical at human timescales, not to be an authorization
boundary. If SEENior ever runs more than one instance, this has to move to shared storage
or the per-IP counts silently multiply by the instance count.

Two kinds of limit, and both are needed
---------------------------------------
`per-IP` stops one host hammering an endpoint. On its own it is not enough for a secret as
small as a six-digit invite code, because an attacker with a pool of addresses simply
spreads the guesses out.

`global` caps the endpoint's total throughput no matter who is calling, which is what
actually bounds a brute-force search of a small keyspace. It is deliberately set far above
real usage: a barangay's worth of families pairing at once must never trip it.
"""

from __future__ import annotations

import asyncio
import logging
import time
from collections import defaultdict, deque

from fastapi import HTTPException, Request, status

logger = logging.getLogger(__name__)

# bucket name -> key -> timestamps of recent hits, oldest first
_hits: dict[str, dict[str, deque[float]]] = defaultdict(lambda: defaultdict(deque))
_lock = asyncio.Lock()

# Keys with no recent hits are dropped on this cadence so a long-running process cannot
# accumulate one deque per address it has ever seen.
_PRUNE_EVERY_SECONDS = 300.0
_last_prune = 0.0

# Sentinel key for a limit that counts every caller together.
GLOBAL = "*"


def client_ip(request: Request) -> str:
    """The caller's address as well as it can be known from behind Render's proxy.

    `request.client.host` is the *proxy* in a deployed environment, which would file every
    request in the world under one key and throttle all users the moment one misbehaved --
    so the forwarded header has to win where it is present.

    The leftmost entry is the convention for a single trusted proxy, which is what Render
    is. It is also client-supplied and therefore spoofable, and nothing here pretends
    otherwise: that is precisely why the endpoints that guard a guessable secret also carry
    a `GLOBAL` limit, which no amount of header rotation can slip past.
    """
    forwarded = request.headers.get("x-forwarded-for")
    if forwarded:
        first = forwarded.split(",")[0].strip()
        if first:
            return first
    return request.client.host if request.client else "unknown"


async def _prune(now: float) -> None:
    """Drop empty and fully-expired deques. Caller must hold the lock."""
    global _last_prune
    if now - _last_prune < _PRUNE_EVERY_SECONDS:
        return
    _last_prune = now
    for bucket, keys in list(_hits.items()):
        for key, stamps in list(keys.items()):
            # A key whose newest hit is older than an hour cannot be inside any window
            # this module uses.
            if not stamps or now - stamps[-1] > 3600:
                del keys[key]
        if not keys:
            del _hits[bucket]


async def check(bucket: str, key: str, limit: int, window_seconds: float) -> None:
    """Record one hit against `bucket`/`key`, or raise 429 if that exceeds `limit`.

    Sliding window rather than a fixed one: a fixed window lets an attacker fire `limit`
    requests at the end of a window and `limit` more at the start of the next, doubling the
    real rate at exactly the moment it matters.
    """
    now = time.monotonic()
    async with _lock:
        await _prune(now)
        stamps = _hits[bucket][key]
        cutoff = now - window_seconds
        while stamps and stamps[0] < cutoff:
            stamps.popleft()

        if len(stamps) >= limit:
            retry_after = max(1, int(window_seconds - (now - stamps[0])) + 1)
            # Logged at warning because a tripped limit on these endpoints is either an
            # attack or a bug in a client, and both are worth seeing.
            logger.warning("Rate limit hit: bucket=%s key=%s limit=%d/%ds", bucket, key, limit, int(window_seconds))
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail="Too many attempts. Please wait a moment and try again.",
                headers={"Retry-After": str(retry_after)},
            )

        stamps.append(now)


def reset() -> None:
    """Clear all counters. For tests only."""
    _hits.clear()
    global _last_prune
    _last_prune = 0.0
