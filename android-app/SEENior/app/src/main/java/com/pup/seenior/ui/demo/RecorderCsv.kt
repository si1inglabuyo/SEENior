package com.pup.seenior.ui.demo

import java.util.Locale

/**
 * File format for the walk-test recorder.
 *
 * The first five columns copy phyphox's "Acceleration (with g)" CSV export (Staacks et al.,
 * 2018), so the walk-test spreadsheet reads a SEENior recording and a phyphox recording the same
 * way. SEENior-only columns follow: the app's own 2 s pre_dev figure ([MotionWindow]), the steps
 * detected since the recording started, and the acceleration without gravity (Android's linear
 * acceleration sensor, the same quantity as phyphox's "Acceleration (without g)"), taken as the
 * latest linear reading at each accelerometer reading, and the gyroscope (rotation speed, used by
 * the fall detector's rotation gate), taken the same way. Blank on phones without that sensor.
 *
 * No Android imports, so JUnit can drive it.
 */
object RecorderCsv {

    /** Activity codes, following the labels of Kwon et al. (2017), and the band each should land in. */
    data class Activity(val code: String, val label: String, val expected: String)

    val ACTIVITIES = listOf(
        Activity("SIT", "Sitting quietly", "Still"),
        Activity("WALK", "Normal walk", "Walking"),
        Activity("SLOWWALK", "Slow walk", "Walking"),
        Activity("TABLE", "Phone on table", "Still"),
        Activity("STAIRS", "Stairs", "Walking"),
    )

    const val HEADER =
        "\"Time (s)\",\"Acceleration x (m/s^2)\",\"Acceleration y (m/s^2)\"," +
            "\"Acceleration z (m/s^2)\",\"Absolute acceleration (m/s^2)\"," +
            "\"SEENior 2 s value (m/s^2)\",\"Steps\"," +
            "\"Linear acceleration x (m/s^2)\",\"Linear acceleration y (m/s^2)\"," +
            "\"Linear acceleration z (m/s^2)\",\"Absolute linear acceleration (m/s^2)\"," +
            "\"Gyroscope x (rad/s)\",\"Gyroscope y (rad/s)\",\"Gyroscope z (rad/s)\"," +
            "\"Absolute rotation (rad/s)\""

    /**
     * One reading. [value] is blank until the 2 s window holds enough readings; [linear] (x, y, z
     * without gravity) is blank when the phone has no linear acceleration sensor or it has not
     * reported yet. Its absolute value is computed here.
     */
    fun row(
        seconds: Double, x: Float, y: Float, z: Float, absolute: Float, value: Float?, steps: Int,
        linear: FloatArray? = null,
        gyro: FloatArray? = null
    ): String {
        val base = String.format(
            Locale.US, "%.6f,%.6f,%.6f,%.6f,%.6f,%s,%d",
            seconds, x, y, z, absolute, value?.let { fmt(it) } ?: "", steps
        )
        val lin = linear?.let {
            val abs = kotlin.math.sqrt(it[0] * it[0] + it[1] * it[1] + it[2] * it[2])
            "${fmt(it[0])},${fmt(it[1])},${fmt(it[2])},${fmt(abs)}"
        } ?: ",,,"
        val rot = gyro?.let {
            val abs = kotlin.math.sqrt(it[0] * it[0] + it[1] * it[1] + it[2] * it[2])
            "${fmt(it[0])},${fmt(it[1])},${fmt(it[2])},${fmt(abs)}"
        } ?: ",,,"
        return "$base,$lin,$rot"
    }

    private fun fmt(v: Float) = String.format(Locale.US, "%.6f", v)

    /** Letters and digits only, upper case, at most 8 characters, so it is safe in a file name. */
    fun cleanParticipant(raw: String): String =
        raw.uppercase(Locale.US).filter { it in 'A'..'Z' || it in '0'..'9' }.take(8)

    /** For example `P01_WALK_R2.csv`. */
    fun fileName(participant: String, activityCode: String, repeat: Int): String =
        "${cleanParticipant(participant)}_${activityCode}_R$repeat.csv"

    /** The next free repeat number for this participant and activity, given the files already saved. */
    fun nextRepeat(existing: Collection<String>, participant: String, activityCode: String): Int {
        val prefix = "${cleanParticipant(participant)}_${activityCode}_R"
        val used = existing.mapNotNull { name ->
            if (name.startsWith(prefix) && name.endsWith(".csv")) {
                name.removePrefix(prefix).removeSuffix(".csv").toIntOrNull()
            } else {
                null
            }
        }
        return (used.maxOrNull() ?: 0) + 1
    }
}
