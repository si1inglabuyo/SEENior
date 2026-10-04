"""senior roster status: seniors.status / status_changed_at / status_changed_by

Gives the dashboard's Deactivate / Reactivate control somewhere to save, so every
responder sees the same roster. It is the responder's bookkeeping, not `deleted_at`, and
it does not stop monitoring or stop alerts reaching the barangay (see SeniorStatus in
app/db/models.py). `status_changed_by` is a FK to users so the change is attributable.

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
    # checkfirst makes a re-run a no-op if the type already exists.
    SENIOR_STATUS.create(op.get_bind(), checkfirst=True)

    # server_default backfills existing rows to "active", so the column can be NOT NULL.
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
        # Deleting a responder account must not delete the senior row or block the delete.
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
