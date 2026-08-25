"""seniors: last check-in, battery level and charging state

The senior's phone previously only ever spoke to the server on an event — creating the
account, generating an invite, raising an alert — so between alerts there was no way to
tell a phone that was quietly monitoring from one that was flat, switched off, or had
stopped running the app after a reboot. A monitoring system that can silently stop
monitoring has to be able to say so, and these three columns are what it says it with.

**Three scalars, deliberately, not a history table.** CLAUDE.md §11 keeps raw sensor data
on the senior's device, and §4 counts battery and charging among the sensors. The line
this migration draws is between a *reading* and a *record*: the current charge says only
whether the phone can keep working, while a series of charge readings over time is
behavioural — when someone plugs in is roughly when they go to bed, which is exactly the
routine §11 exists to keep local. Each check-in overwrites the last. There is no
`battery_history`, and adding one would breach §11 in substance whatever the column was
called.

Nullable because every existing senior predates this: NULL means "never heard from",
which is a different and honest answer from "0% and discharging".

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
    # When the senior's phone last checked in. The family app reads this as "no contact
    # since ...", which is the part that matters most: a stale timestamp means monitoring
    # may have stopped, and nothing else in the system can currently reveal that.
    op.add_column("seniors", sa.Column("last_seen_at", sa.DateTime(), nullable=True))
    # 0-100. Overwritten on every check-in and never appended to.
    op.add_column("seniors", sa.Column("battery_percent", sa.Integer(), nullable=True))
    # Context for the number above: 12% and charging is fine, 12% and draining is not.
    op.add_column("seniors", sa.Column("is_charging", sa.Boolean(), nullable=True))


def downgrade() -> None:
    op.drop_column("seniors", "is_charging")
    op.drop_column("seniors", "battery_percent")
    op.drop_column("seniors", "last_seen_at")
