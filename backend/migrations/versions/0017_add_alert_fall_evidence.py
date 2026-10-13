"""alerts.fall_evidence

The panel asked to see, in the database, which phases satisfied a fall alert. The phone already
records what each detector gate measured (free fall, impact, stillness, rotation) in its local
Alerts.fall_evidence. This column carries the same four summary figures to the cloud when the
alert escalates. Durations, a peak magnitude and a peak angular speed, never the sample stream,
so no raw sensor data leaves the phone. Nullable: every other trigger and every older row
leaves it empty.

Revision ID: 0017
Revises: 0016
Create Date: 2026-10-14
"""

from alembic import op
import sqlalchemy as sa

revision = "0017"
down_revision = "0016"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("alerts", sa.Column("fall_evidence", sa.String(length=200), nullable=True))


def downgrade() -> None:
    op.drop_column("alerts", "fall_evidence")
