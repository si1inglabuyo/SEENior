"""pook responders

Each pook has a designated responder the hall staff phone when an alert comes from that area.
The name and number sit on the pook row, one responder per pook, and are edited on the barangay
dashboard. A label for the desk staff only: escalation never reads it.

The rows are seeded with PLACEHOLDER names and numbers (09000000001 and up, which are not real
subscriber numbers) so the screen has something to show. They must be replaced with the real
responders before a pilot.

Revision ID: 0022
Revises: 0021
Create Date: 2026-11-08
"""

from alembic import op
import sqlalchemy as sa

revision = "0022"
down_revision = "0021"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("pooks", sa.Column("responder_name", sa.String(length=64), nullable=True))
    op.add_column("pooks", sa.Column("responder_phone", sa.String(length=32), nullable=True))
    conn = op.get_bind()
    rows = conn.execute(sa.text("SELECT id, name FROM pooks ORDER BY id")).fetchall()
    for n, (pook_id, name) in enumerate(rows, start=1):
        conn.execute(
            sa.text("UPDATE pooks SET responder_name = :who, responder_phone = :phone WHERE id = :id"),
            {"who": f"Responder, {name}"[:64], "phone": f"0900000{n:04d}", "id": pook_id},
        )


def downgrade() -> None:
    op.drop_column("pooks", "responder_phone")
    op.drop_column("pooks", "responder_name")
