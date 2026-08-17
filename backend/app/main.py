from fastapi import FastAPI

from app.api.routes import alerts, auth, contacts, devices, seniors
from app.core import push

app = FastAPI(title="SEENior API")

app.include_router(auth.router)
app.include_router(seniors.router)
app.include_router(contacts.router)
app.include_router(alerts.router)
app.include_router(devices.router)


@app.get("/health")
async def health() -> dict:
    # push_enabled is reported because a deployment with no FCM credentials looks
    # identical to a healthy one from the outside — right up until an emergency fails
    # to reach anybody. This makes that state visible without reading the logs.
    return {"status": "ok", "push_enabled": push.is_configured()}
