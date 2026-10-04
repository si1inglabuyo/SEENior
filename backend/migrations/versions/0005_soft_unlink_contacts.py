"""soft unlink: contacts.unlinked_at / unlinked_by, partial unique index on active pairs

Unlinking used to delete the row. It is now kept with unlinked_at/unlinked_by set, and
the plain unique constraint is replaced by a partial unique index over live pairings, so
a pair can re-link.

Revision ID: 0005
Revises: 0004
Create Date: 2026-08-07
"""

from alembic import op
import sqlalchemy as sa

revision = "0005"
down_revision = "0004"
branch_labels = None
depends_on = None

UNLINK_ACTOR = sa.Enum("senior", "family", name="unlink_actor")


def upgrade() -> None:
    UNLINK_ACTOR.create(op.get_bind(), checkfirst=True)

    op.add_column("contacts", sa.Column("unlinked_at", sa.DateTime(), nullable=True))
    op.add_column("contacts", sa.Column("unlinked_by", UNLINK_ACTOR, nullable=True))

    # Existing rows are all live pairings, so unlinked_at stays NULL.
    op.drop_constraint("uq_contact_pair", "contacts", type_="unique")
    op.create_index(
        "uq_contact_pair_active",
        "contacts",
        ["senior_id", "user_id"],
        unique=True,
        postgresql_where=sa.text("unlinked_at IS NULL"),
    )


def downgrade() -> None:
    # Delete the soft-unlinked rows first; they would violate the restored constraint.
    op.execute(sa.text("DELETE FROM contacts WHERE unlinked_at IS NOT NULL"))

    op.drop_index("uq_contact_pair_active", table_name="contacts")
    op.create_unique_constraint("uq_contact_pair", "contacts", ["senior_id", "user_id"])

    op.drop_column("contacts", "unlinked_by")
    op.drop_column("contacts", "unlinked_at")
    UNLINK_ACTOR.drop(op.get_bind(), checkfirst=True)
