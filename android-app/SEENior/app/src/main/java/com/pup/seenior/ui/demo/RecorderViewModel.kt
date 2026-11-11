package com.pup.seenior.ui.demo

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
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

    val hasStepDetector: Boolean get() = stepDetector != null
    val hasLinearSensor: Boolean get() = linearSensor != null
    val hasGyroscope: Boolean get() = gyroscope != null

    /**
     * The live fall check: SEENior's real detector, production thresholds, never raising an alert.
     * Rotation is required only when the phone has a gyroscope, as in SensorCollectionService.
     */
    private val fallCheck = FallCheckMonitor(FallDetector.Config(requireRotation = gyroscope != null))

    /** Gate by gate status of the fall check, refreshed about ten times a second. */
    var fallSnapshot by mutableStateOf(FallCheckMonitor.Snapshot.idle(fallCheck.config))
        private set

    /** Acceleration magnitude and rotation speed over the graphs' window, times relative to [graphOriginMs]. */
    var accelSeries by mutableStateOf(Series.EMPTY)
        private set
    var rotationSeries by mutableStateOf(Series.EMPTY)
        private set

    /** Sensor time (ms) the graphs' zero is measured from: the newest reading when live, the start of free fall when captured. */
    var graphOriginMs by mutableStateOf(0L)
        private set

    /** The graphs are frozen (paused by hand, or holding a captured fall attempt). */
    var graphPaused by mutableStateOf(false)
        private set

    /** The frozen graphs hold a fall attempt, with time 0 at the start of its free fall. */
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
    var steps by mutableStateOf(0)
        private set
    var readings by mutableStateOf(0)
        private set
    var savedFile by mutableStateOf<File?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    /** The last [GRAPH_POINTS] 2 s values, oldest first, for the live graph (refreshed ~10 times a second). */
    var graph by mutableStateOf(FloatArray(0))
        private set

    // Sensor-thread state, published to the UI by the ticker below.
    private val window = MotionWindow()
    private val graphRing = FloatArray(GRAPH_POINTS)
    private var graphHead = 0
    private var graphCount = 0
    private val lock = Any()
    // t, x, y, z, abs, value (NaN = blank), steps, linear x, y, z (NaN = blank)
    private val rows = ArrayList<FloatArray>()
    private var linear: FloatArray? = null
    private var gyro: FloatArray? = null
    private val accelHistory = History(HISTORY_POINTS)
    private val gyroHistory = History(HISTORY_POINTS)
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
            accelerometer?.let { sensorManager.registerListener(this, it, SAMPLING_PERIOD_US) }
            stepDetector?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }
            linearSensor?.let { sensorManager.registerListener(this, it, SAMPLING_PERIOD_US) }
            gyroscope?.let { sensorManager.registerListener(this, it, SAMPLING_PERIOD_US) }
        } else {
            sensorManager.unregisterListener(this)
            synchronized(lock) {
                latestValue = null
                linear = null
                gyro = null
                graphCount = 0
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

    fun start() {
        if (recording) return
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
        }
        savedFile = null
        message = null
        recording = true
    }

    /** Stops and saves. The file name gets the next free repeat number for this participant and activity. */
    fun stop() {
        if (!recording) return
        recording = false
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

    fun discard() {
        recording = false
        synchronized(lock) { rows.clear() }
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
                    accelHistory.add(event.timestamp / NANOS_PER_MS, magnitude)
                    latestValue = value
                    graphRing[graphHead] = value ?: 0f
                    graphHead = (graphHead + 1) % GRAPH_POINTS
                    if (graphCount < GRAPH_POINTS) graphCount++
                    if (recording && rows.size < MAX_READINGS) {
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
            Sensor.TYPE_STEP_DETECTOR -> synchronized(lock) { if (recording) stepCount++ }
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
                    gyroHistory.add(event.timestamp / NANOS_PER_MS, speed)
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
            steps = stepCount
            readings = rows.size
            if (recording && startNanos != 0L) {
                elapsedSeconds = ((SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000_000L).toInt()
            } else if (!recording) {
                elapsedSeconds = 0
            }
            full = recording && rows.size >= MAX_READINGS
            graph = FloatArray(graphCount) { i -> graphRing[(graphHead - graphCount + i + GRAPH_POINTS) % GRAPH_POINTS] }

            val snap = fallCheck.snapshot()
            fallSnapshot = snap
            // A fall attempt that got past free fall has just ended: freeze the graphs on it.
            val start = snap.freeFallStartAtMs
            if (lastOutcome == FallCheckMonitor.Outcome.NONE && snap.outcome != FallCheckMonitor.Outcome.NONE &&
                snap.freeFallEndAtMs != null && start != null
            ) {
                freezeGraphs(start)
                graphCaptured = true
            }
            lastOutcome = snap.outcome
            if (!graphPaused) {
                val newest = accelHistory.newest() ?: 0L
                graphOriginMs = newest
                accelSeries = accelHistory.series(newest)
                rotationSeries = gyroHistory.series(newest)
            }
        }
        if (full) stop()
    }

    /** Freezes the graphs on what is in memory now, with time 0 at [originMs]. */
    private fun freezeGraphs(originMs: Long) {
        graphOriginMs = originMs
        accelSeries = accelHistory.series(originMs)
        rotationSeries = gyroHistory.series(originMs)
        graphPaused = true
    }

    /** Pauses the live graphs so they can be zoomed and read. */
    fun pauseGraphs() = synchronized(lock) {
        freezeGraphs(accelHistory.newest() ?: 0L)
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
        /** 10 s of history on the live graph. */
        const val GRAPH_POINTS = 500
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
