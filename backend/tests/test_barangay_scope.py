"""The demo switch that lets the barangay dashboard show seniors from every barangay.

Off by default: each responder is scoped to their own barangay (spec section 11). Only
BARANGAY_SHOW_ALL_SENIORS=1 widens it.
"""

from app.api.routes import barangay as routes
from app.core.config import settings


def test_scoped_by_default(monkeypatch):
    monkeypatch.setattr(settings, "barangay_show_all_seniors", False)
    assert routes._in_barangay("U.P. Campus", "U.P. Campus")
    assert not routes._in_barangay("Commonwealth", "U.P. Campus")
    assert not routes._in_barangay(None, "U.P. Campus")
    assert "barangay" in str(routes._barangay_filter("U.P. Campus"))


def test_show_all_matches_every_senior(monkeypatch):
    monkeypatch.setattr(settings, "barangay_show_all_seniors", True)
    assert routes._in_barangay("Commonwealth", "U.P. Campus")
    assert routes._in_barangay(None, "U.P. Campus")
    # SQLAlchemy's true() renders as a constant, not a comparison on the barangay column.
    assert "barangay" not in str(routes._barangay_filter("U.P. Campus"))


def test_flag_is_off_unless_explicitly_set():
    # The suite runs without the variable; a default of True would silently widen access.
    assert settings.barangay_show_all_seniors is False
