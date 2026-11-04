from pydantic import BaseModel


class NumberOut(BaseModel):
    display: str
    dial: str


class HotlineOut(BaseModel):
    name: str
    numbers: list[NumberOut]


class HallHoursOut(BaseModel):
    timezone: str
    open_hour: int
    close_hour: int
    # 0 = Monday ... 6 = Sunday.
    open_weekdays: list[int]


class AfterHoursOut(BaseModel):
    version: int
    hall: HallHoursOut
    hotlines: list[HotlineOut]
