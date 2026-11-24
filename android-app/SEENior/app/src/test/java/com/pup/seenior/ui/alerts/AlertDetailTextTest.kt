package com.pup.seenior.ui.alerts

import com.pup.seenior.database.entities.Alert
import com.pup.seenior.ui.routine.TagKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertDetailTextTest {

    private fun alert(status: String, steps: String = "[]") = Alert(
        seniorId = 1, syncId = "x", triggerType = "inactivity", riskLevel = "medium",
        timeBlock = "afternoon", status = status, escalationSteps = steps
    )

    @Test fun `a senior's own all-clear and a false alarm read as good news`() {
        assertEquals(TagKind.OK, outcomeKind("self_cancelled"))
        assertEquals(TagKind.OK, outcomeKind("false_positive"))
    }

    @Test fun `phone-use clears are kept apart from false alarms`() {
        assertEquals(TagKind.WAIT, outcomeKind("auto_cleared"))
    }

    @Test fun `an alert that went past the senior is flagged as such`() {
        assertEquals(TagKind.SOFT, outcomeKind("pending"))
        assertEquals(TagKind.SOFT, outcomeKind("acknowledged_family"))
        assertEquals(TagKind.SOFT, outcomeKind("escalated_barangay"))
    }

    @Test fun `family told is read from the timeline or the status`() {
        assertFalse(familyWasTold(alert("self_cancelled")))
        assertTrue(familyWasTold(alert("pending", """[{"step":"escalated_family","at":"x"}]""")))
        assertTrue(familyWasTold(alert("acknowledged_family")))
    }

    @Test fun `the what-it-changed line names the block and the count in both languages`() {
        val en = AlertDetailText.changedText(2, "afternoon", fil = false)
        assertTrue(en.contains("2") && en.contains("afternoon"))
        val fil = AlertDetailText.changedText(2, "afternoon", fil = true)
        assertTrue(fil.contains("2") && fil != en)
    }

    @Test fun `every label has a Filipino version that differs from English`() {
        assertTrue(AlertDetailText.whyHeading(true) != AlertDetailText.whyHeading(false))
        assertTrue(AlertDetailText.happenedHeading(true) != AlertDetailText.happenedHeading(false))
        assertTrue(AlertDetailText.familyValue(true, true) != AlertDetailText.familyValue(true, false))
        assertTrue(AlertDetailText.familyValue(false, true) != AlertDetailText.familyValue(false, false))
    }
}
