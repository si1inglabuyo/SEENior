package com.pup.seenior.ui.demo

import com.pup.seenior.detection.FallCheckMonitor
import java.io.File
import java.util.Locale
import kotlin.math.sqrt

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

    /**
     * The walk test's activities: walking with the phone carried three common ways, resting, and
     * four everyday ones a senior does (slow walking, sitting with the phone in hand, getting up
     * from a chair, lying down on a bed). Each code goes into the file name; [expected] is the band it should land in.
     *
     * The codes matter to `docs/evidence/recording_analysis.py`, which reads them back from the file
     * name: `REST*` and `SIT*` count as resting, `CHAIR*` and `BED*` are reported but kept out of
     * the walking-versus-resting threshold sweep (a transition is not meant to read as moving), and
     * everything else counts as walking. Keep new codes in line with that.
     */
    data class Activity(val code: String, val label: String, val expected: String)

    val ACTIVITIES = listOf(
        Activity("WALKHAND", "Walking, phone in hand", "Walking"),
        Activity("WALKPOCKET", "Walking, phone in pocket", "Walking"),
        Activity("WALKBAG", "Walking, phone in bag", "Walking"),
        Activity("RESTPOCKET", "Resting, phone in pocket", "Still"),
        Activity("WALKSLOW", "Walking slowly, phone in pocket", "Walking"),
        Activity("SITHAND", "Sitting, using the phone in hand", "Still"),
        Activity("CHAIRSTAND", "Sitting down and standing up, phone in pocket", "Gentle"),
        Activity("BEDLIE", "Lying down on a bed and getting up", "Gentle"),
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

    // ------------------------------------------------------------------ fall detection attempts

    /** Header of a saved fall-detection attempt: the same readings as the graphs, plus the event marks. */
    const val FALL_HEADER =
        "\"Time (s)\",\"Acceleration x (m/s^2)\",\"Acceleration y (m/s^2)\"," +
            "\"Acceleration z (m/s^2)\",\"Absolute acceleration (m/s^2)\"," +
            "\"Linear acceleration x (m/s^2)\",\"Linear acceleration y (m/s^2)\"," +
            "\"Linear acceleration z (m/s^2)\"," +
            "\"Gyroscope x (rad/s)\",\"Gyroscope y (rad/s)\",\"Gyroscope z (rad/s)\"," +
            "\"Absolute rotation (rad/s)\",\"Event\""

    /** For example `FALLCHECK_20261115_031530_CONFIRMED.csv`; [stamp] is `yyyyMMdd_HHmmss`. */
    fun fallAttemptName(stamp: String, outcome: FallCheckMonitor.Outcome): String {
        val code = when (outcome) {
            FallCheckMonitor.Outcome.CONFIRMED -> "CONFIRMED"
            FallCheckMonitor.Outcome.NOT_CONFIRMED -> "NOTFALL"
            FallCheckMonitor.Outcome.NONE -> "LIVE"
        }
        return "FALLCHECK_${stamp}_$code.csv"
    }

    /**
     * The moments the detector marked on the graphs, as (seconds, label) for the Event column:
     * the start of free fall, the impact, the end of the settle period, and how the attempt ended.
     */
    fun fallEvents(snap: FallCheckMonitor.Snapshot, originMs: Long): List<Pair<Float, String>> {
        fun sec(atMs: Long?) = atMs?.let { (it - originMs) / 1000f }
        val out = mutableListOf<Pair<Float, String>>()
        sec(snap.freeFallStartAtMs)?.let { out += it to "FREE_FALL_START" }
        sec(snap.freeFallEndAtMs)?.let { out += it to "FREE_FALL_END" }
        sec(snap.impactAtMs)?.let { impact ->
            out += impact to "IMPACT"
            val settled = impact + snap.config.settleGraceMs / 1000f
            out += settled to "SETTLED"
            if (snap.outcome == FallCheckMonitor.Outcome.CONFIRMED) {
                out += settled + snap.config.stillnessMs / 1000f to "STILL_CONFIRMED"
            }
        }
        if (snap.outcome == FallCheckMonitor.Outcome.NOT_CONFIRMED) {
            sec(snap.endedAtMs)?.let { out += it to "STOPPED_NOT_A_FALL" }
        }
        return out.sortedBy { it.first }
    }

    /**
     * One CSV line per accelerometer reading. The linear-acceleration and gyroscope columns carry
     * the latest reading of their own sensor at or before that moment (the sensors report on their
     * own clocks, as in the pre-fall recording) and are blank before that sensor's first reading.
     * Each event is written on the first row at or after its time.
     */
    fun fallAttemptRows(
        accelX: Series, accelY: Series, accelZ: Series,
        linX: Series, linY: Series, linZ: Series,
        gyroX: Series, gyroY: Series, gyroZ: Series,
        events: List<Pair<Float, String>>
    ): List<String> {
        val n = minOf(accelX.t.size, accelY.t.size, accelZ.t.size)
        val labels = arrayOfNulls<String>(n)
        for ((at, label) in events) {
            val i = (0 until n).firstOrNull { accelX.t[it] >= at } ?: continue
            labels[i] = listOfNotNull(labels[i], label).joinToString(";")
        }

        /** Index of the newest reading at or before [t], moving on from [from]; -1 if none yet. */
        fun advance(s: Series, from: Int, t: Float): Int {
            var i = from
            while (i + 1 < s.t.size && s.t[i + 1] <= t) i++
            return i
        }

        var li = -1
        var gi = -1
        val out = ArrayList<String>(n)
        for (k in 0 until n) {
            val t = accelX.t[k]
            val x = accelX.v[k]
            val y = accelY.v[k]
            val z = accelZ.v[k]
            li = advance(linX, li, t)
            gi = advance(gyroX, gi, t)
            val lin = if (li >= 0 && li < linY.v.size && li < linZ.v.size)
                "${fmt(linX.v[li])},${fmt(linY.v[li])},${fmt(linZ.v[li])}" else ",,"
            val rot = if (gi >= 0 && gi < gyroY.v.size && gi < gyroZ.v.size) {
                val gx = gyroX.v[gi]
                val gy = gyroY.v[gi]
                val gz = gyroZ.v[gi]
                "${fmt(gx)},${fmt(gy)},${fmt(gz)},${fmt(sqrt(gx * gx + gy * gy + gz * gz))}"
            } else ",,,"
            out += String.format(Locale.US, "%.6f,%s,%s,%s,%s,%s,%s,%s", t, fmt(x), fmt(y), fmt(z),
                fmt(sqrt(x * x + y * y + z * z)), lin, rot, labels[k] ?: "")
        }
        return out
    }

    /** Whether a saved file came from the Fall detection screen (as opposed to a Pre-fall recording). */
    fun isFallAttempt(fileName: String): Boolean = fileName.startsWith("FALLCHECK_")

    /** The saved recordings in [dir] for one screen: the Fall detection attempts, or the Pre-fall ones. */
    fun recordings(dir: File, fallAttempts: Boolean): List<File> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".csv") && isFallAttempt(f.name) == fallAttempts }
            .orEmpty().sortedByDescending { it.lastModified() }

    /**
     * Deletes the saved recordings of one screen and nothing else: the other screen's files, and
     * anything that is not a `.csv`, are left alone. Returns how many were removed.
     */
    fun deleteAll(dir: File, fallAttempts: Boolean): Int = recordings(dir, fallAttempts).count { it.delete() }
}
