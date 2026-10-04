from typing import Literal

from pydantic import BaseModel, EmailStr, Field

from app.db.models import UserRole

# Matches the 6-character minimum Firebase Auth already enforces on the family app.
MIN_PASSWORD_LENGTH = 6


class Token(BaseModel):
    access_token: str
    token_type: str = "bearer"


class UserOut(BaseModel):
    id: int
    username: str
    role: UserRole
    barangay: str | None = None
    full_name: str | None = None
    phone: str | None = None
    email: str | None = None
    # False for Google-only accounts; the app then shows "Set a password".
    has_password: bool = False
    # "en" / "fil"; drives the family app's language toggle.
    language_preference: str = "en"

    model_config = {"from_attributes": True}


class UserUpdate(BaseModel):
    """Editable account details — the family app's Edit Profile screen."""
    full_name: str
    phone: str


class PasswordChangeRequest(BaseModel):
    current_password: str
    new_password: str = Field(min_length=MIN_PASSWORD_LENGTH)


class PasswordSetRequest(BaseModel):
    """Add a password to a Google-only account. No current password is needed."""
    new_password: str = Field(min_length=MIN_PASSWORD_LENGTH)


class RegisterRequest(BaseModel):
    """The family app's Sign Up: creates an email/password account before pairing."""
    full_name: str
    phone: str
    email: EmailStr
    password: str = Field(min_length=MIN_PASSWORD_LENGTH)


class GoogleSignInRequest(BaseModel):
    id_token: str


class FirebaseSignInRequest(BaseModel):
    id_token: str
    # True only from the dedicated Sign Up screen. Default False keeps the existing
    # "same email, different sign-in method" merge.
    is_sign_up: bool = False


class LanguagePreferenceUpdate(BaseModel):
    """Family app's language toggle. Same codes as the senior side's language preference."""

    language: Literal["en", "fil"]


class AccountDeletionRequest(BaseModel):
    """Why an account is being deleted: a stable `reason` code plus an optional note."""

    reason: str = Field(min_length=1, max_length=64)
    note: str | None = Field(default=None, max_length=500)
