"""users.language_preference

The family app is getting a Tagalog option (Profile -> Language), mirroring the senior
side's onboarding toggle. Unlike the senior side there is no local SQLite table to hold
this — a family account has no on-device database at all (spec §2) — so the
preference lives on the cloud `users` row and follows the account across devices instead
of being pinned to one handset.

Uses the same two codes the senior app already writes to Senior_Onboarding.language_
preference ("en" / "fil", WellnessMessages.ENGLISH / .FILIPINO on-device) purely so the
vocabulary matches across both apps; nothing here reads or writes that table, which stays
local-only per spec §11.

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
    # server_default backfills every existing account to "en" in the same statement,
    # which is what lets the column be NOT NULL without a separate UPDATE pass -- every
    # family/barangay account today either never saw a language question or explicitly
    # used the app in English, so "en" is the correct history to write.
    op.add_column(
        "users",
        sa.Column("language_preference", sa.String(length=8), nullable=False, server_default="en"),
    )


def downgrade() -> None:
    op.drop_column("users", "language_preference")
