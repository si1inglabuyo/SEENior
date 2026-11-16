"""How many 5-minute readings a phone collected each day (migration 0028).

Counts per time block, never a reading and never anything about what the senior did: only whether
the phone was collecting. About 288 readings a day when it runs all day.
"""

from datetime import date

from pydantic import BaseModel, Field

# What the cloud keeps. Older days are trimmed on every upload.
SAMPLING_DAYS = 30

# A block can hold at most one reading per 5 minutes: 288 for a whole day. The bound is generous
# (a phone that samples faster than designed is still plausible) but stops a nonsense number.
MAX_PER_BLOCK = 600


class SamplingDayIn(BaseModel):
    day: date
    morning: int = Field(ge=0, le=MAX_PER_BLOCK)
    afternoon: int = Field(ge=0, le=MAX_PER_BLOCK)
    evening: int = Field(ge=0, le=MAX_PER_BLOCK)
    night: int = Field(ge=0, le=MAX_PER_BLOCK)


class SamplingSync(BaseModel):
    days: list[SamplingDayIn] = Field(max_length=SAMPLING_DAYS + 2)


class SamplingOut(BaseModel):
    stored: int
