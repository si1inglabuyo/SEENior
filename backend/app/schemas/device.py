from datetime import datetime

from pydantic import BaseModel, Field


class DeviceTokenRegister(BaseModel):
    # FCM token format isn't contractual, so only check that something plausible arrived.
    token: str = Field(min_length=32, max_length=255)
    platform: str = Field(default="android", max_length=16)


class DeviceTokenOut(BaseModel):
    id: int
    platform: str
    created_at: datetime
    last_seen_at: datetime

    # The token is not returned: it is a delivery credential and shouldn't leak.
    model_config = {"from_attributes": True}
