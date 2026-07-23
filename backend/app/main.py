from fastapi import FastAPI

from app.api.routes import alerts, auth, contacts, seniors

app = FastAPI(title="SEENior API")

app.include_router(auth.router)
app.include_router(seniors.router)
app.include_router(contacts.router)
app.include_router(alerts.router)


@app.get("/health")
async def health() -> dict:
    return {"status": "ok"}
