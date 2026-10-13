"""pooks, and seniors.pook_id

Barangay U.P. Campus is divided into sixteen pooks in two sectors (north and south of the
Sunken Garden). Each senior can name the pook they live in so the barangay dashboard can show
a responder which area an alert is in. The label is display only: escalation never reads it,
so a senior with no pook, or a wrong one, is escalated exactly as before.

`name` is the short form and `description` keeps the printed name from the barangay profile.

Revision ID: 0016
Revises: 0015
Create Date: 2026-10-13
"""

from alembic import op
import sqlalchemy as sa

revision = "0016"
down_revision = "0015"
branch_labels = None
depends_on = None

BARANGAY = "U.P. Campus"

# (name, sector, printed name)
POOKS = [
    ("Arboretum", "north", "Pook Arboretum"),
    ("Dagohoy", "north", "Pook Francisco Dagohoy"),
    ("Ricarte", "north", "Pook Artemio Ricarte"),
    ("Palaris", "north", "Pook Palaris"),
    ("Sumakwel (Area 1/3)", "north", "Pook Sumakwel (Area 1 / 3)"),
    ("Sumakwel (Area 2)", "north", "Pook Sumakwel (Area 2)"),
    ("Aguinaldo", "south", "Pook Gen. Emilio Aguinaldo (including Hardin ng Rosas and Hardin ng Bougainvilla)"),
    ("Amorsolo", "south", "Pook Fernando Amorsolo"),
    ("Daang Tubo", "south", "Pook Daang Tubo"),
    ("Carlos P. Garcia", "south", "Pook Carlos P. Garcia"),
    ("Hernandez - Timog", "south", "Pook Amado V. Hernandez - Timog (U.P. Employees Village A including Hardin ng Dona Aurora)"),
    ("Hernandez - Hilaga", "south", "Pook Amado V. Hernandez - Hilaga (U.P. Employees Village B including Village C)"),
    ("Libis", "south", "Pook Libis"),
    ("Libis (Annex)", "south", "Pook Libis (Annex)"),
    ("Sikatuna Bliss", "south", "Pook Sikatuna Bliss"),
    ("Area 17", "south", "Area 17"),
]


def upgrade() -> None:
    pooks = op.create_table(
        "pooks",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("barangay", sa.String(length=128), nullable=False),
        sa.Column("name", sa.String(length=64), nullable=False),
        sa.Column("description", sa.String(length=255), nullable=True),
        sa.Column("sector", sa.String(length=16), nullable=False),
    )
    op.create_index("ix_pooks_barangay", "pooks", ["barangay"])
    op.bulk_insert(
        pooks,
        [
            {"barangay": BARANGAY, "name": name, "sector": sector, "description": printed}
            for name, sector, printed in POOKS
        ],
    )
    op.add_column("seniors", sa.Column("pook_id", sa.Integer(), nullable=True))
    op.create_foreign_key("fk_seniors_pook_id", "seniors", "pooks", ["pook_id"], ["id"])


def downgrade() -> None:
    op.drop_constraint("fk_seniors_pook_id", "seniors", type_="foreignkey")
    op.drop_column("seniors", "pook_id")
    op.drop_index("ix_pooks_barangay", table_name="pooks")
    op.drop_table("pooks")
