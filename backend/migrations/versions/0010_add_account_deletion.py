"""account deletion: soft-delete + reason on users and seniors

Rows are kept for audit with deleted_at / deletion_reason / deletion_note set; user-facing
queries must exclude deleted_at IS NOT NULL. deletion_reason is a stable code, not the
translated label. The unique user columns are tombstoned in the delete endpoint, so no
constraint changes are needed here.

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
