"""alert_outcomes.cancelled_after_seconds and sampling_daily

Two things the team needs to see in the database for the evaluation.

1. A cancelled SOS. The swipe is cancelled inside its 10 s window, so the alert never reaches
   `alerts`; whether seniors swipe by mistake could not be counted. The phone now reports it to
   `alert_outcomes` with the answer code `sos_cancelled` (no question is asked for an SOS), and
   every row there gains `cancelled_after_seconds`: how long after the alert fired the senior
   closed it. For an SOS that separates an instant slip from a deliberate change of mind.

2. The 5-minute sampling cycle. `sampling_daily` keeps, per senior per day, how many 5-minute
   readings the phone collected (about 288 a day when it runs all day) and how many fell in each
   time block. It counts readings; it holds none and says nothing about what the senior did, only
   whether the phone was collecting. It shows which phones lose hours to the operating system.
   Last 30 days only. Deleted with the senior's account. Nothing in detection reads it.

Revision ID: 0028
Revises: 0027
Create Date: 2026-11-16
"""

from alembic import op
import sqlalchemy as sa

revision = "0028"
down_revision = "0027"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("alert_outcomes", sa.Column("cancelled_after_seconds", sa.Integer(), nullable=True))
    op.create_table(
        "sampling_daily",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("senior_id", sa.Integer(), sa.ForeignKey("seniors.id"), nullable=False),
        sa.Column("day", sa.Date(), nullable=False),
        sa.Column("morning", sa.Integer(), nullable=False),
        sa.Column("afternoon", sa.Integer(), nullable=False),
        sa.Column("evening", sa.Integer(), nullable=False),
        sa.Column("night", sa.Integer(), nullable=False),
        sa.Column("updated_at", sa.DateTime(), server_default=sa.func.now(), nullable=False),
        sa.UniqueConstraint("senior_id", "day", name="uq_sampling_day"),
    )
    op.create_index("ix_sampling_daily_senior_id", "sampling_daily", ["senior_id"])


def downgrade() -> None:
    op.drop_index("ix_sampling_daily_senior_id", table_name="sampling_daily")
    op.drop_table("sampling_daily")
    op.drop_column("alert_outcomes", "cancelled_after_seconds")
