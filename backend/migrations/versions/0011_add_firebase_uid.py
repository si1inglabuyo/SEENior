"""add firebase_uid to users, for Firebase Authentication (family accounts)

Mirrors google_sub: a stable per-account ID from the identity provider (here,
Firebase Auth instead of Google Sign-In directly), used to recognize a
returning sign-in independent of email changes.

Revision ID: 0011
Revises: 0010
Create Date: 2026-09-14
"""

from alembic import op
import sqlalchemy as sa

revision = "0011"
down_revision = "0010"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "users",
        sa.Column("firebase_uid", sa.String(255), nullable=True),
    )
    op.create_index(
        "ix_users_firebase_uid", "users", ["firebase_uid"], unique=True
    )


def downgrade() -> None:
    op.drop_index("ix_users_firebase_uid", table_name="users")
    op.drop_column("users", "firebase_uid")
