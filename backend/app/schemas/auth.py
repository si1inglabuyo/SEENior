from pydantic import BaseModel, EmailStr, Field

from app.db.models import UserRole


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
    # False for a Google-only account — the family app shows "Set a password" rather than
    # "Change password" (read off User.has_password).
    has_password: bool = False

    model_config = {"from_attributes": True}


class UserUpdate(BaseModel):
    """Editable account details — the family app's Edit Profile screen."""
    full_name: str
    phone: str


class PasswordChangeRequest(BaseModel):
    current_password: str
    new_password: str


class PasswordSetRequest(BaseModel):
    """Add a password to a Google-only account so email + password sign-in also works.
    No current password: there isn't one."""
    new_password: str = Field(min_length=4)


class RegisterRequest(BaseModel):
    """The family app's Sign Up screen — email/password account creation,
    separate from and prior to pairing with any senior."""
    full_name: str
    phone: str
    email: EmailStr
    password: str


class GoogleSignInRequest(BaseModel):
    id_token: str


class AccountDeletionRequest(BaseModel):
    """Why an account is being deleted — shown to the user as a required reason
    picker (`reason` is a stable code, not the localized label) plus an optional
    free-text note."""

    reason: str = Field(min_length=1, max_length=64)
    note: str | None = Field(default=None, max_length=500)
