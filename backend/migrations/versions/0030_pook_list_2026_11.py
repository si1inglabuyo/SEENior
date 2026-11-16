"""pook list from the barangay's November 2026 roster (18 pooks), with each pook's responder

The hall gave the list of pooks and a person for each. This replaces the sixteen seeded in 0016:

* Rows are renamed IN PLACE where the area is the same, so a senior who already picked one keeps
  it (seniors.pook_id points at the row id).
* Four pooks are new (Sikatuna Bliss 2, ACSPOL - Libis, Marilag, Malinis).
* Two old rows have no counterpart on the new list (Sumakwel (Area 2), Libis (Annex)). Any senior
  on them is set to no pook, which is always allowed, and the row is deleted.
* Ginintuang Samahan and BUPSCA are on the roster but are NOT pooks, so they are not here. They
  are outside help (see app/core/after_hours.py).

Responder names are the people on the list. The phone numbers are PLACEHOLDERS (0900000nnnn,
not real subscriber numbers) until the hall supplies them. A label for the desk only: escalation
never reads any of this.

SECTOR is a guess for Marilag, Malinis, Libis SSCPL, ACSPOL - Libis and Sikatuna Bliss 2: the
roster does not say which side of the Sunken Garden they are on. Correct the `sector` column
(north / south) once the hall confirms; it only decides how the dashboard groups them.

Revision ID: 0030
Revises: 0029
Create Date: 2026-11-16
"""

from alembic import op
import sqlalchemy as sa

revision = "0030"
down_revision = "0029"
branch_labels = None
depends_on = None

BARANGAY = "U.P. Campus"

# (old name or None for a new row, new name, sector, printed name, responder)
ROWS = [
    ("Sikatuna Bliss", "Sikatuna Bliss 1", "south", "Pook Sikatuna Bliss 1", "Oscar V. Andrada"),
    ("Amorsolo", "Amorsolo", "south", "Pook Fernando Amorsolo", "Carmelita A. Tobias"),
    ("Hernandez - Hilaga", "Village B", "south", "Pook Village B (U.P. Employees Village B)", "Ricardo L. Moralejo"),
    ("Dagohoy", "Dagohoy", "north", "Pook Francisco Dagohoy", "Juanita DC Canlas"),
    ("Carlos P. Garcia", "C.P. Garcia", "south", "Pook Carlos P. Garcia", "Annie F. Condino"),
    ("Libis", "Libis SSCPL", "south", "Pook Libis SSCPL", "Ramir B. Mariano"),
    ("Arboretum", "Arboretum", "north", "Pook Arboretum", "Dominador M Bautista"),
    (None, "ACSPOL - Libis", "south", "Pook ACSPOL - Libis", "Ramon S. Oximas"),
    ("Ricarte", "Ricarte", "north", "Pook Artemio Ricarte", "Paul John C. Alix"),
    ("Daang Tubo", "Daang Tubo", "south", "Pook Daang Tubo", "Hamilcar S. Maslog"),
    ("Area 17", "Area 17", "south", "Area 17", "Elvigia Bawa"),
    ("Hernandez - Timog", "Village A", "south", "Pook Village A (U.P. Employees Village A)", "Manuel dela Paz"),
    ("Sumakwel (Area 1/3)", "Area 1-3", "north", "Area 1-3", "Violeta Delos Reyes"),
    (None, "Sikatuna Bliss 2", "south", "Pook Sikatuna Bliss 2", "Estelita Jimenez"),
    (None, "Marilag", "south", "Pook Marilag", "Purisima Canlas"),
    (None, "Malinis", "south", "Pook Malinis", "Adelina Bantayan"),
    ("Palaris", "Palaris", "north", "Pook Palaris", "Eduardo Garcia"),
    ("Aguinaldo", "Aguinaldo", "south", "Pook Gen. Emilio Aguinaldo", "Lorna Lunas"),
]

# Old rows with no place on the new list.
DROPPED = ["Sumakwel (Area 2)", "Libis (Annex)"]


def _placeholder_phone(n: int) -> str:
    return f"0900000{n:04d}"


def upgrade() -> None:
    conn = op.get_bind()

    # 1. Free the old rows nobody keeps. A senior on one simply has no pook.
    for old in DROPPED:
        row = conn.execute(
            sa.text("SELECT id FROM pooks WHERE barangay = :b AND name = :n"), {"b": BARANGAY, "n": old}
        ).fetchone()
        if row is None:
            continue
        conn.execute(sa.text("UPDATE seniors SET pook_id = NULL WHERE pook_id = :id"), {"id": row[0]})
        conn.execute(sa.text("DELETE FROM pooks WHERE id = :id"), {"id": row[0]})

    # 2. Rename in place, or insert. Each row is matched on its OLD name, and no new name equals
    #    another row's old name, so renaming one cannot capture another.
    for n, (old, name, sector, printed, responder) in enumerate(ROWS, start=1):
        params = {
            "b": BARANGAY, "old": old, "name": name, "sector": sector, "printed": printed,
            "who": responder[:64], "phone": _placeholder_phone(n),
        }
        found = None
        if old is not None:
            found = conn.execute(
                sa.text("SELECT id FROM pooks WHERE barangay = :b AND name = :old"), params
            ).fetchone()
        if found is not None:
            params["id"] = found[0]
            conn.execute(
                sa.text(
                    "UPDATE pooks SET name = :name, sector = :sector, description = :printed, "
                    "responder_name = :who, responder_phone = :phone WHERE id = :id"
                ),
                params,
            )
        else:
            conn.execute(
                sa.text(
                    "INSERT INTO pooks (barangay, name, sector, description, responder_name, responder_phone) "
                    "VALUES (:b, :name, :sector, :printed, :who, :phone)"
                ),
                params,
            )


def downgrade() -> None:
    # The sixteen-pook list of 0016 is not rebuilt: names were changed in place and two rows were
    # deleted. Restoring would mean guessing which seniors were on which row.
    raise NotImplementedError("0030 changes pook names in place and cannot be undone automatically")
