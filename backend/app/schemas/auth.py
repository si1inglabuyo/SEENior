from pydantic import BaseModel, EmailStr

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

    model_config = {"from_attributes": True}


class UserUpdate(BaseModel):
    """Editable account details — the family app's Edit Profile screen."""
    full_name: str
    phone: str


class PasswordChangeRequest(BaseModel):
    current_password: str
    new_password: str


class RegisterRequest(BaseModel):
    """The family app's Sign Up screen — email/password account creation,
    separate from and prior to pairing with any senior."""
    full_name: str
    phone: str
    email: EmailStr
    password: str


class GoogleSignInRequest(BaseModel):
    id_token: str
