"""seniors: last check-in, battery level and charging state

Lets the system tell a phone that is quietly monitoring from one that is flat, off or no
longer running the app. Three scalars, not a history: a charging history would reveal the
senior's routine, which stays on the device. Nullable because existing seniors have no
value; NULL means "never heard from".

Revision ID: 0007
Revises: 0006
Create Date: 2026-08-26
"""

from alembic import op
import sqlalchemy as sa

revision = "0007"
down_revision = "0006"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # When the phone last checked in. A stale value means monitoring may have stopped.
    op.add_column("seniors", sa.Column("last_seen_at", sa.DateTime(), nullable=True))
    # 0-100. Overwritten on every check-in and never appended to.
    op.add_column("seniors", sa.Column("battery_percent", sa.Integer(), nullable=True))
    # Context for the number above: 12% and charging is fine, 12% and draining is not.
    op.add_column("seniors", sa.Column("is_charging", sa.Boolean(), nullable=True))


def downgrade() -> None:
    op.drop_column("seniors", "is_charging")
    op.drop_column("seniors", "battery_percent")
    op.drop_column("seniors", "last_seen_at")
