package com.pup.seenior.ui.demo

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pup.seenior.detection.FallCheckMonitor
import com.pup.seenior.detection.FallDetector
import com.pup.seenior.detection.MotionWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.sqrt

/**
 * Backs the walk-test recorder on the Home tab: a live view of the accelerometer, a live fall check
 * (SEENior's real detector, never raising an alert), and labelled recordings exported as CSV for
 * the walk-test spreadsheet.
 *
 * Privacy: readings are held in memory while recording and written only to this app's own
 * storage on the phone. Nothing here touches the database or the network, so raw sensor data
 * still never leaves the phone unless the research team exports a file by hand (CLAUDE.md §11).
 */
class RecorderViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val stepDetector: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val linearSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    val hasLinearSensor: Boolean get() = linearSensor != null
    val hasGyroscope: Boolean get() = gyroscope != null

    /**
     * The live fall check: SEENior's real detector, production thresholds, never raising an alert.
     * Rotation is required only when the phone has a gyroscope, as in SensorCollectionService.
     * No one-minute pause after a confirmed fall (the real detector keeps it), so trials can follow
     * each other straight away.
     */
    private val fallCheck = FallCheckMonitor(
        FallDetector.Config(requireRotation = gyroscope != null, cooldownMs = 0)
    )

    /** Gate by gate status of the fall check, refreshed about ten times a second. */
    var fallSnapshot by mutableStateOf(FallCheckMonitor.Snapshot.idle(fallCheck.config))
        private set

    /** Acceleration magnitude and rotation speed over the graphs' window, times relative to [graphOriginMs]. */
    var accelSeries by mutableStateOf(Series.EMPTY)
        private set
    var rotationSeries by mutableStateOf(Series.EMPTY)
        private set

    /** The accelerometer's three axes (m/s2), same times as [accelSeries]. */
    var accelX by mutableStateOf(Series.EMPTY)
        private set
    var accelY by mutableStateOf(Series.EMPTY)
        private set
    var accelZ by mutableStateOf(Series.EMPTY)
        private set

    /** The linear accelerometer (without gravity) and gyroscope axes, and the 2 s pre-fall value. */
    var linX by mutableStateOf(Series.EMPTY)
        private set
    var linY by mutableStateOf(Series.EMPTY)
        private set
    var linZ by mutableStateOf(Series.EMPTY)
        private set
    var gyroX by mutableStateOf(Series.EMPTY)
        private set
    var gyroY by mutableStateOf(Series.EMPTY)
        private set
    var gyroZ by mutableStateOf(Series.EMPTY)
        private set
    var preFallSeries by mutableStateOf(Series.EMPTY)
        private set
    /** Absolute linear acceleration: the magnitude of the without-g reading. */
    var linAbs by mutableStateOf(Series.EMPTY)
        private set

    /** Show acceleration with gravity (what SEENior's detection uses) or without, as phyphox offers both. */
    var withGravity by mutableStateOf(true)

    /** A recording is running but paused (phyphox's pause): nothing is added until it is resumed. */
    var recordingPaused by mutableStateOf(false)
        private set

    /**
     * Sensor time (ms) of the graphs' 0 s, as in phyphox: the first reading after the screen opened,
     * or after Reset live check, or when a recording started. Times count up from there.
     */
    var graphOriginMs by mutableStateOf(0L)
        private set

    /** Seconds from 0 s to the newest reading. */
    var latestSeconds by mutableStateOf(0f)
        private set

    /** The graphs are frozen (paused by hand, or holding a captured fall attempt). */
    var graphPaused by mutableStateOf(false)
        private set

    /** The frozen graphs hold a fall attempt. */
    var graphCaptured by mutableStateOf(false)
        private set

    var participant by mutableStateOf("P01")
    var activityCode by mutableStateOf(RecorderCsv.ACTIVITIES.first().code)

    var recording by mutableStateOf(false)
        private set
    var elapsedSeconds by mutableStateOf(0)
        private set
    var liveValue by mutableStateOf<Float?>(null)
        private set
    /** Acceleration without gravity right now (magnitude of the linear acceleration sensor). */
    var liveLinear by mutableStateOf<Float?>(null)
        private set
    var readings by mutableStateOf(0)
        private set
    var savedFile by mutableStateOf<File?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    // Sensor-thread state, published to the UI by the ticker below.
    private val window = MotionWindow()
    private val lock = Any()
    // t, x, y, z, abs, value (NaN = blank), steps, linear x, y, z, gyro x, y, z (NaN = blank)
    private val rows = ArrayList<FloatArray>()
    private var linear: FloatArray? = null
    private var gyro: FloatArray? = null
    private val accelHistory = History(HISTORY_POINTS)
    private val xHistory = History(HISTORY_POINTS)
    private val yHistory = History(HISTORY_POINTS)
    private val zHistory = History(HISTORY_POINTS)
    private val gyroHistory = History(HISTORY_POINTS)
    private val linXHistory = History(HISTORY_POINTS)
    private val linYHistory = History(HISTORY_POINTS)
    private val linZHistory = History(HISTORY_POINTS)
    private val gyroXHistory = History(HISTORY_POINTS)
    private val gyroYHistory = History(HISTORY_POINTS)
    private val gyroZHistory = History(HISTORY_POINTS)
    private val preFallHistory = History(HISTORY_POINTS)
    private val linAbsHistory = History(HISTORY_POINTS)
    private val histories = listOf(
        accelHistory, xHistory, yHistory, zHistory, gyroHistory, linXHistory, linYHistory, linZHistory,
        gyroXHistory, gyroYHistory, gyroZHistory, preFallHistory, linAbsHistory
    )
    /** Null until the first reading after [restartClock]; that reading becomes 0 s. */
    private var originMs: Long? = null
    private var capturedStartMs: Long? = null
    private var lastOutcome = FallCheckMonitor.Outcome.NONE
    private var startNanos = 0L
    private var stepCount = 0
    private var latestValue: Float? = null
    private var ticker: Job? = null

    private var listening = false

    /**
     * Sensors run only while the recorder card is open (or a recording is running), so the Home
     * tab costs no battery when the card is closed.
     */
    fun listen(on: Boolean) {
        if (on == listening) return
        listening = on
        if (on) {
            window.clear()
            synchronized(lock) { restartClock() }
            accelerometer?.let { sensorManager.registerListener(this, it, SAMPLING_PERIOD_US) }
            // Steps go into the CSV's Steps column (not shown on screen).
            stepDetector?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }
            linearSensor?.let { sensorManager.registerListener(this, it, SAMPLING_PERIOD_US) }
            gyroscope?.let { sensorManager.registerListener(this, it, SAMPLING_PERIOD_US) }
        } else {
            sensorManager.unregisterListener(this)
            synchronized(lock) {
                latestValue = null
                linear = null
                gyro = null
            }
        }
    }

    init {
        if (accelerometer == null) message = "This phone has no accelerometer."
        ticker = viewModelScope.launch {
            while (isActive) {
                publish()
                delay(100)
            }
        }
    }

    /**
     * phyphox's play button: starts a new recording, or resumes a paused one. While paused nothing
     * is recorded and the graphs are frozen for reading; the time keeps the real clock, so a pause
     * shows as a gap in the file rather than joining two moments together.
     */
    fun start() {
        if (recording) {
            if (recordingPaused) {
                recordingPaused = false
                resumeGraphs()
            }
            return
        }
        val code = RecorderCsv.cleanParticipant(participant)
        if (code.isEmpty()) {
            message = "Enter a participant code, for example P01."
            return
        }
        participant = code
        synchronized(lock) {
            rows.clear()
            stepCount = 0
            startNanos = 0L
            // Like pressing play in phyphox: the graphs start again at 0 s with the recording.
            restartClock()
            graphPaused = false
            graphCaptured = false
        }
        savedFile = null
        message = null
        recordingPaused = false
        recording = true
    }

    /** phyphox's pause: stop adding readings and freeze the graphs, keeping what was recorded. */
    fun pauseRecording() {
        if (!recording || recordingPaused) return
        recordingPaused = true
        pauseGraphs()
    }

    /** Stops and saves. The file name gets the next free repeat number for this participant and activity. */
    fun stop() {
        if (!recording) return
        recording = false
        recordingPaused = false
        val snapshot = synchronized(lock) { ArrayList(rows).also { rows.clear() } }
        if (snapshot.isEmpty()) {
            message = "Nothing was recorded."
            return
        }
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) { write(snapshot) }
            savedFile = file
            refreshSaved()
            message = "Saved ${file.name}: ${snapshot.size} readings, %.1f s.".format(snapshot.last()[0])
        }
    }

    /** Reset: throws away the current recording and starts the graphs again from 0 s. */
    fun discard() {
        recording = false
        recordingPaused = false
        synchronized(lock) {
            rows.clear()
            restartClock()
        }
        graphPaused = false
        graphCaptured = false
        message = "Recording discarded."
    }

    /** All recordings saved on this phone, newest first. Refreshed on open and after each save. */
    var savedRecordings by mutableStateOf<List<File>>(emptyList())
        private set

    private fun refreshSaved() {
        savedRecordings = recordingsDir().listFiles { f -> f.name.endsWith(".csv") }
            ?.sortedByDescending { it.lastModified() }.orEmpty()
    }

    // After the property above, so its state holder exists before the first refresh.
    init {
        refreshSaved()
    }

    fun recordingsDir(): File {
        val app = getApplication<Application>()
        val dir = app.getExternalFilesDir("recordings") ?: File(app.filesDir, "recordings")
        dir.mkdirs()
        return dir
    }

    private fun write(snapshot: List<FloatArray>): File {
        val dir = recordingsDir()
        val repeat = RecorderCsv.nextRepeat(dir.list()?.toList().orEmpty(), participant, activityCode)
        val file = File(dir, RecorderCsv.fileName(participant, activityCode, repeat))
        file.bufferedWriter().use { out ->
            out.appendLine(RecorderCsv.HEADER)
            for (r in snapshot) {
                out.appendLine(
                    RecorderCsv.row(
                        r[0].toDouble(), r[1], r[2], r[3], r[4], r[5].takeUnless { it.isNaN() }, r[6].toInt(),
                        if (r[7].isNaN()) null else floatArrayOf(r[7], r[8], r[9]),
                        if (r[10].isNaN()) null else floatArrayOf(r[10], r[11], r[12])
                    )
                )
            }
        }
        return file
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = sqrt(x * x + y * y + z * z)
                val value = window.add(event.timestamp / NANOS_PER_MS, magnitude)
                synchronized(lock) {
                    fallCheck.onAcceleration(event.timestamp, magnitude)
                    val ms = event.timestamp / NANOS_PER_MS
                    if (originMs == null) originMs = ms
                    accelHistory.add(ms, magnitude)
                    xHistory.add(ms, x)
                    yHistory.add(ms, y)
                    zHistory.add(ms, z)
                    linear?.let {
                        linXHistory.add(ms, it[0])
                        linYHistory.add(ms, it[1])
                        linZHistory.add(ms, it[2])
                        linAbsHistory.add(ms, sqrt(it[0] * it[0] + it[1] * it[1] + it[2] * it[2]))
                    }
                    value?.let { preFallHistory.add(ms, it) }
                    latestValue = value
                    if (recording && !recordingPaused && rows.size < MAX_READINGS) {
                        if (startNanos == 0L) startNanos = event.timestamp
                        val seconds = (event.timestamp - startNanos) / 1e9f
                        val lin = linear
                        val rot = gyro
                        rows.add(
                            floatArrayOf(
                                seconds, x, y, z, magnitude, value ?: Float.NaN, stepCount.toFloat(),
                                lin?.get(0) ?: Float.NaN, lin?.get(1) ?: Float.NaN, lin?.get(2) ?: Float.NaN,
                                rot?.get(0) ?: Float.NaN, rot?.get(1) ?: Float.NaN, rot?.get(2) ?: Float.NaN
                            )
                        )
                    }
                }
            }
            // Steps taken while a recording is paused are not counted.
            Sensor.TYPE_STEP_DETECTOR -> synchronized(lock) { if (recording && !recordingPaused) stepCount++ }
            Sensor.TYPE_LINEAR_ACCELERATION -> synchronized(lock) {
                linear = floatArrayOf(event.values[0], event.values[1], event.values[2])
            }
            Sensor.TYPE_GYROSCOPE -> {
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                val speed = sqrt(gx * gx + gy * gy + gz * gz)
                synchronized(lock) {
                    gyro = floatArrayOf(gx, gy, gz)
                    fallCheck.onRotation(event.timestamp, speed)
                    val ms = event.timestamp / NANOS_PER_MS
                    if (originMs != null) {
                        gyroHistory.add(ms, speed)
                        gyroXHistory.add(ms, gx)
                        gyroYHistory.add(ms, gy)
                        gyroZHistory.add(ms, gz)
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun publish() {
        var full = false
        synchronized(lock) {
            liveValue = latestValue
            liveLinear = linear?.let { sqrt(it[0] * it[0] + it[1] * it[1] + it[2] * it[2]) }
            readings = rows.size
            elapsedSeconds = if (recording && rows.isNotEmpty()) rows.last()[0].toInt() else 0
            full = recording && rows.size >= MAX_READINGS

            val snap = fallCheck.snapshot()
            fallSnapshot = snap
            // A new attempt began after a captured one: go back to live so it can be watched.
            val newStart = snap.freeFallStartAtMs
            val captured = capturedStartMs
            if (graphCaptured && snap.outcome == FallCheckMonitor.Outcome.NONE && newStart != null &&
                captured != null && newStart > captured
            ) {
                graphPaused = false
                graphCaptured = false
            }
            // A fall attempt that got past free fall has just ended: freeze the graphs on it.
            val start = snap.freeFallStartAtMs
            if (lastOutcome == FallCheckMonitor.Outcome.NONE && snap.outcome != FallCheckMonitor.Outcome.NONE &&
                snap.freeFallEndAtMs != null && start != null
            ) {
                freezeGraphs()
                graphCaptured = true
                capturedStartMs = start
            }
            lastOutcome = snap.outcome
            if (!graphPaused) setGraphs()
        }
        if (full) stop()
    }

    /** Freezes the graphs on what is in memory now. */
    private fun freezeGraphs() {
        setGraphs()
        graphPaused = true
    }

    /** Publishes every history with times in seconds since 0 s. */
    private fun setGraphs() {
        val origin = originMs ?: return
        graphOriginMs = origin
        latestSeconds = ((accelHistory.newest() ?: origin) - origin) / 1000f
        accelSeries = accelHistory.series(origin)
        accelX = xHistory.series(origin)
        accelY = yHistory.series(origin)
        accelZ = zHistory.series(origin)
        rotationSeries = gyroHistory.series(origin)
        linX = linXHistory.series(origin)
        linY = linYHistory.series(origin)
        linZ = linZHistory.series(origin)
        gyroX = gyroXHistory.series(origin)
        gyroY = gyroYHistory.series(origin)
        gyroZ = gyroZHistory.series(origin)
        preFallSeries = preFallHistory.series(origin)
        linAbs = linAbsHistory.series(origin)
    }

    /** Empties the graphs; the next reading becomes 0 s. */
    private fun restartClock() {
        histories.forEach { it.clear() }
        originMs = null
        latestSeconds = 0f
        capturedStartMs = null
    }

    /** Pauses the live graphs so they can be zoomed and read. */
    fun pauseGraphs() = synchronized(lock) {
        freezeGraphs()
        graphCaptured = false
    }

    fun resumeGraphs() = synchronized(lock) {
        graphPaused = false
        graphCaptured = false
    }

    /** Clears the fall check and resumes the graphs, ready for the next trial. */
    fun resetFallCheck() = synchronized(lock) {
        fallCheck.reset()
        lastOutcome = FallCheckMonitor.Outcome.NONE
        fallSnapshot = fallCheck.snapshot()
        restartClock()
        graphPaused = false
        graphCaptured = false
    }

    override fun onCleared() {
        sensorManager.unregisterListener(this)
        ticker?.cancel()
        super.onCleared()
    }

    companion object {
        /** 50 Hz, the same rate the fall detector reads the accelerometer at (SensorCollectionService). */
        const val SAMPLING_PERIOD_US = 20_000
        private const val NANOS_PER_MS = 1_000_000L
        /** 10 minutes at 50 Hz; the recording stops and saves on its own after that. */
        const val MAX_READINGS = 30_000
        /** 30 s at 50 Hz kept for the zoomable graphs: room for a whole fall attempt. */
        const val HISTORY_POINTS = 1_500
    }
}

/** A fixed-size ring of (sensor time ms, value), oldest overwritten first. */
private class History(private val capacity: Int) {
    private val times = LongArray(capacity)
    private val values = FloatArray(capacity)
    private var head = 0
    private var count = 0

    fun add(ms: Long, value: Float) {
        times[head] = ms
        values[head] = value
        head = (head + 1) % capacity
        if (count < capacity) count++
    }

    fun newest(): Long? = if (count == 0) null else times[(head - 1 + capacity) % capacity]

    fun clear() {
        head = 0
        count = 0
    }

    /** Everything held, oldest first, with times in seconds relative to [originMs]. */
    fun series(originMs: Long): Series {
        val t = FloatArray(count)
        val v = FloatArray(count)
        for (i in 0 until count) {
            val idx = (head - count + i + capacity) % capacity
            t[i] = (times[idx] - originMs) / 1000f
            v[i] = values[idx]
        }
        return Series(t, v)
    }
}
