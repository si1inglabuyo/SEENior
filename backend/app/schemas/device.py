from datetime import datetime

from pydantic import BaseModel, Field


class DeviceTokenRegister(BaseModel):
    # FCM tokens are long and their format is not contractual, so this validates only
    # that something plausible arrived rather than pattern-matching a shape Google is
    # free to change.
    token: str = Field(min_length=32, max_length=255)
    platform: str = Field(default="android", max_length=16)


class DeviceTokenOut(BaseModel):
    id: int
    platform: str
    created_at: datetime
    last_seen_at: datetime

    # The token itself is deliberately NOT returned. It is a delivery credential for a
    # specific device, and echoing it back serves no client need while widening where it
    # can leak (logs, proxies, crash reports).
    model_config = {"from_attributes": True}
