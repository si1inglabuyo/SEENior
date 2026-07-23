"""add invite_code / invite_code_expires_at to seniors

Revision ID: 0002
Revises: 0001
Create Date: 2026-07-23
"""

from alembic import op
import sqlalchemy as sa

revision = "0002"
down_revision = "0001"
branch_labels = None
depends_on = None

def upgrade() -> None:
     op.add_column("seniors", sa.Column("invite_code", sa.String(length=6), nullable=True))
     op.add_column("seniors", sa.Column("invite_code_expires_at", sa.DateTime(), nullable=True))


def downgrade() -> None:
     op.drop_column("seniors", "invite_code_expires_at")
     op.drop_column("seniors", "invite_code")