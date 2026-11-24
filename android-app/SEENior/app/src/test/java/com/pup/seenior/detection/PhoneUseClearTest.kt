package com.pup.seenior.detection

import com.pup.seenior.alerts.AlertEscalator
import com.pup.seenior.database.entities.Alert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which alerts the senior using their phone may close: routine anomalies only, before family is told. */
class PhoneUseClearTest {

    private fun alert(
        trigger: String,
        status: String = "pending",
        synced: Boolean = false,
        steps: String = "[]"
    ) = Alert(
        seniorId = 1, syncId = "s", triggerType = trigger, riskLevel = "medium",
        timeBlock = "morning", deviationScore = 3.0, triggeredAt = 0L,
        status = status, isSynced = synced, escalationSteps = steps
    )

    @Test
    fun routineAnomaliesAreClearable() {
        listOf("inactivity", "movement", "screen_idle", "charging", "ml_flag", "fall_pattern").forEach {
            assertTrue(it, AlertEscalator.clearableByPhoneUse(alert(it)))
        }
    }

    @Test
    fun sosIsNeverClearable() {
        assertFalse(AlertEscalator.clearableByPhoneUse(alert("sos")))
    }

    @Test
    fun onceFamilyIsToldItIsNotClearable() {
        val told = AlertEscalator.appendStep("[]", "escalated_family", 1L)
        assertFalse(AlertEscalator.clearableByPhoneUse(alert("inactivity", steps = told)))
        assertFalse(AlertEscalator.clearableByPhoneUse(alert("inactivity", synced = true)))
    }

    @Test
    fun onlyPendingAlertsAreClearable() {
        listOf("logged", "self_cancelled", "acknowledged_family", "escalated_barangay", "resolved", "auto_cleared")
            .forEach { assertFalse(it, AlertEscalator.clearableByPhoneUse(alert("inactivity", status = it))) }
    }

    @Test
    fun anAlertThatReachedTheCloudMayBeClosedWhenTheServerAgrees() {
        listOf("pending", "escalated_barangay").forEach {
            assertTrue(it, AlertEscalator.escalatedClearableByPhoneUse(alert("inactivity", status = it, synced = true)))
        }
    }

    @Test
    fun escalatedSosAndUnsyncedAlertsAreNeverAskedAbout() {
        assertFalse(AlertEscalator.escalatedClearableByPhoneUse(alert("sos", synced = true)))
        assertTrue(AlertEscalator.escalatedClearableByPhoneUse(alert("fall_pattern", synced = true)))
        assertFalse(AlertEscalator.escalatedClearableByPhoneUse(alert("inactivity", synced = false)))
    }

    @Test
    fun escalatedClearOnlyForOpenStatuses() {
        listOf("logged", "self_cancelled", "acknowledged_family", "resolved", "auto_cleared", "false_positive")
            .forEach { assertFalse(it, AlertEscalator.escalatedClearableByPhoneUse(alert("inactivity", status = it, synced = true))) }
    }

    @Test
    fun clearedStatusIsOutsideTheFalseAlarmStatuses() {
        // Mirrors AlertDao.getToleratedDeviationScores: only these two feed the tolerance.
        val feedsTolerance = setOf("self_cancelled", "false_positive")
        assertEquals(false, "auto_cleared" in feedsTolerance)
    }

    @Test
    fun aPromptIsDroppedOnlyForAnAlertClosedByPhoneUse() {
        assertTrue(AlertEscalator.closedByPhoneUse(alert("inactivity", status = "auto_cleared")))
        // A self-cancel must keep the prompt up for its "what happened" question.
        assertFalse(AlertEscalator.closedByPhoneUse(alert("inactivity", status = "self_cancelled")))
        assertFalse(AlertEscalator.closedByPhoneUse(alert("inactivity", status = "pending")))
        assertFalse(AlertEscalator.closedByPhoneUse(null))
    }

    @Test
    fun aScreenOnRightAfterTheAlertIsItsOwnPromptNotAPickUp() {
        val a = alert("inactivity")
        assertFalse(AlertEscalator.oldEnough(a, minAgeMs = 5_000L, now = a.triggeredAt + 1_000L))
        assertTrue(AlertEscalator.oldEnough(a, minAgeMs = 5_000L, now = a.triggeredAt + 6_000L))
        // A real unlock (ACTION_USER_PRESENT) has no minimum age.
        assertTrue(AlertEscalator.oldEnough(a, minAgeMs = 0L, now = a.triggeredAt))
    }
}
