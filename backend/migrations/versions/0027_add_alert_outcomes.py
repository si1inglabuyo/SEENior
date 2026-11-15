"""alert_outcomes

A senior who answers "I'm safe" inside the response window closes the alert before anyone else is
told, so it never reaches the cloud (`alerts`) and nobody can count how often a dropped phone, an
explained quiet spell or a real fall ended that way. This table is where the phone reports the
senior's one-tap answer (migration 0026's codes) for such an alert.

One row per alert the phone closed itself: when it fired, its type, risk level, time block and the
answer code. Deliberately NOT a copy of the alert: no location, no fall evidence or trace, no
deviation score, no escalation steps, and it never notifies anyone or appears in the family app
or the barangay dashboard. Nothing in detection, escalation or false-alarm tolerance reads it.

An alert that already escalated keeps its answer in `alerts.outcome_reason` (0026) instead, so an
analysis reads both. Deleted with the senior's account.

Revision ID: 0027
Revises: 0026
Create Date: 2026-11-16
"""

from alembic import op
import sqlalchemy as sa

revision = "0027"
down_revision = "0026"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "alert_outcomes",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("senior_id", sa.Integer(), sa.ForeignKey("seniors.id"), nullable=False),
        sa.Column("alert_sync_id", sa.Uuid(), nullable=False),
        sa.Column("trigger_type", sa.String(length=20), nullable=False),
        sa.Column("risk_level", sa.String(length=10), nullable=False),
        sa.Column("time_block", sa.String(length=16), nullable=False),
        sa.Column("triggered_at", sa.DateTime(), nullable=True),
        sa.Column("outcome_reason", sa.String(length=32), nullable=False),
        sa.Column("created_at", sa.DateTime(), server_default=sa.func.now(), nullable=False),
        sa.UniqueConstraint("senior_id", "alert_sync_id", name="uq_alert_outcome_alert"),
    )
    op.create_index("ix_alert_outcomes_senior_id", "alert_outcomes", ["senior_id"])


def downgrade() -> None:
    op.drop_index("ix_alert_outcomes_senior_id", table_name="alert_outcomes")
    op.drop_table("alert_outcomes")
