package com.pup.seenior.network

import com.pup.seenior.database.entities.DailyAggregate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SamplingSyncerTest {

    private val today = LocalDate.of(2026, 11, 16)

    private fun row(date: String, block: String, samples: Int?) = DailyAggregate(
        seniorId = 1, date = date, timeBlock = block, avgMovementScore = 0.0, totalInactivityDuration = 0,
        avgScreenIdleDuration = 0, totalScreenUnlocks = 0, totalSteps = 0, isChargingMajority = false,
        sampleCount = samples
    )

    @Test
    fun `a finished day is counted per block`() {
        val days = SamplingSyncer.build(
            listOf(
                row("2026-11-15", "morning", 60), row("2026-11-15", "afternoon", 55),
                row("2026-11-15", "evening", 50), row("2026-11-15", "night", 120)
            ),
            today
        )
        assertEquals(1, days.size)
        assertEquals(listOf(60, 55, 50, 120), days[0].let { listOf(it.morning, it.afternoon, it.evening, it.night) })
    }

    @Test
    fun `a block with no row collected nothing`() {
        val d = SamplingSyncer.build(listOf(row("2026-11-15", "night", 100)), today).single()
        assertEquals(listOf(0, 0, 0, 100), listOf(d.morning, d.afternoon, d.evening, d.night))
    }

    @Test
    fun `today is left out because its blocks are still filling`() {
        assertTrue(SamplingSyncer.build(listOf(row("2026-11-16", "morning", 10)), today).isEmpty())
    }

    @Test
    fun `a day with an unknown count is skipped, not guessed`() {
        val days = SamplingSyncer.build(
            listOf(row("2026-11-14", "morning", null), row("2026-11-14", "night", 100), row("2026-11-15", "night", 90)),
            today
        )
        assertEquals(listOf("2026-11-15"), days.map { it.day })
    }

    @Test
    fun `only the last thirty days are sent, oldest first`() {
        val days = SamplingSyncer.build(
            listOf(row("2026-09-01", "night", 99), row("2026-11-14", "night", 90), row("2026-11-12", "night", 80)),
            today
        )
        assertEquals(listOf("2026-11-12", "2026-11-14"), days.map { it.day })
    }

    @Test
    fun `the signature changes only when a count changes`() {
        val a = SamplingSyncer.build(listOf(row("2026-11-15", "night", 90)), today)
        val b = SamplingSyncer.build(listOf(row("2026-11-15", "night", 90)), today)
        val c = SamplingSyncer.build(listOf(row("2026-11-15", "night", 91)), today)
        assertEquals(SamplingSyncer.signatureOf(a), SamplingSyncer.signatureOf(b))
        assertTrue(SamplingSyncer.signatureOf(a) != SamplingSyncer.signatureOf(c))
    }
}
