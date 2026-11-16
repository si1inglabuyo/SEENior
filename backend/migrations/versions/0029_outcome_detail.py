"""outcome_detail: the senior's typed reason after choosing "Others"

When the senior answers the "what happened" question with "Others" (`outcome_reason = 'other'`),
the phone now lets them type a short reason (at most 120 characters). It is kept beside the code so
the team can read the reasons when writing up the results.

Two nullable columns, one per place an answer lands: `alerts.outcome_detail` (the alert had
already escalated) and `alert_outcomes.outcome_detail` (the senior cancelled inside the window).
Only ever set when the code is `other`; the server refuses it otherwise. Not shown in the family
app or the barangay dashboard, notifies nobody, and nothing in detection, escalation or
false-alarm tolerance reads it. Deleted with the senior's account, like the rows it sits on.

Free text is personal data, so the consent form must mention it (CLAUDE.md section 11).

Revision ID: 0029
Revises: 0028
Create Date: 2026-11-16
"""

from alembic import op
import sqlalchemy as sa

revision = "0029"
down_revision = "0028"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("alerts", sa.Column("outcome_detail", sa.String(length=120), nullable=True))
    op.add_column("alert_outcomes", sa.Column("outcome_detail", sa.String(length=120), nullable=True))


def downgrade() -> None:
    op.drop_column("alert_outcomes", "outcome_detail")
    op.drop_column("alerts", "outcome_detail")
