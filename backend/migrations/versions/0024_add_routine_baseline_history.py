"""routine_baseline_history

The panel wanted to see how a routine is learned, not only where it stands today. The phone now
sends one copy of the Routine Fingerprint per day (a median and a MAD per feature per time block,
at most 20 rows a day) and the cloud keeps the last 30 days, so the family app and the barangay
dashboard can draw the trend.

This AMENDS the "current fingerprint, never a history" line in CLAUDE.md section 11 (2026-10-19).
Still a statistical summary, not a sensor reading: `Sensor_Data` and `Daily_Aggregates` stay on
the phone. `is_estimate` marks days the phone worked out afterwards from its stored daily
summaries (today's rules applied to older data) as opposed to a copy saved that night.

Revision ID: 0024
Revises: 0023
Create Date: 2026-11-14
"""

from alembic import op
import sqlalchemy as sa

revision = "0024"
down_revision = "0023"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "routine_baseline_history",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("senior_id", sa.Integer(), sa.ForeignKey("seniors.id"), nullable=False),
        sa.Column("day", sa.Date(), nullable=False),
        sa.Column("feature_name", sa.String(length=32), nullable=False),
        sa.Column("time_block", sa.String(length=16), nullable=False),
        sa.Column("median_value", sa.Float(), nullable=False),
        sa.Column("mad_value", sa.Float(), nullable=False),
        sa.Column("sample_count", sa.Integer(), nullable=False),
        sa.Column("is_seed", sa.Boolean(), nullable=False),
        sa.Column("is_estimate", sa.Boolean(), nullable=False),
        sa.Column("updated_at", sa.DateTime(), server_default=sa.func.now(), nullable=False),
        sa.UniqueConstraint(
            "senior_id", "day", "feature_name", "time_block", name="uq_routine_history_cell"
        ),
    )
    op.create_index("ix_routine_baseline_history_senior_id", "routine_baseline_history", ["senior_id"])


def downgrade() -> None:
    op.drop_index("ix_routine_baseline_history_senior_id", table_name="routine_baseline_history")
    op.drop_table("routine_baseline_history")
