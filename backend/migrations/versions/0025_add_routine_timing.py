"""routine_timing

The routine card said only "Morning / Afternoon / Evening / Night". Each senior's blocks are
different (the day is split from the wake and sleep times typed at setup), so the phone now sends
the four block start times, plus the time it usually first saw the senior active in the morning and
last active in the evening, so the family app can show real ranges and what was actually observed.

One row per senior, overwritten in place. The observed times are a median over the last 14 days and
are only sent once at least 3 days stand behind them; they are a summary, never one day and never a
reading. This is an addition to the 2026-10-19 / 2026-11-14 routine amendments in CLAUDE.md section
11. Display only: detection and escalation never read this table.

Revision ID: 0025
Revises: 0024
Create Date: 2026-11-15
"""

from alembic import op
import sqlalchemy as sa

revision = "0025"
down_revision = "0024"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "routine_timing",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("senior_id", sa.Integer(), sa.ForeignKey("seniors.id"), nullable=False, unique=True),
        sa.Column("morning_start", sa.Integer(), nullable=False),
        sa.Column("afternoon_start", sa.Integer(), nullable=False),
        sa.Column("evening_start", sa.Integer(), nullable=False),
        sa.Column("night_start", sa.Integer(), nullable=False),
        sa.Column("wake_minute", sa.Integer(), nullable=True),
        sa.Column("wake_days", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("evening_last_minute", sa.Integer(), nullable=True),
        sa.Column("evening_last_days", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("updated_at", sa.DateTime(), server_default=sa.func.now(), nullable=False),
    )


def downgrade() -> None:
    op.drop_table("routine_timing")
