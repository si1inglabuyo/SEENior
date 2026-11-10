"""alerts.fall_trace

The panel asked to see, in the database, the readings behind a fall alert's pre_dev (the mean
distance of the phone's acceleration from 1 g over the 2 s before the free fall). The phone now
sends that series when a confirmed fall escalates, e.g. "dev=0.0034,0.0034,...", one figure per
reading, oldest first. Adding them gives fall_context's sum; dividing by n gives pre_dev.

This AMENDS the raw-data boundary in CLAUDE.md section 11: it is the first per-reading series to
leave the phone. Each figure is the distance of the TOTAL acceleration from 1 g, so the three axes,
the phone's orientation and any timestamp stay on the device. Nullable: every other trigger and
every older row leaves it empty. TEXT, since 2 s at 50 Hz is about 100 figures (about 700 chars).

Revision ID: 0023
Revises: 0022
Create Date: 2026-11-10
"""

from alembic import op
import sqlalchemy as sa

revision = "0023"
down_revision = "0022"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("alerts", sa.Column("fall_trace", sa.Text(), nullable=True))


def downgrade() -> None:
    op.drop_column("alerts", "fall_trace")
