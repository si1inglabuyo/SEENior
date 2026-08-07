"""soft unlink: contacts.unlinked_at / unlinked_by, partial unique index on active pairs

Unlinking used to DELETE the contacts row, which made an accidental tap unrecoverable
and left no record that the pairing ever existed. The row now survives with
unlinked_at/unlinked_by set.

The plain UniqueConstraint on (senior_id, user_id) has to go: with rows surviving an
unlink, the same senior and family member could never re-pair. It's replaced by a
PARTIAL unique index covering only live pairings, so re-linking is allowed while a
duplicate ACTIVE link is still impossible.

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

    # Existing rows are all live pairings, so unlinked_at stays NULL for them and they
    # are picked up by the partial index below exactly as before.
    op.drop_constraint("uq_contact_pair", "contacts", type_="unique")
    op.create_index(
        "uq_contact_pair_active",
        "contacts",
        ["senior_id", "user_id"],
        unique=True,
        postgresql_where=sa.text("unlinked_at IS NULL"),
    )


def downgrade() -> None:
    # Hard-delete the soft-unlinked rows first: they are exactly the rows that would
    # violate the plain unique constraint being restored, and under the old schema they
    # had no representation anyway (an unlink WAS a delete).
    op.execute(sa.text("DELETE FROM contacts WHERE unlinked_at IS NOT NULL"))

    op.drop_index("uq_contact_pair_active", table_name="contacts")
    op.create_unique_constraint("uq_contact_pair", "contacts", ["senior_id", "user_id"])

    op.drop_column("contacts", "unlinked_by")
    op.drop_column("contacts", "unlinked_at")
    UNLINK_ACTOR.drop(op.get_bind(), checkfirst=True)
