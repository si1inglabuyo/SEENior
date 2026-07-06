"""initial schema: users, seniors, contacts, alerts

Revision ID: 0001
Revises:
Create Date: 2026-07-05
"""

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "users",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("username", sa.String(length=64), nullable=False),
        sa.Column("password_hash", sa.String(length=255), nullable=False),
        sa.Column(
            "role",
            sa.Enum("family_contact", "barangay_responder", name="user_role"),
            nullable=False,
        ),
        sa.Column("barangay", sa.String(length=128), nullable=True),
        sa.Column("is_active", sa.Boolean(), nullable=False),
        sa.Column("created_at", sa.DateTime(), server_default=sa.func.now(), nullable=False),
        sa.UniqueConstraint("username", name="uq_users_username"),
    )
    op.create_index("ix_users_username", "users", ["username"])

    op.create_table(
        "seniors",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("sync_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("first_name", sa.String(length=64), nullable=False),
        sa.Column("last_name", sa.String(length=64), nullable=False),
        sa.Column("barangay", sa.String(length=128), nullable=False),
        sa.Column("address", sa.String(length=255), nullable=False),
        sa.Column("mobile_number", sa.String(length=20), nullable=False),
        sa.Column("created_at", sa.DateTime(), server_default=sa.func.now(), nullable=False),
        sa.UniqueConstraint("sync_id", name="uq_seniors_sync_id"),
    )
    op.create_index("ix_seniors_sync_id", "seniors", ["sync_id"])

    op.create_table(
        "contacts",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("senior_id", sa.Integer(), sa.ForeignKey("seniors.id"), nullable=False),
        sa.Column("user_id", sa.Integer(), sa.ForeignKey("users.id"), nullable=False),
        sa.Column(
            "contact_type",
            sa.Enum("family", "barangay_responder", name="contact_type"),
            nullable=False,
        ),
        sa.Column("created_at", sa.DateTime(), server_default=sa.func.now(), nullable=False),
        sa.UniqueConstraint("senior_id", "user_id", name="uq_contact_pair"),
    )

    op.create_table(
        "alerts",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("sync_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("senior_id", sa.Integer(), sa.ForeignKey("seniors.id"), nullable=False),
        sa.Column(
            "risk_level",
            sa.Enum("low", "medium", "high", name="risk_level"),
            nullable=False,
        ),
        sa.Column(
            "trigger_type",
            sa.Enum(
                "inactivity",
                "movement",
                "screen_idle",
                "charging",
                "sos",
                "ml_flag",
                "fall_pattern",
                name="trigger_type",
            ),
            nullable=False,
        ),
        sa.Column(
            "status",
            sa.Enum(
                "pending",
                "acknowledged",
                "escalated",
                "resolved",
                "false_positive",
                name="alert_status",
            ),
            nullable=False,
        ),
        sa.Column("location_cluster_id", sa.String(length=64), nullable=True),
        sa.Column("escalation_steps", sa.JSON(), nullable=True),
        sa.Column("created_at", sa.DateTime(), server_default=sa.func.now(), nullable=False),
        sa.Column("resolved_at", sa.DateTime(), nullable=True),
        sa.UniqueConstraint("sync_id", name="uq_alerts_sync_id"),
    )
    op.create_index("ix_alerts_sync_id", "alerts", ["sync_id"])


def downgrade() -> None:
    op.drop_table("alerts")
    op.drop_table("contacts")
    op.drop_table("seniors")
    op.drop_table("users")
    for enum_name in ("alert_status", "trigger_type", "risk_level", "contact_type", "user_role"):
        sa.Enum(name=enum_name).drop(op.get_bind(), checkfirst=True)
