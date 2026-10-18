"""routine_baseline

The panel asked to see seniors' real routines. The Routine Fingerprint (a median and a MAD per
feature per time block, over the trailing 14 days) now syncs to the cloud so the family app and
the barangay dashboard can show it. It is a statistical summary, at most 20 rows per senior,
overwritten in place on every sync. Raw `Sensor_Data` and the per-day `Daily_Aggregates` stay on
the phone.

Revision ID: 0018
Revises: 0017
Create Date: 2026-10-19
"""

from alembic import op
import sqlalchemy as sa

revision = "0018"
down_revision = "0017"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "routine_baseline",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("senior_id", sa.Integer(), sa.ForeignKey("seniors.id"), nullable=False),
        sa.Column("feature_name", sa.String(length=32), nullable=False),
        sa.Column("time_block", sa.String(length=16), nullable=False),
        sa.Column("median_value", sa.Float(), nullable=False),
        sa.Column("mad_value", sa.Float(), nullable=False),
        sa.Column("sample_count", sa.Integer(), nullable=False),
        sa.Column("is_seed", sa.Boolean(), nullable=False),
        sa.Column("updated_at", sa.DateTime(), server_default=sa.func.now(), nullable=False),
        sa.UniqueConstraint("senior_id", "feature_name", "time_block", name="uq_routine_baseline_cell"),
    )
    op.create_index("ix_routine_baseline_senior_id", "routine_baseline", ["senior_id"])


def downgrade() -> None:
    op.drop_index("ix_routine_baseline_senior_id", table_name="routine_baseline")
    op.drop_table("routine_baseline")
