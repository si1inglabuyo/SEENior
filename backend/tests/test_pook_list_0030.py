"""The November 2026 pook roster (migration 0030) and the two outside-help services."""

import importlib.util
from pathlib import Path

from app.core import after_hours


def _migration():
    path = Path(__file__).parent.parent / "migrations" / "versions" / "0030_pook_list_2026_11.py"
    spec = importlib.util.spec_from_file_location("m0030", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_chains_after_0029():
    m = _migration()
    assert (m.revision, m.down_revision) == ("0030", "0029")


def test_eighteen_distinct_pooks_each_with_a_responder_and_placeholder_phone():
    m = _migration()
    names = [row[1] for row in m.ROWS]
    assert len(m.ROWS) == 18
    assert len(set(names)) == 18
    assert {row[2] for row in m.ROWS} <= {"north", "south"}
    phones = [m._placeholder_phone(n) for n in range(1, len(m.ROWS) + 1)]
    assert len(set(phones)) == 18
    assert all(p.startswith("0900000") and len(p) == 11 for p in phones)
    assert all(row[4] for row in m.ROWS)


def test_the_two_outside_help_services_are_not_pooks():
    names = {row[1].lower() for row in _migration().ROWS}
    assert not any("ginintuang" in n or "bupsca" in n for n in names)


def test_no_new_name_collides_with_another_rows_old_name():
    rows = _migration().ROWS
    for i, (_, new, *_rest) in enumerate(rows):
        for j, (old, *_r) in enumerate(rows):
            if i != j:
                assert new != old, (new, old)


def test_dropped_rows_are_not_also_renamed():
    m = _migration()
    assert not set(m.DROPPED) & {row[0] for row in m.ROWS if row[0]}


def test_outside_help_is_listed_but_kept_off_the_senior_hotlines():
    outside = {h.name for h in after_hours.OUTSIDE_HELP}
    assert outside == {"Ginintuang Samahan", "BUPSCA"}
    # Phones show `hotlines` to a senior with tap-to-call; a placeholder must never be there.
    assert outside.isdisjoint({h.name for h in after_hours.HOTLINES})
    for h in after_hours.OUTSIDE_HELP:
        assert all(n.dial.startswith("0900000") for n in h.numbers)
