"""alerts.outcome_reason

After a senior answers "I'm safe" / "I'm fine now" to an anomaly or fall alert, the phone now asks
one optional question: what happened. The answer is a fixed code, never free text:

  fall_pattern:  phone_dropped | fell_fine | other
  other triggers (inactivity, movement, screen_idle, charging, ml_flag):
                 unused_fine | left_behind_fine | other

It exists so the team can say how many alerts were a dropped phone, a real fall that turned out
fine, or an explained quiet spell, which the status alone ("self_cancelled") cannot. It is a RECORD,
not a rule: nothing in detection or escalation reads it, and it does not feed the false-alarm
tolerance. Only alerts that already reached the cloud carry it; an alert the senior cancelled
inside the response window stays on the phone, so does its answer.

Nullable: every older row and every alert the senior skipped leaves it empty. It is a code, not a
sensor reading, so the privacy boundary in CLAUDE.md section 11 is unchanged.

Revision ID: 0026
Revises: 0025
Create Date: 2026-11-16
"""

from alembic import op
import sqlalchemy as sa

revision = "0026"
down_revision = "0025"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("alerts", sa.Column("outcome_reason", sa.String(length=32), nullable=True))


def downgrade() -> None:
    op.drop_column("alerts", "outcome_reason")
