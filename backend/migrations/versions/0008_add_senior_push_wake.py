"""seniors: push token and nudge timestamp, so the server can wake a sleeping phone

Measured on the Infinix X6885 (Android 15, XOS) on 2026-08-29: every deferrable
mechanism the phone offers stops while it sleeps. The sensor service's coroutine
`delay(5 min)` produced ONE sample in 24 minutes; `dumpsys jobscheduler` showed the
persisted 15-minute watchdog job running three times in 13.5 hours, with a 12.5-hour
hole straight through the night. That is the window in which a senior living alone is
least observed, so it is the window passive monitoring cannot afford to lose.

This is the same conclusion the escalation deadline already reached in migration 0007's
neighbourhood: the phone cannot be trusted to hold a clock, so the server holds it. A
high-priority data-only FCM message is exempt from Doze and is the one thing that can
still reach a suspended handset, so the backend nudges a phone that has gone quiet and
the phone takes a sample on waking.

`push_token` sits on `seniors` rather than in `device_tokens`, and the difference is not
laziness. That table is keyed to `users.id` NOT NULL, and a senior deliberately has no
account (CLAUDE.md §2) — there is nothing to key to. Its two reasons for existing also
do not apply here: a senior has exactly one monitored phone, not a phone and a tablet,
and there is no second account a handset could be signed into.

Neither column is behavioural data. A token identifies a device, and a nudge timestamp
records what the server did, not what the senior did — the §11 boundary is untouched.

Revision ID: 0008
Revises: 0007
Create Date: 2026-08-29
"""

from alembic import op
import sqlalchemy as sa

revision = "0008"
down_revision = "0007"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # 255 matches device_tokens.token: comfortably above the ~163 characters FCM tokens
    # run to today, with headroom for a format that has grown before.
    op.add_column("seniors", sa.Column("push_token", sa.String(length=255), nullable=True))
    # NULL means never nudged, which is the correct starting state and is distinct from
    # "nudged long ago" — the sweep treats both as due, but only one of them is a phone
    # we have already given up on once.
    op.add_column("seniors", sa.Column("last_nudge_at", sa.DateTime(), nullable=True))


def downgrade() -> None:
    op.drop_column("seniors", "last_nudge_at")
    op.drop_column("seniors", "push_token")
