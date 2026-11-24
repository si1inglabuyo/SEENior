package com.pup.seenior.ui.routine

import com.pup.seenior.database.entities.Alert
import com.pup.seenior.database.entities.Baseline
import com.pup.seenior.database.entities.BaselineHistory
import com.pup.seenior.database.entities.DailyAggregate
import com.pup.seenior.ui.routine.SeniorRoutineModel.DayState
import com.pup.seenior.ui.routine.SeniorRoutineModel.Figures
import com.pup.seenior.ui.routine.SeniorRoutineModel.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class SeniorRoutineModelTest {

    private val utc: ZoneId = ZoneOffset.UTC

    private fun millis(date: String, hour: Int = 12): Long =
        LocalDate.parse(date).atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun baseline(block: String, feature: String, median: Double, mad: Double = 100.0, seed: Boolean = false, samples: Int = 14) =
        Baseline(seniorId = 1, featureName = feature, timeBlock = block, medianValue = median, madValue = mad, sampleCount = samples, isSeed = seed)

    private fun alert(trigger: String, block: String, z: Double?, status: String, date: String) = Alert(
        seniorId = 1, syncId = "s-$date-$status-$z", triggerType = trigger, riskLevel = "medium",
        timeBlock = block, deviationScore = z, status = status, triggeredAt = millis(date)
    )

    // ------------------------------------------------------------------ verdict

    private val usual = Figures(steps = 1000, unlocks = 8, stillSeconds = 1800, idleSeconds = 6000)

    @Test fun `a day close to the usual reads as usual`() {
        assertEquals(Verdict.AS_USUAL, SeniorRoutineModel.verdict(usual, Figures(900, 7, 2000, 6000), learned = true))
    }

    @Test fun `far fewer steps reads as quieter`() {
        assertEquals(Verdict.QUIETER, SeniorRoutineModel.verdict(usual, Figures(500, 8, 1800, 6000), learned = true))
    }

    @Test fun `a much longer still spell reads as quieter`() {
        assertEquals(Verdict.QUIETER, SeniorRoutineModel.verdict(usual, Figures(1000, 8, 3000, 6000), learned = true))
    }

    @Test fun `many more steps reads as busier`() {
        assertEquals(Verdict.BUSIER, SeniorRoutineModel.verdict(usual, Figures(1800, 8, 1800, 6000), learned = true))
    }

    @Test fun `a block still on the setup guess is never compared`() {
        assertEquals(Verdict.NOT_COMPARED, SeniorRoutineModel.verdict(usual, Figures(10, 0, 9000, 0), learned = false))
    }

    @Test fun `no figures means nothing to compare`() {
        assertEquals(Verdict.NOT_COMPARED, SeniorRoutineModel.verdict(usual, null, learned = true))
        assertEquals(Verdict.NOT_COMPARED, SeniorRoutineModel.verdict(usual, Figures(null, null, null, null), learned = true))
    }

    @Test fun `a tiny usual step count is too small to compare steps`() {
        // Night: ~30 steps usually. 5 steps is not "far fewer".
        val night = Figures(steps = 30, unlocks = 0, stillSeconds = 27000, idleSeconds = 27000)
        assertEquals(Verdict.AS_USUAL, SeniorRoutineModel.verdict(night, Figures(5, 0, 27000, 27000), learned = true))
    }

    // ------------------------------------------------------------------ blocks and day state

    @Test fun `day state follows the open block`() {
        assertEquals(DayState.DONE, SeniorRoutineModel.dayState("morning", "afternoon"))
        assertEquals(DayState.IN_PROGRESS, SeniorRoutineModel.dayState("afternoon", "afternoon"))
        assertEquals(DayState.NOT_YET, SeniorRoutineModel.dayState("evening", "afternoon"))
    }

    @Test fun `windows come back in display order and cover the day`() {
        val w = SeniorRoutineModel.windows("06:00", "22:00")
        assertEquals(listOf("morning", "afternoon", "evening", "night"), w.map { it.block })
        assertEquals(1440, w.sumOf { it.durationMinutes })
    }

    @Test fun `learned needs real days behind every cell of the block`() {
        val rows = listOf(
            baseline("morning", SeniorRoutineModel.STEPS, 1000.0, seed = false),
            baseline("afternoon", SeniorRoutineModel.STEPS, 700.0, seed = true, samples = 2)
        )
        assertTrue(SeniorRoutineModel.isLearned(rows, "morning"))
        assertFalse(SeniorRoutineModel.isLearned(rows, "afternoon"))
        assertFalse(SeniorRoutineModel.isLearned(rows, "evening"))
    }

    // ------------------------------------------------------------------ usual times

    private fun aggregate(date: String, block: String, first: Int?, last: Int?) = DailyAggregate(
        seniorId = 1, date = date, timeBlock = block, avgMovementScore = 0.1, totalInactivityDuration = 0,
        avgScreenIdleDuration = 0, totalScreenUnlocks = 0, totalSteps = 0, isChargingMajority = false,
        firstActiveMinute = first, lastActiveMinute = last
    )

    @Test fun `usual times are withheld below three days`() {
        val today = LocalDate.parse("2026-11-23")
        val two = listOf(aggregate("2026-11-21", "morning", 390, 700), aggregate("2026-11-22", "morning", 400, 700))
        assertNull(SeniorRoutineModel.usualTimes(two, today).getValue("morning").firstMinute)
    }

    @Test fun `usual times are the median once three days stand behind them`() {
        val today = LocalDate.parse("2026-11-23")
        val rows = listOf(
            aggregate("2026-11-20", "morning", 380, null),
            aggregate("2026-11-21", "morning", 390, null),
            aggregate("2026-11-22", "morning", 420, null)
        )
        assertEquals(390, SeniorRoutineModel.usualTimes(rows, today).getValue("morning").firstMinute)
    }

    // ------------------------------------------------------------------ what the phone learned

    private fun history(date: String, block: String, feature: String, median: Double, seed: Boolean = false, estimate: Boolean = false) =
        BaselineHistory(seniorId = 1, date = date, featureName = feature, timeBlock = block, medianValue = median,
            madValue = 50.0, sampleCount = 10, isSeed = seed, isEstimate = estimate)

    @Test fun `a learned figure that moved a lot is reported`() {
        val today = LocalDate.parse("2026-11-23")
        val hist = listOf(history("2026-11-17", "afternoon", SeniorRoutineModel.STEPS, 900.0, estimate = true))
        val now = listOf(baseline("afternoon", SeniorRoutineModel.STEPS, 700.0))
        val result = SeniorRoutineModel.changes(hist, now, today)
        assertEquals(1, result.items.size)
        assertEquals(900.0, result.items[0].from, 0.0)
        assertEquals(700.0, result.items[0].to, 0.0)
        assertTrue(result.includesEstimates)
    }

    @Test fun `small moves and setup guesses are not reported`() {
        val today = LocalDate.parse("2026-11-23")
        val hist = listOf(
            history("2026-11-17", "morning", SeniorRoutineModel.STEPS, 1000.0),
            history("2026-11-17", "evening", SeniorRoutineModel.STEPS, 100.0, seed = true)
        )
        val now = listOf(
            baseline("morning", SeniorRoutineModel.STEPS, 1050.0),
            baseline("evening", SeniorRoutineModel.STEPS, 800.0)
        )
        assertTrue(SeniorRoutineModel.changes(hist, now, today).items.isEmpty())
    }

    @Test fun `moved needs both a relative and an absolute change`() {
        assertFalse(SeniorRoutineModel.moved(SeniorRoutineModel.STEPS, 100.0, 150.0)) // 50% but only 50 steps
        assertFalse(SeniorRoutineModel.moved(SeniorRoutineModel.STEPS, 5000.0, 5200.0)) // 200 steps but 4%
        assertTrue(SeniorRoutineModel.moved(SeniorRoutineModel.STEPS, 900.0, 700.0))
        assertTrue(SeniorRoutineModel.moved(SeniorRoutineModel.STILL, 1500.0, 2400.0)) // +15 min, +60%
        assertFalse(SeniorRoutineModel.moved(SeniorRoutineModel.STILL, 1500.0, 1700.0)) // +3 min
    }

    // ------------------------------------------------------------------ how careful alerts are

    private val now = millis("2026-11-23")

    @Test fun `two false alarms in one block loosen the trigger and say when it ends`() {
        val alerts = listOf(
            alert("inactivity", "afternoon", 2.6, "self_cancelled", "2026-11-19"),
            alert("inactivity", "afternoon", 2.7, "self_cancelled", "2026-11-21")
        )
        val base = listOf(baseline("afternoon", SeniorRoutineModel.STILL, 1800.0, mad = 1200.0))
        val care = SeniorRoutineModel.care(alerts, base, now, utc)
        assertEquals(1, care.size)
        val c = care.single()
        assertEquals("afternoon", c.block)
        assertEquals(listOf(LocalDate.parse("2026-11-19"), LocalDate.parse("2026-11-21")), c.answeredOn)
        assertEquals(LocalDate.parse("2026-12-03"), c.backToNormal) // 14 days after the older one
        // gate = 2.65 + 0.25; raise 0.4; 0.4 * 1200 / 0.6745 / 60 is about 12 minutes
        assertEquals(12, c.extraMinutes)
    }

    @Test fun `one false alarm changes nothing`() {
        val alerts = listOf(alert("inactivity", "afternoon", 2.6, "self_cancelled", "2026-11-21"))
        assertTrue(SeniorRoutineModel.care(alerts, emptyList(), now, utc).isEmpty())
    }

    @Test fun `extreme false alarms are discarded and cannot loosen anything`() {
        val alerts = listOf(
            alert("inactivity", "afternoon", 3.6, "self_cancelled", "2026-11-19"),
            alert("inactivity", "afternoon", 4.0, "self_cancelled", "2026-11-21")
        )
        assertTrue(SeniorRoutineModel.care(alerts, emptyList(), now, utc).isEmpty())
    }

    @Test fun `a real alert in the same hours takes the slack back`() {
        val alerts = listOf(
            alert("inactivity", "afternoon", 2.6, "self_cancelled", "2026-11-19"),
            alert("inactivity", "afternoon", 2.7, "self_cancelled", "2026-11-21"),
            alert("inactivity", "afternoon", 2.8, "acknowledged_family", "2026-11-22"),
            alert("inactivity", "afternoon", 2.9, "resolved", "2026-11-22")
        )
        assertTrue(SeniorRoutineModel.care(alerts, emptyList(), now, utc).isEmpty())
    }

    @Test fun `old false alarms and phone-use auto clears do not count`() {
        val alerts = listOf(
            alert("inactivity", "afternoon", 2.6, "self_cancelled", "2026-11-02"), // outside 14 days
            alert("inactivity", "afternoon", 2.7, "auto_cleared", "2026-11-20"),
            alert("inactivity", "afternoon", 2.7, "auto_cleared", "2026-11-21")
        )
        assertTrue(SeniorRoutineModel.care(alerts, emptyList(), now, utc).isEmpty())
    }

    @Test fun `false alarms in another block or signal are kept apart`() {
        val alerts = listOf(
            alert("inactivity", "afternoon", 2.6, "self_cancelled", "2026-11-19"),
            alert("inactivity", "morning", 2.7, "self_cancelled", "2026-11-21")
        )
        assertTrue(SeniorRoutineModel.care(alerts, emptyList(), now, utc).isEmpty())
    }

    @Test fun `a movement signal has no minutes to show`() {
        val alerts = listOf(
            alert("movement", "evening", 2.6, "false_positive", "2026-11-19"),
            alert("movement", "evening", 2.7, "self_cancelled", "2026-11-21")
        )
        val c = SeniorRoutineModel.care(alerts, emptyList(), now, utc).single()
        assertNull(c.extraMinutes)
    }

    @Test fun `the care card keeps the working behind its minutes`() {
        val alerts = listOf(
            alert("inactivity", "afternoon", 2.6, "self_cancelled", "2026-11-19"),
            alert("inactivity", "afternoon", 2.7, "self_cancelled", "2026-11-21")
        )
        val base = listOf(baseline("afternoon", SeniorRoutineModel.STILL, 1800.0, mad = 1200.0))
        val c = SeniorRoutineModel.care(alerts, base, now, utc).single()
        assertEquals(listOf(2.6, 2.7), c.scores)
        assertEquals(1200.0, c.spreadSeconds!!, 0.0001)
        val b = c.breakdown!!
        assertEquals(2.65, b.medianZ!!, 0.0001)
        assertEquals(2.90, b.gateBeforeRealAlerts, 0.0001)
        assertEquals(0, b.realAlertCount)
        // The working must land on the same minutes the card shows.
        assertEquals(c.extraMinutes, Math.round(b.extraMinutes(c.spreadSeconds!!)).toInt())
    }

    @Test fun `a part of the day is quieter busier or different`() {
        fun ch(feature: String, from: Double, to: Double) = SeniorRoutineModel.Change("morning", feature, from, to)
        assertEquals(SeniorRoutineModel.Mood.QUIETER, SeniorRoutineModel.mood(listOf(ch(SeniorRoutineModel.STEPS, 3100.0, 2400.0))))
        assertEquals(SeniorRoutineModel.Mood.BUSIER, SeniorRoutineModel.mood(listOf(ch(SeniorRoutineModel.STEPS, 2400.0, 3100.0))))
        assertEquals(SeniorRoutineModel.Mood.QUIETER, SeniorRoutineModel.mood(listOf(ch(SeniorRoutineModel.STILL, 3600.0, 4800.0))))
        assertEquals(SeniorRoutineModel.Mood.BUSIER, SeniorRoutineModel.mood(listOf(ch(SeniorRoutineModel.UNLOCKS, 10.0, 20.0))))
    }

    // ------------------------------------------------------------------ the line on the care card

    private fun care(vararg dates: String) = SeniorRoutineModel.Care(
        block = "afternoon", trigger = "inactivity",
        answeredOn = dates.map { LocalDate.parse(it) },
        extraMinutes = 12, backToNormal = LocalDate.parse("2026-12-03")
    )

    @Test fun `the line is the last fourteen days with today on the right edge`() {
        val today = LocalDate.parse("2026-11-23")
        val strip = SeniorRoutineModel.strip(care("2026-11-19", "2026-11-21"), today)
        assertEquals(13, strip.days) // 14 cells, 13 gaps
        assertEquals(9f / 13f, strip.answered[0], 0.001f)  // Nov 19 is the 10th day of Nov 10..23
        assertEquals(11f / 13f, strip.answered[1], 0.001f)
    }

    @Test fun `an answer today sits on the right edge and the oldest on the left`() {
        val today = LocalDate.parse("2026-11-23")
        val strip = SeniorRoutineModel.strip(care("2026-11-10", "2026-11-23"), today)
        assertEquals(0f, strip.answered[0], 0f)
        assertEquals(1f, strip.answered[1], 0f)
    }

    @Test fun `an answer just outside the line is held on its left edge`() {
        val today = LocalDate.parse("2026-11-23")
        val strip = SeniorRoutineModel.strip(care("2026-11-09", "2026-11-12"), today)
        assertEquals(0f, strip.answered[0], 0f)
    }

    @Test fun `the minutes chip falls back to words when there is no time to give`() {
        assertEquals("+12 min", SeniorRoutineText.careChip(12, fil = false))
        assertEquals("asks a bit later", SeniorRoutineText.careChip(null, fil = false))
        assertEquals("asks a bit later", SeniorRoutineText.careChip(0, fil = false))
        assertEquals("mas huli magtanong", SeniorRoutineText.careChip(null, fil = true))
    }

    @Test fun `short dates carry no weekday`() {
        assertEquals("Nov 19", SeniorRoutineText.shortDate(LocalDate.parse("2026-11-19"), fil = false))
        assertEquals("Dec 3", SeniorRoutineText.shortDate(LocalDate.parse("2026-12-03"), fil = false))
    }

    // ------------------------------------------------------------------ wording

    @Test fun `clock reads like a phone clock`() {
        assertEquals("6:30 AM", SeniorRoutineText.clock(390))
        assertEquals("12:00 PM", SeniorRoutineText.clock(720))
        assertEquals("12:00 AM", SeniorRoutineText.clock(0))
        assertEquals("8:30 PM", SeniorRoutineText.clock(20 * 60 + 30))
    }

    @Test fun `a block that wraps midnight still gets an end time`() {
        val night = SeniorRoutineModel.windows("06:00", "22:00").last()
        assertEquals("10:00 PM - 6:00 AM", SeniorRoutineText.hours(night))
    }

    @Test fun `headline says still learning for a guessed block`() {
        val times = SeniorRoutineModel.Times(390, 1230)
        assertEquals("Still learning this part of your day.",
            SeniorRoutineText.usualHeadline("afternoon", times, learned = false, fil = false))
        assertEquals("Usually up by about 6:30 AM",
            SeniorRoutineText.usualHeadline("morning", times, learned = true, fil = false))
        assertEquals("Usually last active about 8:30 PM",
            SeniorRoutineText.usualHeadline("evening", times, learned = true, fil = false))
    }

    @Test fun `every wording function has a Filipino version that differs from English`() {
        assertTrue(SeniorRoutineText.learnedHeading(true) != SeniorRoutineText.learnedHeading(false))
        assertTrue(SeniorRoutineText.careNote(true) != SeniorRoutineText.careNote(false))
        assertTrue(SeniorRoutineText.labelStill(true) != SeniorRoutineText.labelStill(false))
        assertTrue(SeniorRoutineText.verdict(Verdict.QUIETER, false, true) != SeniorRoutineText.verdict(Verdict.QUIETER, false, false))
    }
}
