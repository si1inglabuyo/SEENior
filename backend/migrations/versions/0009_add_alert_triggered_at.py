"""alerts: triggered_at, the moment the phone's own detector actually fired

created_at is stamped when the POST reaches the server, which can be much later than
detection if the phone was offline. This column is nullable and sent by the client. It is
for audit and reporting only; escalation deadlines still use created_at.

Revision ID: 0009
Revises: 0008
Create Date: 2026-09-06
"""

from alembic import op
import sqlalchemy as sa

revision = "0009"
down_revision = "0008"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("alerts", sa.Column("triggered_at", sa.DateTime(), nullable=True))


def downgrade() -> None:
    op.drop_column("alerts", "triggered_at")
