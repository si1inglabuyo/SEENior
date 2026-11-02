"""users.two_factor_enabled, users.two_factor_phone, login_challenges

Barangay responders can be required to enter a 6-digit code texted to their phone after a
correct password. `two_factor_enabled` is the per-account switch (default off, so no account is
locked out by the migration). `two_factor_phone` is where the code is texted; it is separate from
`phone` so a number kept for sign-in codes never receives alert texts. `login_challenges` holds each pending code as a keyed hash with a
short expiry and a wrong-guess counter. Nothing here is a sensor reading.

Revision ID: 0020
Revises: 0019
Create Date: 2026-11-02
"""

from alembic import op
import sqlalchemy as sa

revision = "0020"
down_revision = "0019"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "users",
        sa.Column("two_factor_enabled", sa.Boolean(), nullable=False, server_default=sa.false()),
    )
    op.add_column("users", sa.Column("two_factor_phone", sa.String(length=20), nullable=True))
    op.create_table(
        "login_challenges",
        sa.Column("id", sa.String(length=36), primary_key=True),
        sa.Column("user_id", sa.Integer(), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("code_hash", sa.String(length=64), nullable=False),
        sa.Column("expires_at", sa.DateTime(), nullable=False),
        sa.Column("attempts", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("sends", sa.Integer(), nullable=False, server_default="1"),
        sa.Column("last_sent_at", sa.DateTime(), server_default=sa.func.now(), nullable=False),
        sa.Column("consumed", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("created_at", sa.DateTime(), server_default=sa.func.now(), nullable=False),
    )
    op.create_index("ix_login_challenges_user_id", "login_challenges", ["user_id"])


def downgrade() -> None:
    op.drop_index("ix_login_challenges_user_id", table_name="login_challenges")
    op.drop_table("login_challenges")
    op.drop_column("users", "two_factor_phone")
    op.drop_column("users", "two_factor_enabled")
