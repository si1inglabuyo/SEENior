"""The after-hours settings the apps fetch and also bundle as defaults."""

import re

from fastapi.testclient import TestClient

from app.core import after_hours
from app.main import app


def test_endpoint_needs_no_login_and_lists_every_hotline():
    out = TestClient(app).get("/config/after-hours")
    assert out.status_code == 200
    body = out.json()
    assert [h["name"] for h in body["hotlines"]] == [h.name for h in after_hours.HOTLINES]
    assert body["hall"] == {"timezone": "Asia/Manila", "open_hour": 8, "close_hour": 17, "open_weekdays": [0, 1, 2, 3, 4]}


def test_national_hotline_is_first():
    assert after_hours.HOTLINES[0].numbers[0].dial == "911"


def test_every_dial_string_is_digits_only_and_nonempty():
    for hotline in after_hours.HOTLINES:
        assert hotline.numbers, hotline.name
        for number in hotline.numbers:
            assert re.fullmatch(r"\d{3,11}", number.dial), (hotline.name, number.dial)
            assert number.display.strip()


def test_landlines_carry_the_area_code_a_mobile_needs():
    # Eight-digit Metro Manila landlines are dialled with the 02 prefix (ten digits in all).
    for hotline in after_hours.HOTLINES:
        for number in hotline.numbers:
            if len(number.dial) == 10:
                assert number.dial.startswith("02"), (hotline.name, number.dial)
