import asyncio
import contextlib
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.escalation import escalation_sweep_loop
from app.api.routes import alerts, auth, barangay, contacts, devices, seniors
from app.core import push
from app.core.config import settings

logging.basicConfig(level=logging.INFO)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Starts the escalation clock with the API and stops it cleanly on shutdown.

    Without this, the third tier of a system whose title promises progressive escalation
    would only ever be reached by a family member pressing a button by hand.
    """
    task = asyncio.create_task(escalation_sweep_loop())
    yield
    task.cancel()
    with contextlib.suppress(asyncio.CancelledError):
        await task


app = FastAPI(title="SEENior API", lifespan=lifespan)

# Browsers refuse to send a cross-origin request unless the server says the origin is
# welcome. The Android apps are not browsers, which is why this was never needed until the
# barangay dashboard -- the first part of SEENior that runs in one.
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
    # push_enabled is reported because a deployment with no FCM credentials looks
    # identical to a healthy one from the outside — right up until an emergency fails
    # to reach anybody. This makes that state visible without reading the logs.
    return {"status": "ok", "push_enabled": push.is_configured()}
