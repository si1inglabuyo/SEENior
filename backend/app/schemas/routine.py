from datetime import date, datetime
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


class RoutineHistoryRow(BaseModel):
    """One cell of the fingerprint on one day. The same figures as [RoutineRow] plus the date."""

    day: date
    feature_name: Feature
    time_block: TimeBlock
    median_value: float = Field(ge=0, le=10_000_000)
    mad_value: float = Field(ge=0, le=10_000_000)
    sample_count: int = Field(ge=0, le=1000)
    is_seed: bool
    # True when the phone worked this day out afterwards; false when it saved it that night.
    is_estimate: bool


# 30 days of 20 cells. More than that is not a fingerprint history, so it is refused.
HISTORY_DAYS = 30


class RoutineHistorySync(BaseModel):
    rows: list[RoutineHistoryRow] = Field(max_length=HISTORY_DAYS * 20)


class RoutineHistoryOut(BaseModel):
    rows: list[RoutineHistoryRow]


class RoutineTiming(BaseModel):
    """When each block starts, and when the phone usually saw the senior first/last active.

    The observed pair is a median over up to 14 days and is null until at least 3 days stand
    behind it. `*_days` is how many days that median is built from, so the apps can say so.
    """

    # Minutes after midnight. A bounded int is the only shape accepted, so this carries nothing else.
    morning_start: int = Field(ge=0, le=1439)
    afternoon_start: int = Field(ge=0, le=1439)
    evening_start: int = Field(ge=0, le=1439)
    night_start: int = Field(ge=0, le=1439)
    wake_minute: int | None = Field(default=None, ge=0, le=1439)
    wake_days: int = Field(default=0, ge=0, le=30)
    evening_last_minute: int | None = Field(default=None, ge=0, le=1439)
    evening_last_days: int = Field(default=0, ge=0, le=30)


class RoutineOut(BaseModel):
    rows: list[RoutineRow]
    # When the phone last synced; null when nothing has arrived yet.
    updated_at: datetime | None = None
    # Null until a phone that knows about block times has synced.
    timing: RoutineTiming | None = None
