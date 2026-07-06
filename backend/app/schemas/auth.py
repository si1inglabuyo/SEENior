from pydantic import BaseModel

from app.db.models import UserRole


class Token(BaseModel):
    access_token: str
    token_type: str = "bearer"


class UserOut(BaseModel):
    id: int
    username: str
    role: UserRole
    barangay: str | None = None

    model_config = {"from_attributes": True}
