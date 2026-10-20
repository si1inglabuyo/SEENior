"""alerts.fall_context

The panel asked how the system tells a sleeping senior dropping the phone from an active senior
falling while walking, and to see it in the database. The phone now records, per fall alert, how
much the phone was moving in the two seconds before the free fall began and whether the alert
fell in the senior's night block. This column carries those figures to the cloud when the alert
escalates, e.g. "pre_dev=2.8;active=1;night=0". Two derived figures and a flag, never the sample
stream, so no raw sensor data leaves the phone. Nullable: every other trigger and every older row
leaves it empty.

Revision ID: 0019
Revises: 0018
Create Date: 2026-10-21
"""

from alembic import op
import sqlalchemy as sa

revision = "0019"
down_revision = "0018"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("alerts", sa.Column("fall_context", sa.String(length=200), nullable=True))


def downgrade() -> None:
    op.drop_column("alerts", "fall_context")
