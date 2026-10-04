import asyncio
import contextlib
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.escalation import escalation_sweep_loop
from app.api.routes import alerts, auth, barangay, contacts, devices, seniors
from app.core import push, sms
from app.core.config import settings

logging.basicConfig(level=logging.INFO)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Starts the escalation clock with the API and stops it cleanly on shutdown."""
    task = asyncio.create_task(escalation_sweep_loop())
    yield
    task.cancel()
    with contextlib.suppress(asyncio.CancelledError):
        await task


app = FastAPI(title="SEENior API", lifespan=lifespan)

# Browsers need the server to allow the origin; the barangay dashboard is the only browser client.
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router)
app.include_router(seniors.router)
app.include_router(contacts.router)
app.include_router(alerts.router)
app.include_router(devices.router)
app.include_router(barangay.router)


@app.get("/health")
async def health() -> dict:
    # Report whether push and SMS are configured, so a deployment missing credentials
    # doesn't look healthy.
    return {
        "status": "ok",
        "push_enabled": push.is_configured(),
        "sms_enabled": sms.is_configured(),
    }
