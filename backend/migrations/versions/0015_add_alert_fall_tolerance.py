"""alerts.fall_tolerance

A senior who keeps cancelling dropped-phone alarms makes fall detection slightly harder to
trigger (see FallTolerance in the Android app). This column records, on each fall alert, the
thresholds the detector was set to and how many false and real fall alerts earned them, so the
effect is visible in the cloud database and not only inside the app. Settings and two counts,
never a sensor reading. Nullable: every other trigger and every older row leaves it empty.

Revision ID: 0015
Revises: 0014
Create Date: 2026-10-12
"""

from alembic import op
import sqlalchemy as sa

revision = "0015"
down_revision = "0014"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("alerts", sa.Column("fall_tolerance", sa.String(length=200), nullable=True))


def downgrade() -> None:
    op.drop_column("alerts", "fall_tolerance")
