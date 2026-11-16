"""A second, read-only copy of the API must be able to run without its own escalation clock."""

import asyncio

from app import main
from app.core.config import settings


def _run_lifespan(monkeypatch, enabled):
    started = []

    async def fake_loop():
        started.append(True)
        await asyncio.sleep(3600)

    monkeypatch.setattr(main, "escalation_sweep_loop", fake_loop)
    monkeypatch.setattr(settings, "escalation_sweep_enabled", enabled)

    async def go():
        async with main.lifespan(main.app):
            await asyncio.sleep(0)

    asyncio.run(go())
    return started


def test_the_sweep_runs_by_default(monkeypatch):
    assert settings.escalation_sweep_enabled is True
    assert _run_lifespan(monkeypatch, True) == [True]


def test_the_sweep_can_be_switched_off_for_a_read_only_copy(monkeypatch):
    assert _run_lifespan(monkeypatch, False) == []
