"""routine_timing: the time the phone usually saw the senior last active in the afternoon

The routine cards could say when a senior is usually up (morning) and usually last active
(evening), but nothing for the afternoon, so the afternoon had a blank where the senior's own
phone shows "Usually active until about ...". The phone already stores that time for every block
in its local Daily_Aggregates; it just was not sent. This adds the two columns for it.

Same rules as the other observed pair: a median over the last 14 days, null until at least 3 days
stand behind it, one row per senior overwritten in place. A summary, never one day and never a
reading. Display only: detection and escalation never read this table. Existing rows keep NULL / 0
until the senior's phone updates and syncs again.

Revision ID: 0031
Revises: 0030
Create Date: 2026-11-25
"""

from alembic import op
import sqlalchemy as sa

revision = "0031"
down_revision = "0030"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("routine_timing", sa.Column("afternoon_last_minute", sa.Integer(), nullable=True))
    op.add_column(
        "routine_timing",
        sa.Column("afternoon_last_days", sa.Integer(), nullable=False, server_default="0"),
    )


def downgrade() -> None:
    op.drop_column("routine_timing", "afternoon_last_days")
    op.drop_column("routine_timing", "afternoon_last_minute")
