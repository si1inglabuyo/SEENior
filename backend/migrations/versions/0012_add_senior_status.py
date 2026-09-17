"""senior roster status: seniors.status / status_changed_at / status_changed_by

The barangay dashboard has had a Deactivate / Reactivate control on the senior record
since 2026-09-09, and until now it had nowhere to write: it persisted the flip to the
responder's own browser, so a senior deactivated on one machine was still on the active
roster on every other one, and came back on that machine as soon as site data was cleared.
Two responders looking at the same barangay saw two different rosters, and the one who
pressed the button had every reason to think it had taken. This is the column it was
waiting for.

What the status is NOT
----------------------
It is not `deleted_at`, which is the senior's own decision taken in their own app and
which wipes the handset's local database. This is the responder's bookkeeping about their
own roster -- moved away, passed away, left the programme -- and it changes nothing on the
phone, because the server cannot reach into a handset and switch monitoring off.

It also does not, and must not, stop alerts reaching the barangay. The barangay is the
last tier in the chain; with the family contacts possibly unlinked too, dropping tier 3 on
a roster flag would produce an alert with nowhere to go and nobody told it went nowhere.
See SeniorStatus in app/db/models.py.

`status_changed_by` is a FK to users because this is one person acting on another person's
record, which makes "a responder did it" an insufficient answer.

Revision ID: 0012
Revises: 0011
Create Date: 2026-09-18
"""

from alembic import op
import sqlalchemy as sa

revision = "0012"
down_revision = "0011"
branch_labels = None
depends_on = None

SENIOR_STATUS = sa.Enum("active", "inactive", name="senior_status")


def upgrade() -> None:
    # checkfirst so a re-run against a database that already has the type is a no-op
    # rather than a DuplicateObject, matching migration 0005's handling of unlink_actor.
    SENIOR_STATUS.create(op.get_bind(), checkfirst=True)

    # server_default backfills every existing row to "active" in the same statement, which
    # is what lets the column be NOT NULL without a separate UPDATE pass. Every senior on
    # the roster today is on it legitimately, so "active" is the correct history to write.
    op.add_column(
        "seniors",
        sa.Column("status", SENIOR_STATUS, nullable=False, server_default="active"),
    )
    op.add_column("seniors", sa.Column("status_changed_at", sa.DateTime(), nullable=True))
    op.add_column(
        "seniors",
        sa.Column("status_changed_by", sa.Integer(), nullable=True),
    )
    op.create_foreign_key(
        "fk_seniors_status_changed_by_users",
        "seniors",
        "users",
        ["status_changed_by"],
        ["id"],
        # A responder account being deleted must not take the senior row with it, nor block
        # its own deletion. The audit loses the name and keeps the timestamp, which is the
        # right trade when the alternative is a foreign key that can wedge a delete.
        ondelete="SET NULL",
    )

    # The dashboard's roster query filters on this every time it loads.
    op.create_index("ix_seniors_status", "seniors", ["status"])


def downgrade() -> None:
    op.drop_index("ix_seniors_status", table_name="seniors")
    op.drop_constraint("fk_seniors_status_changed_by_users", "seniors", type_="foreignkey")
    op.drop_column("seniors", "status_changed_by")
    op.drop_column("seniors", "status_changed_at")
    op.drop_column("seniors", "status")
    SENIOR_STATUS.drop(op.get_bind(), checkfirst=True)
