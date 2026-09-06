"""alerts: triggered_at, the moment the phone's own detector actually fired

Every alert has only ever carried created_at, which Postgres stamps at INSERT time --
the moment the phone's POST reached the server, not the moment the senior's own
detection pipeline decided something was wrong. Those two moments are usually close
enough not to matter. On 2026-09-06 (pilot Day 1) they were 47 minutes apart: the
phone detected a screen_idle anomaly at 09:17 but had Wi-Fi switched off by hand, so
the POST did not land until 10:02. created_at read 10:02. The 09:17 moment, and the
47-minute gap itself, existed only in the phone's own local escalation_steps log --
invisible to the cloud database, the dashboard, the family app, and the CLAUDE.md §10
delivery-time metric, which cannot be computed without a real starting point.

Nullable and client-supplied. Nullable because every row written before this migration,
and any future client that omits the field, has no other source for it -- there is
nothing to backfill from. Client-supplied because the phone is the only thing that ever
knew this moment; the server finding out 47 minutes late is exactly the problem.

Deliberately NOT wired into escalation timing. family_deadline() in api/escalation.py
computes the family/barangay deadlines from created_at (the database's own clock) and
must keep doing so -- api/escalation.py's db_now() docstring already documents why a
client-supplied clock cannot be trusted for that (a dev machine off UTC, or a phone that
free-runs during Doze, would silently shift every deadline). This column is an audit and
reporting field only.

Revision ID: 0009
Revises: 0008
Create Date: 2026-09-06
"""

from alembic import op
import sqlalchemy as sa

revision = "0009"
down_revision = "0008"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("alerts", sa.Column("triggered_at", sa.DateTime(), nullable=True))


def downgrade() -> None:
    op.drop_column("alerts", "triggered_at")
