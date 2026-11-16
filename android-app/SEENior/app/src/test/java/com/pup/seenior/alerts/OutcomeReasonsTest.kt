package com.pup.seenior.alerts

import com.pup.seenior.ui.wellness.OutcomeMessages
import com.pup.seenior.ui.wellness.WellnessMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutcomeReasonsTest {

    private val anomalies = listOf("inactivity", "movement", "screen_idle", "charging", "ml_flag")

    @Test
    fun `a fall offers the fall answers in the agreed order`() {
        assertEquals(listOf("phone_dropped", "fell_fine", "other"), OutcomeReasons.codesFor("fall_pattern"))
    }

    @Test
    fun `every routine anomaly offers the anomaly answers in the agreed order`() {
        anomalies.forEach {
            assertEquals(listOf("unused_fine", "left_behind_fine", "other"), OutcomeReasons.codesFor(it))
        }
    }

    @Test
    fun `an SOS is never asked`() {
        assertTrue(OutcomeReasons.codesFor("sos").isEmpty())
        assertFalse(OutcomeReasons.isAsked("sos"))
        assertNull(OutcomeMessages.forAlert("en", "sos"))
        assertNull(OutcomeMessages.forAlert("fil", "sos"))
    }

    @Test
    fun `a cancelled SOS is the only answer an SOS takes, and it is never offered`() {
        assertTrue(OutcomeReasons.isValid("sos", OutcomeReasons.SOS_CANCELLED))
        assertFalse(OutcomeReasons.isValid("sos", "other"))
        assertFalse(OutcomeReasons.isValid("fall_pattern", OutcomeReasons.SOS_CANCELLED))
        assertFalse(OutcomeReasons.isValid("inactivity", OutcomeReasons.SOS_CANCELLED))
        assertFalse(OutcomeReasons.isAsked("sos"))
    }

    @Test
    fun `a fall refuses an anomaly answer and the reverse`() {
        assertFalse(OutcomeReasons.isValid("fall_pattern", "unused_fine"))
        assertFalse(OutcomeReasons.isValid("inactivity", "phone_dropped"))
        assertTrue(OutcomeReasons.isValid("fall_pattern", "other"))
        assertTrue(OutcomeReasons.isValid("screen_idle", "other"))
    }

    @Test
    fun `free text is never a valid answer`() {
        assertFalse(OutcomeReasons.isValid("fall_pattern", "I dropped it in the kitchen"))
    }

    @Test
    fun `both languages offer the same codes with a label for each`() {
        (anomalies + "fall_pattern").forEach { trigger ->
            val en = OutcomeMessages.forAlert(WellnessMessages.ENGLISH, trigger)
            val fil = OutcomeMessages.forAlert(WellnessMessages.FILIPINO, trigger)
            assertNotNull(en)
            assertNotNull(fil)
            assertEquals(OutcomeReasons.codesFor(trigger), en!!.options.map { it.code })
            assertEquals(en.options.map { it.code }, fil!!.options.map { it.code })
            (en.options + fil.options).forEach { assertTrue(it.label.isNotBlank()) }
            assertTrue(en.question.isNotBlank() && fil.question.isNotBlank())
        }
    }

    private fun closed(trigger: String, outcome: String? = null, status: String = "self_cancelled") =
        com.pup.seenior.database.entities.Alert(
            seniorId = 1, syncId = "s", triggerType = trigger, riskLevel = "high", timeBlock = "morning",
            status = status, outcomeReason = outcome,
            escalationSteps = AlertEscalator.selfCancelledSteps("[]", trigger, 1L)
        )

    @Test
    fun `closing a fall or an anomaly leaves the question owed`() {
        (anomalies + "fall_pattern").forEach {
            assertTrue(it, AlertEscalator.awaitingOutcome(closed(it)))
        }
    }

    @Test
    fun `closing an SOS never owes a question`() {
        assertFalse(AlertEscalator.awaitingOutcome(closed("sos")))
        assertFalse(AlertEscalator.selfCancelledSteps("[]", "sos", 1L).contains("outcome_pending"))
    }

    @Test
    fun `the question stops being owed once answered`() {
        assertFalse(AlertEscalator.awaitingOutcome(closed("fall_pattern", outcome = "phone_dropped")))
    }

    @Test
    fun `an alert that is not closed by the senior owes nothing`() {
        assertFalse(AlertEscalator.awaitingOutcome(closed("fall_pattern", status = "pending")))
        assertFalse(AlertEscalator.awaitingOutcome(closed("fall_pattern", status = "resolved")))
    }

    @Test
    fun `an older closed alert with no marker is never asked`() {
        val old = closed("fall_pattern").copy(escalationSteps = AlertEscalator.appendStep("[]", "self_cancelled", 1L))
        assertFalse(AlertEscalator.awaitingOutcome(old))
    }

    @Test
    fun `the pending marker is the exact text the query looks for`() {
        // AlertDao.observeAwaitingOutcome matches on this quoted name.
        assertTrue(AlertEscalator.selfCancelledSteps("[]", "fall_pattern", 1L).contains("\"outcome_pending\""))
    }

    @Test
    fun `the labels are the agreed wording`() {
        val fall = OutcomeMessages.forAlert("fil", "fall_pattern")!!
        assertEquals(
            listOf("Nalaglag ang phone", "May totoong pagkahulog kanina", "Iba pa"),
            fall.options.map { it.label }
        )
        val anomaly = OutcomeMessages.forAlert("fil", "inactivity")!!
        assertEquals(
            listOf("Matagal hindi nagamit ang phone", "Naiwanan ang phone", "Iba pa"),
            anomaly.options.map { it.label }
        )
        assertEquals("Others", OutcomeMessages.forAlert("en", "fall_pattern")!!.options.last().label)
    }

    @Test
    fun `a typed reason is kept only for others, trimmed and capped`() {
        assertEquals("nag-ayos ng kama", OutcomeReasons.cleanDetail("other", "  nag-ayos   ng kama "))
        assertEquals(null, OutcomeReasons.cleanDetail("other", "   "))
        assertEquals(null, OutcomeReasons.cleanDetail("phone_dropped", "anything"))
        assertEquals(120, OutcomeReasons.cleanDetail("other", "x".repeat(300))!!.length)
    }
}
