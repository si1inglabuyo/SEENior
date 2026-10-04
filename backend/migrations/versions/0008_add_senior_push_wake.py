"""seniors: push token and nudge timestamp, so the server can wake a sleeping phone

Some phones stop background work while asleep, which leaves gaps in monitoring overnight.
A high-priority data-only FCM message can still reach them, so the server nudges a quiet
phone. `push_token` is on `seniors` because a senior has no users row. Neither column is
behavioural data.

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
    # 255 matches device_tokens.token.
    op.add_column("seniors", sa.Column("push_token", sa.String(length=255), nullable=True))
    # NULL means never nudged, which counts as due.
    op.add_column("seniors", sa.Column("last_nudge_at", sa.DateTime(), nullable=True))


def downgrade() -> None:
    op.drop_column("seniors", "last_nudge_at")
    op.drop_column("seniors", "push_token")
