from fastapi import FastAPI

from app.api.routes import auth

app = FastAPI(title="SEENior API")

app.include_router(auth.router)


@app.get("/health")
async def health() -> dict:
    return {"status": "ok"}
