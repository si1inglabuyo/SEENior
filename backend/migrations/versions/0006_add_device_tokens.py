"""device_tokens: per-device FCM registration tokens for push delivery

One row per installed app per device (see the DeviceToken docstring in app/db/models.py).

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
        # 255 leaves headroom, since the FCM token format isn't contractual.
        sa.Column("token", sa.String(length=255), nullable=False),
        sa.Column("platform", sa.String(length=16), server_default="android", nullable=False),
        sa.Column("created_at", sa.DateTime(), server_default=sa.func.now(), nullable=False),
        sa.Column("last_seen_at", sa.DateTime(), server_default=sa.func.now(), nullable=False),
        # Deleting a user removes their tokens.
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
    )
    # Unique globally: a handed-over device's token must move to the new account.
    op.create_index("ix_device_tokens_token", "device_tokens", ["token"], unique=True)
    # Drives the send path's "every token for every family contact of this senior" lookup.
    op.create_index("ix_device_tokens_user_id", "device_tokens", ["user_id"])


def downgrade() -> None:
    op.drop_index("ix_device_tokens_user_id", table_name="device_tokens")
    op.drop_index("ix_device_tokens_token", table_name="device_tokens")
    op.drop_table("device_tokens")
