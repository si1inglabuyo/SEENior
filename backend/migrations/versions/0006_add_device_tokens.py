"""device_tokens: per-device FCM registration tokens for push delivery

Adds the table that lets the backend PUSH an alert to a family contact instead of
waiting for their app to poll for it. Without this the escalation chain only reaches a
family member who already has the app open (CLAUDE.md §7).

One row per installed app per device, not a column on `users` — see the DeviceToken
docstring in app/db/models.py for why that distinction is load-bearing.

Revision ID: 0006
Revises: 0005
Create Date: 2026-08-17
"""

from alembic import op
import sqlalchemy as sa

revision = "0006"
down_revision = "0005"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "device_tokens",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("user_id", sa.Integer(), nullable=False),
        # 255 is comfortably above the ~163 chars FCM tokens run to today, but the format
        # is not contractual and has grown before, so this is sized with headroom.
        sa.Column("token", sa.String(length=255), nullable=False),
        sa.Column("platform", sa.String(length=16), server_default="android", nullable=False),
        sa.Column("created_at", sa.DateTime(), server_default=sa.func.now(), nullable=False),
        sa.Column("last_seen_at", sa.DateTime(), server_default=sa.func.now(), nullable=False),
        # A user going away takes their tokens with them; leaving orphans would mean
        # pushing to a device whose account no longer exists.
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
    )
    # Unique GLOBALLY, not per user: one physical device holds one token, and if it is
    # handed to another person who signs in, that token must move accounts rather than
    # exist twice and deliver a stranger's alerts to the previous owner.
    op.create_index("ix_device_tokens_token", "device_tokens", ["token"], unique=True)
    # Drives the send path's "every token for every family contact of this senior" lookup.
    op.create_index("ix_device_tokens_user_id", "device_tokens", ["user_id"])


def downgrade() -> None:
    op.drop_index("ix_device_tokens_user_id", table_name="device_tokens")
    op.drop_index("ix_device_tokens_token", table_name="device_tokens")
    op.drop_table("device_tokens")
