"""hall_closed_days

A weekday the barangay hall is shut ("Hall closed today"), kept in the cloud so every
operator and both mobile apps see the same answer instead of one browser's localStorage. One row
per (barangay, Manila day). Not a sensor reading, and escalation never reads it.

Revision ID: 0021
Revises: 0020
Create Date: 2026-11-05
"""

from alembic import op
import sqlalchemy as sa

revision = "0021"
down_revision = "0020"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "hall_closed_days",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("barangay", sa.String(length=128), nullable=False, index=True),
        sa.Column("day", sa.Date(), nullable=False),
        sa.Column("set_by_user_id", sa.Integer(), sa.ForeignKey("users.id"), nullable=True),
        sa.Column("created_at", sa.DateTime(), server_default=sa.func.now(), nullable=False),
        sa.UniqueConstraint("barangay", "day", name="uq_hall_closed_day"),
    )


def downgrade() -> None:
    op.drop_table("hall_closed_days")
