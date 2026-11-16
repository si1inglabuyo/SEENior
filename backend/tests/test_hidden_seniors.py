"""HIDDEN_SENIOR_TAG keeps tagged demo seniors off the barangay dashboard, and only the dashboard."""

from app.api.routes import barangay as routes
from app.core.config import settings


def test_nobody_is_hidden_by_default():
    assert settings.hidden_senior_tag == ""
    assert routes._in_barangay("U.P. CAMPUS", "U.P. CAMPUS", "[DEMO] Rosa")


def test_a_tagged_senior_is_refused_even_in_their_own_barangay(monkeypatch):
    monkeypatch.setattr(settings, "hidden_senior_tag", "[DEMO]")
    assert not routes._in_barangay("U.P. CAMPUS", "U.P. CAMPUS", "[DEMO] Rosa")
    assert routes._in_barangay("U.P. CAMPUS", "U.P. CAMPUS", "Rosa")


def test_the_tag_wins_over_the_show_all_switch(monkeypatch):
    monkeypatch.setattr(settings, "hidden_senior_tag", "[DEMO]")
    monkeypatch.setattr(settings, "barangay_show_all_seniors", True)
    assert not routes._in_barangay("Commonwealth", "U.P. CAMPUS", "[DEMO] Rosa")
    assert routes._in_barangay("Commonwealth", "U.P. CAMPUS", "Rosa")


def test_only_the_start_of_the_name_counts(monkeypatch):
    monkeypatch.setattr(settings, "hidden_senior_tag", "[DEMO]")
    assert routes._in_barangay("U.P. CAMPUS", "U.P. CAMPUS", "Rosa [DEMO]")


def test_without_a_first_name_the_old_check_is_unchanged(monkeypatch):
    monkeypatch.setattr(settings, "hidden_senior_tag", "[DEMO]")
    assert routes._in_barangay("U.P. CAMPUS", "U.P. CAMPUS")
    assert not routes._in_barangay("Commonwealth", "U.P. CAMPUS")


def test_the_query_filter_excludes_the_tag_and_stays_scoped(monkeypatch):
    monkeypatch.setattr(settings, "barangay_show_all_seniors", False)
    monkeypatch.setattr(settings, "hidden_senior_tag", "[DEMO]")
    text = str(routes._barangay_filter("U.P. CAMPUS"))
    assert "barangay" in text and "first_name" in text and "NOT" in text.upper()


def test_the_query_filter_is_unchanged_without_the_tag(monkeypatch):
    monkeypatch.setattr(settings, "barangay_show_all_seniors", False)
    monkeypatch.setattr(settings, "hidden_senior_tag", "")
    assert "first_name" not in str(routes._barangay_filter("U.P. CAMPUS"))


def test_every_dashboard_lookup_goes_through_the_filter():
    """A new query that skips both helpers would show a hidden senior; this pins the sites."""
    import inspect

    src = inspect.getsource(routes)
    # Every place the dashboard selects seniors by barangay uses one of the two helpers.
    assert "Senior.barangay ==" not in src.replace("scope = true() if settings.barangay_show_all_seniors else Senior.barangay == barangay", "")
