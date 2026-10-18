from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field

# The five features and four blocks the phone's fingerprint is made of. Anything else is
# rejected, so this endpoint cannot be used to park arbitrary data in the cloud.
Feature = Literal[
    "inactivity_duration",
    "movement_score",
    "screen_idle_duration",
    "screen_unlock_count",
    "step_count",
]
TimeBlock = Literal["morning", "afternoon", "evening", "night"]


class RoutineRow(BaseModel):
    feature_name: Feature
    time_block: TimeBlock
    # Finite, bounded numbers only: a statistic can't be larger than a day of seconds.
    median_value: float = Field(ge=0, le=10_000_000)
    mad_value: float = Field(ge=0, le=10_000_000)
    sample_count: int = Field(ge=0, le=1000)
    is_seed: bool


class RoutineSync(BaseModel):
    rows: list[RoutineRow] = Field(max_length=20)


class RoutineOut(BaseModel):
    rows: list[RoutineRow]
    # When the phone last synced; null when nothing has arrived yet.
    updated_at: datetime | None = None
