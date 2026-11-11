package com.pup.seenior.ui.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecorderCsvTest {

    @Test
    fun `first five columns match phyphox's acceleration export`() {
        val columns = RecorderCsv.HEADER.split(",")
        assertEquals(
            listOf(
                "\"Time (s)\"", "\"Acceleration x (m/s^2)\"", "\"Acceleration y (m/s^2)\"",
                "\"Acceleration z (m/s^2)\"", "\"Absolute acceleration (m/s^2)\""
            ),
            columns.take(5)
        )
        assertEquals(15, columns.size)
        assertEquals("\"Absolute rotation (rad/s)\"", columns.last())
    }

    @Test
    fun `rows use a decimal point and leave the value blank until it exists`() {
        assertEquals(
            "0.020000,0.100000,-0.200000,9.810000,9.812600,,0,,,,,,,,",
            RecorderCsv.row(0.02, 0.1f, -0.2f, 9.81f, 9.8126f, null, 0)
        )
        assertTrue(RecorderCsv.row(1.0, 0f, 0f, 9.8f, 9.8f, 0.25f, 3).contains(",0.250000,3,"))
    }

    @Test
    fun `without-g columns carry the linear acceleration and its absolute value`() {
        val row = RecorderCsv.row(1.0, 0f, 0f, 9.8f, 9.8f, null, 0, floatArrayOf(3f, 0f, 4f))
        assertTrue(row.contains(",3.000000,0.000000,4.000000,5.000000,"))
        assertEquals(15, row.split(",").size)
    }

    @Test
    fun `gyroscope columns carry rotation and its absolute value`() {
        val row = RecorderCsv.row(1.0, 0f, 0f, 9.8f, 9.8f, null, 0, null, floatArrayOf(0f, 0.6f, 0.8f))
        assertTrue(row.endsWith(",0.000000,0.600000,0.800000,1.000000"))
        assertEquals(15, row.split(",").size)
    }

    @Test
    fun `participant code is made safe for a file name`() {
        assertEquals("P01", RecorderCsv.cleanParticipant(" p-01 "))
        assertEquals("ABCDEFGH", RecorderCsv.cleanParticipant("abcdefghij"))
        assertEquals("P01_WALK_R2.csv", RecorderCsv.fileName("p01", "WALK", 2))
    }

    @Test
    fun `repeat number follows the files already saved`() {
        val saved = listOf("P01_WALK_R1.csv", "P01_WALK_R2.csv", "P01_SIT_R1.csv", "P02_WALK_R5.csv")
        assertEquals(3, RecorderCsv.nextRepeat(saved, "P01", "WALK"))
        assertEquals(2, RecorderCsv.nextRepeat(saved, "P01", "SIT"))
        assertEquals(1, RecorderCsv.nextRepeat(saved, "P01", "TABLE"))
    }

    @Test
    fun `activities follow Kwon et al's labels`() {
        assertEquals(listOf("SIT", "WALK", "SLOWWALK", "TABLE", "STAIRS"), RecorderCsv.ACTIVITIES.map { it.code })
    }
}
