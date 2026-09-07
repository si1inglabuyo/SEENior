"""account deletion: soft-delete + reason on users and seniors

Deleting an account is soft on both sides. The row stays for audit with
deleted_at / deletion_reason / deletion_note set; every user-facing query must
now exclude deleted_at IS NOT NULL.

deletion_reason holds a stable code from the app's reason picker (e.g.
"switching_phone"), never the translated label, so the reasons stay analysable.
deletion_note is the user's own words, optional.

For users, the unique columns (username, email, google_sub) are tombstoned in
the delete endpoint (a "+deletedNNN" suffix) rather than guarded by a partial
index, so the email/username slots free up for a fresh sign-up with no change to
any constraint -- only the three new columns per table land here.

Revision ID: 0010
Revises: 0009
Create Date: 2026-09-07
"""

from alembic import op
import sqlalchemy as sa

revision = "0010"
down_revision = "0009"
branch_labels = None
depends_on = None


def _add(table: str) -> None:
    op.add_column(table, sa.Column("deleted_at", sa.DateTime(), nullable=True))
    op.add_column(table, sa.Column("deletion_reason", sa.String(64), nullable=True))
    op.add_column(table, sa.Column("deletion_note", sa.String(500), nullable=True))


def _drop(table: str) -> None:
    op.drop_column(table, "deletion_note")
    op.drop_column(table, "deletion_reason")
    op.drop_column(table, "deleted_at")


def upgrade() -> None:
    _add("users")
    _add("seniors")


def downgrade() -> None:
    _drop("seniors")
    _drop("users")
