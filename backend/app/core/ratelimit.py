"""A small in-process rate limiter for the few endpoints that need one.

Limits are per process and reset on restart, which is acceptable: they make online
guessing impractical, they aren't an authorization boundary. With more than one instance
this would need shared storage.

Two kinds of limit: `per-IP` stops one host hammering an endpoint, and `global` caps total
throughput so a brute-force search of a small keyspace (like the six-digit invite code)
can't be spread across many addresses. The global limit is set well above real usage.
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

# Idle keys are dropped on this cadence so memory doesn't grow.
_PRUNE_EVERY_SECONDS = 300.0
_last_prune = 0.0

# Sentinel key for a limit that counts every caller together.
GLOBAL = "*"


def client_ip(request: Request) -> str:
    """The caller's address, preferring the forwarded header behind Render's proxy.

    The header is client-supplied and spoofable, which is why endpoints guarding a
    guessable secret also have a global limit.
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
            # Newer than an hour cannot be outside any window used here.
            if not stamps or now - stamps[-1] > 3600:
                del keys[key]
        if not keys:
            del _hits[bucket]


async def check(bucket: str, key: str, limit: int, window_seconds: float) -> None:
    """Record one hit against `bucket`/`key`, or raise 429 if it exceeds `limit`.

    Sliding window, so an attacker can't double the rate across a window boundary.
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
            # Warning level: a tripped limit is either an attack or a client bug.
            logger.warning("Rate limit hit: bucket=%s key=%s limit=%d/%ds", bucket, key, limit, int(window_seconds))
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail="Too many attempts. Please wait a moment and try again.",
                headers={"Retry-After": str(retry_after)},
            )

        stamps.append(now)


def per_ip(bucket: str, limit: int, window_seconds: float):
    """A route dependency that limits each client address to `limit` calls per window.

    Use it as `dependencies=[Depends(ratelimit.per_ip("register", 10, 3600))]`.
    """

    async def dependency(request: Request) -> None:
        await check(bucket, client_ip(request), limit=limit, window_seconds=window_seconds)

    return dependency


def reset() -> None:
    """Clear all counters. For tests only."""
    _hits.clear()
    global _last_prune
    _last_prune = 0.0
