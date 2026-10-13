package com.pup.seenior.detection

import org.junit.Assert.assertTrue
import org.junit.Test

/** The server only accepts `key=number;key=number`; this keeps the phone's encoding inside that. */
class FallEvidenceSyncTest {
    private val serverPattern = Regex("^[a-z_]+=[0-9.]+(;[a-z_]+=[0-9.]+)*$")

    @Test
    fun `evidence with a gyroscope is in the shape the server accepts`() {
        val note = FallEvidence(freeFallMs = 400, impactPeak = 35f, stillnessMs = 10_500, rotationPeak = 4.5f).encode()
        assertTrue(note, serverPattern.matches(note) && note.length <= 200)
    }

    @Test
    fun `evidence without a gyroscope is in the shape the server accepts`() {
        val note = FallEvidence(freeFallMs = 400, impactPeak = 35f, stillnessMs = 10_500, rotationPeak = null).encode()
        assertTrue(note, serverPattern.matches(note) && note.length <= 200)
    }
}
