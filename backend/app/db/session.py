from typing import AsyncGenerator

from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.orm import DeclarativeBase

from app.core.config import settings


class Base(DeclarativeBase):
    pass


# pool_pre_ping issues a cheap liveness check before handing out a pooled connection, and
# without it this deployment hands out dead ones routinely: Render's free tier spins the
# service down when idle and Supabase's pooler closes idle connections of its own accord, so
# a connection that was fine when it was pooled is frequently gone by the time the next
# request wants it. The symptom is a request failing once for no visible reason after a quiet
# spell -- which, on the endpoint that records an alert, is not a failure to shrug at.
#
# pool_recycle retires connections before the pooler's own idle timeout can, so the pre-ping
# is a backstop rather than the routine path.
engine = create_async_engine(
    settings.database_url,
    echo=False,
    pool_pre_ping=True,
    pool_recycle=300,
)
SessionLocal = async_sessionmaker(engine, expire_on_commit=False)


async def get_db() -> AsyncGenerator[AsyncSession, None]:
    async with SessionLocal() as session:
        yield session
