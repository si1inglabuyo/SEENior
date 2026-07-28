"""add profile + pairing fields: senior age/gender, user full_name/phone, contact relationship

Revision ID: 0003
Revises: 0002
Create Date: 2026-07-28
"""

from alembic import op
import sqlalchemy as sa

revision = "0003"
down_revision = "0002"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Senior profile fields shown on the family's "Connected" card (age · gender · barangay).
    # server_default backfills any pre-existing rows; new inserts always supply real values.
    op.add_column("seniors", sa.Column("age", sa.Integer(), nullable=False, server_default="0"))
    op.add_column("seniors", sa.Column("gender", sa.String(length=16), nullable=False, server_default="unknown"))

    # Family member's display identity, shown on the senior's Contacts list.
    op.add_column("users", sa.Column("full_name", sa.String(length=128), nullable=True))
    op.add_column("users", sa.Column("phone", sa.String(length=20), nullable=True))

    # How the family member relates to the senior ("daughter", "son", ...).
    op.add_column("contacts", sa.Column("relationship_label", sa.String(length=32), nullable=True))


def downgrade() -> None:
    op.drop_column("contacts", "relationship_label")
    op.drop_column("users", "phone")
    op.drop_column("users", "full_name")
    op.drop_column("seniors", "gender")
    op.drop_column("seniors", "age")
