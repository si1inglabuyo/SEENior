"""users.language_preference

Stores the family app's language choice ("en" / "fil") on the cloud user row, since a
family account has no local database. Same codes as the senior app's language preference.

Revision ID: 0013
Revises: 0012
Create Date: 2026-09-27
"""

from alembic import op
import sqlalchemy as sa

revision = "0013"
down_revision = "0012"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # server_default backfills existing accounts to "en", so the column can be NOT NULL.
    op.add_column(
        "users",
        sa.Column("language_preference", sa.String(length=8), nullable=False, server_default="en"),
    )


def downgrade() -> None:
    op.drop_column("users", "language_preference")
