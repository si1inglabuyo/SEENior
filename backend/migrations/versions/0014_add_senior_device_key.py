"""seniors.device_key_hash

A senior's sync_id is visible to linked family members and barangay responders, so it can't
authenticate the phone. The phone now holds a random key and the server stores only its SHA-256
hash (see app/core/device_key.py). Nullable: seniors registered before this have no key until
their phone claims one, and the server accepts them the old way until then.

Revision ID: 0014
Revises: 0013
Create Date: 2026-10-04
"""

from alembic import op
import sqlalchemy as sa

revision = "0014"
down_revision = "0013"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("seniors", sa.Column("device_key_hash", sa.String(length=64), nullable=True))


def downgrade() -> None:
    op.drop_column("seniors", "device_key_hash")
