"""The helper that makes a bcrypt hash for a new barangay password refuses weak ones."""

import pytest

from app.core.security import verify_password
from app.scripts import hash_password as tool


def test_the_old_password_is_refused():
    assert tool.check_new_password("password1234") is not None


@pytest.mark.parametrize("weak", ["short1!", "123456789012345", "abcdefghijklmnop", "Password1234"[:11]])
def test_short_or_one_kind_passwords_are_refused(weak):
    assert tool.check_new_password(weak) is not None


def test_a_password_over_72_bytes_is_refused():
    assert tool.check_new_password("aB3!" * 20) is not None


def test_a_reasonable_password_passes():
    assert tool.check_new_password("Brgy-UP#Campus-2026!") is None


def test_the_hash_it_makes_verifies_against_the_password():
    from app.core.security import hash_password

    assert verify_password("Brgy-UP#Campus-2026!", hash_password("Brgy-UP#Campus-2026!"))
    assert not verify_password("password1234", hash_password("Brgy-UP#Campus-2026!"))
