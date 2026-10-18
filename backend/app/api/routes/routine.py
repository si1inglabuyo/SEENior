"""The Routine Fingerprint in the cloud: written by the senior's phone, read by family and barangay.

What syncs is the fingerprint only (a median and a MAD per feature per time block, 20 rows at
most). It is a statistical summary, not a sensor reading, so spec section 11 holds: raw
`Sensor_Data` and the per-day `Daily_Aggregates` never leave the phone.

Read access follows the same rules as everything else a senior owns: a family contact needs an
active pairing, a barangay responder needs the senior to be in their barangay. Sharing it with the
barangay rests on RA 10173 section 12(c) like the rest of what they see, and is shown on the
dashboard with the same access logging as the other details.
"""

from datetime import datetime, timezone
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import func, select
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_authenticated_senior, get_current_user
from app.api.routes.barangay import _assigned_barangay, _in_barangay
from app.db.models import Contact, ContactType, RoutineBaseline, Senior, User, UserRole
from app.db.session import get_db
from app.schemas.routine import RoutineOut, RoutineRow, RoutineSync

router = APIRouter(prefix="/seniors", tags=["routine"])


@router.put("/{sync_id}/routine", response_model=RoutineOut)
async def sync_routine(
    payload: RoutineSync,
    senior: Senior = Depends(get_authenticated_senior),
    db: AsyncSession = Depends(get_db),
) -> RoutineOut:
    """Overwrites this senior's fingerprint with what the phone just computed.

    Upserts per (feature, time block); cells the phone doesn't send are left as they were, so a
    block the baseline updater skipped keeps its last good value instead of vanishing.
    """
    if senior.deleted_at is not None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Senior not found")

    # A repeated cell would make Postgres refuse the whole statement; the last one wins.
    cells = {(r.feature_name, r.time_block): r for r in payload.rows}
    if cells:
        now = datetime.now(timezone.utc).replace(tzinfo=None)
        stmt = pg_insert(RoutineBaseline).values(
            [
                dict(
                    senior_id=senior.id,
                    feature_name=r.feature_name,
                    time_block=r.time_block,
                    median_value=r.median_value,
                    mad_value=r.mad_value,
                    sample_count=r.sample_count,
                    is_seed=r.is_seed,
                    updated_at=now,
                )
                for r in cells.values()
            ]
        )
        stmt = stmt.on_conflict_do_update(
            constraint="uq_routine_baseline_cell",
            set_={
                "median_value": stmt.excluded.median_value,
                "mad_value": stmt.excluded.mad_value,
                "sample_count": stmt.excluded.sample_count,
                "is_seed": stmt.excluded.is_seed,
                "updated_at": stmt.excluded.updated_at,
            },
        )
        await db.execute(stmt)
        await db.commit()
    return await _read_routine(senior, db)


async def _read_routine(senior: Senior, db: AsyncSession) -> RoutineOut:
    result = await db.execute(
        select(RoutineBaseline)
        .where(RoutineBaseline.senior_id == senior.id)
        .order_by(RoutineBaseline.time_block, RoutineBaseline.feature_name)
    )
    rows = list(result.scalars().all())
    return RoutineOut(
        rows=[RoutineRow.model_validate(r, from_attributes=True) for r in rows],
        updated_at=max((r.updated_at for r in rows), default=None),
    )


@router.get("/{sync_id}/routine", response_model=RoutineOut)
async def read_routine(
    sync_id: UUID,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
) -> RoutineOut:
    """A senior's fingerprint for the family contact paired to them or the responder covering them.

    Anyone else gets the same 404 as for a senior that doesn't exist.
    """
    senior = (await db.execute(select(Senior).where(Senior.sync_id == sync_id))).scalar_one_or_none()
    allowed = False
    if senior is not None and senior.deleted_at is None:
        if user.role == UserRole.BARANGAY_RESPONDER:
            allowed = _in_barangay(senior.barangay, _assigned_barangay(user))
        elif user.role == UserRole.FAMILY_CONTACT:
            paired = await db.execute(
                select(func.count(Contact.id)).where(
                    Contact.senior_id == senior.id,
                    Contact.user_id == user.id,
                    Contact.contact_type == ContactType.FAMILY,
                    Contact.is_active(),
                )
            )
            allowed = paired.scalar_one() > 0
    if not allowed:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Senior not found")
    return await _read_routine(senior, db)
