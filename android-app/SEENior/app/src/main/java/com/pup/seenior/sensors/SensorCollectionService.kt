package com.pup.seenior.sensors

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pup.seenior.R
import com.pup.seenior.alerts.AlertResponder
import com.pup.seenior.baseline.SeedBaselineGenerator
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.SensorData
import com.pup.seenior.detection.FallDetector
import com.pup.seenior.detection.MedianMadDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

class SensorCollectionService : Service(), SensorEventListener
{
   private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
   private var pollingJob : Job? = null

   private lateinit var sensorManager: SensorManager
   private var accelerometer: Sensor? = null
   private var stepCounter: Sensor? = null
   private var gyroscope: Sensor? = null

    private var movementSampleSum = 0.0
    private var movementSampleCount = 0
    private var lastSignificantMovementAt = System.currentTimeMillis()
    private var latestStepCount = 0
    private var screenUnlockCount = 0
    private var screenOffSince: Long? = null

    /**
     * Layer 0 (CLAUDE.md §5). Confined to the sensor callback thread along with
     * [lastMovementSampleNanos], so unlike the counters above it needs no lock.
     */
    private lateinit var fallDetector: FallDetector
    private var lastMovementSampleNanos = 0L

    // onSensorChanged/screenReceiver fire on the main thread (no Handler passed to
    // registerListener/registerReceiver); collectAndStore() runs on Dispatchers.Default.
    // All reads/writes of the counters above must go through this lock.
    private val stateLock = Any()

    private data class SensorSnapshot(
        val movementScore: Double,
        val inactivityDurationSeconds: Long,
        val screenIdleDurationSeconds: Long,
        val screenUnlockCount: Int,
        val stepCount: Int,
    )

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            synchronized(stateLock) {
                when (intent.action) {
                    // Only the first SCREEN_OFF starts the clock. A repeat without an
                    // intervening SCREEN_ON would restart it and lose the stretch so far.
                    Intent.ACTION_SCREEN_OFF ->
                        if (screenOffSince == null) screenOffSince = System.currentTimeMillis()
                    Intent.ACTION_SCREEN_ON -> screenOffSince = null
                    Intent.ACTION_USER_PRESENT -> screenUnlockCount++
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        isRunning = true

        // The service can start while the screen is already off (boot, or a restart with the
        // phone in a pocket). Without this the idle clock never starts, because the SCREEN_OFF
        // that would have started it already happened.
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        screenOffSince = if (powerManager.isInteractive) null else System.currentTimeMillis()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        // Rotation confirms a fall (CLAUDE.md §4), but not every Android phone ships a gyroscope.
        // On one that does not, demanding rotation would mean never detecting a fall at all.
        // The trace goes to logcat from out here rather than from inside FallDetector, which
        // stays free of Android imports so a JUnit test can drive it. Read with:
        //   adb logcat -s FallDetector
        //
        // INFO rather than DEBUG, and not by preference: the Infinix X6885 this is developed
        // against ships with log.tag=I and drops every DEBUG line system-wide, so a Log.d
        // trace is invisible on the one device that matters. A fall candidate is rare and
        // important enough that INFO is defensible on its own terms anyway.
        fallDetector = FallDetector(
            FallDetector.Config(requireRotation = gyroscope != null),
            trace = { Log.i("FallDetector", it) }
        )
        // Proves the trace path itself is alive. Without it, an empty log after a drop cannot
        // be told apart from logging being broken again.
        Log.i("FallDetector", "armed: requireRotation=${gyroscope != null}, accelerometer at 50 Hz")

        accelerometer?.let { registerForFallDetection(it) }
        gyroscope?.let { registerForFallDetection(it) }
        // The step counter is an on-change sensor reporting a running total; it has nothing to
        // contribute to a fall signature and stays at the low rate.
        stepCounter?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }

        pollingJob = serviceScope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                collectAndStore()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // Cleared first, so nothing can read a stale `true` while the service tears down.
        isRunning = false
        sensorManager.unregisterListener(this)
        unregisterReceiver(screenReceiver)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val magnitude = sqrt(
                    event.values[0] * event.values[0] +
                            event.values[1] * event.values[1] +
                            event.values[2] * event.values[2]
                )
                if (fallDetector.onAcceleration(event.timestamp, magnitude)) onFallDetected()
                recordMovementSample(event.timestamp, magnitude)
            }
            Sensor.TYPE_GYROSCOPE -> {
                val angularSpeed = sqrt(
                    event.values[0] * event.values[0] +
                            event.values[1] * event.values[1] +
                            event.values[2] * event.values[2]
                )
                fallDetector.onRotation(event.timestamp, angularSpeed)
            }
            Sensor.TYPE_STEP_COUNTER -> synchronized(stateLock) {
                latestStepCount = event.values[0].toInt()
            }
        }
    }

    /**
     * Feeds the Layer 1 movement signals, decimated back to the 5 Hz this service sampled at
     * before fall detection raised the accelerometer to 50 Hz.
     *
     * Without the decimation the change would quietly reshape the Routine Fingerprint: ten times
     * as many samples means ten times as many chances to catch a twitch, so `inactivity_duration`
     * would read shorter and `movement_score` different for reasons that have nothing to do with
     * how the senior actually behaved. Baselines built before this change would no longer be
     * comparable with readings taken after it.
     */
    private fun recordMovementSample(eventNanos: Long, magnitude: Float) {
        if (eventNanos - lastMovementSampleNanos < MOVEMENT_SAMPLE_INTERVAL_NANOS) return
        lastMovementSampleNanos = eventNanos

        val deviation = (abs(magnitude - SensorManager.GRAVITY_EARTH) / SensorManager.GRAVITY_EARTH)
            .coerceIn(0.0f, 1.0f)
        synchronized(stateLock) {
            movementSampleSum += deviation
            movementSampleCount++
            if (deviation > MOVEMENT_THRESHOLD) lastSignificantMovementAt = wallClockOf(eventNanos)
        }
    }

    /**
     * Converts a sensor event's own clock to wall-clock time. Batched samples can be seconds old
     * by the time they are delivered, and inactivity is measured from this instant — dating a
     * movement from when the batch arrived rather than when it happened would shorten every
     * inactivity reading by the batching latency.
     */
    private fun wallClockOf(eventNanos: Long): Long {
        val ageMillis = ((SystemClock.elapsedRealtimeNanos() - eventNanos) / 1_000_000)
            .coerceAtLeast(0)
        return System.currentTimeMillis() - ageMillis
    }

    /**
     * Layer 0 confirmed a fall. High risk without any fuzzy classification: CLAUDE.md §5 fixes
     * the risk level for this trigger, and unlike a statistical deviation there is no degree to
     * weigh — either the three-phase signature matched or it did not.
     */
    private fun onFallDetected() {
        serviceScope.launch {
            AlertResponder.raise(
                applicationContext,
                SeniorAppDatabase.getInstance(applicationContext),
                "fall_pattern",
                "high"
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun snapshotAndReset(now: Long): SensorSnapshot = synchronized(stateLock) {
        val movementScore = if (movementSampleCount > 0) {
            (movementSampleSum / movementSampleCount).coerceIn(0.0, 1.0)
        } else 0.0
        val inactivityDurationSeconds = (now - lastSignificantMovementAt) / 1000

        // Seconds since the screen was last on: 0 while it is on, growing for as long as it
        // stays off. Same running-counter shape as inactivity, and deliberately NOT reset each
        // poll.
        //
        // The per-poll version measured screen-off time *within the window since the last poll*,
        // which sounds equivalent and is not: this device's power management suspends the polling
        // loop, so observed windows ranged from five minutes to three and a half hours. The
        // reading therefore meant "screen-off seconds during however long the OS happened to let
        // us sleep" -- a scale that changes from one row to the next, compared against a baseline
        // authored as a fixed number of minutes. A running counter is the same quantity every
        // time it is read, whenever it is read.
        val screenIdleDurationSeconds = screenOffSince?.let { (now - it) / 1000 } ?: 0L

        val snapshot = SensorSnapshot(
            movementScore = movementScore,
            inactivityDurationSeconds = inactivityDurationSeconds,
            screenIdleDurationSeconds = screenIdleDurationSeconds,
            screenUnlockCount = screenUnlockCount,
            stepCount = latestStepCount,
        )
        movementSampleSum = 0.0
        movementSampleCount = 0
        screenUnlockCount = 0
        snapshot
    }

    private suspend fun collectAndStore() {
        val database = SeniorAppDatabase.getInstance(applicationContext)
        val senior = database.seniorDao().getOnboardedSenior() ?: return
        val onboarding = database.seniorOnboardingDao().getBySeniorId(senior.seniorId) ?: return

        val now = System.currentTimeMillis()
        val snapshot = snapshotAndReset(now)
        val timeBlock = SeedBaselineGenerator.resolveTimeBlock(now, onboarding.wakeTime, onboarding.sleepTime)

        val sensorData = SensorData(
            seniorId = senior.seniorId,
            timestamp = now,
            timeBlock = timeBlock.name.lowercase(),
            movementScore = snapshot.movementScore,
            inactivityDuration = snapshot.inactivityDurationSeconds,
            screenIdleDuration = snapshot.screenIdleDurationSeconds,
            screenUnlockCount = snapshot.screenUnlockCount,
            isCharging = isCurrentlyCharging(),
            stepCount = snapshot.stepCount
        )
        database.sensorDataDao().insert(sensorData)

        MedianMadDetector.evaluate(
            senior.seniorId,
            sensorData,
            onboarding,
            database.baselineDao(),
            database.alertDao()
        ).forEach { alert ->
            AlertResponder.onAlertCreated(applicationContext, database, alert)
        }
    }

    /**
     * Registers a sensor fast enough to see a fall — 50 Hz, against the 5 Hz this service used
     * before Layer 0 existed. A free fall lasts a few hundred milliseconds and the impact spike
     * is over in tens; at the old rate the signature falls between samples entirely.
     *
     * Ten times the sample rate running continuously is the single most likely thing to breach
     * the ≤10% battery target (CLAUDE.md §10), so where the sensor has a hardware FIFO the
     * samples are batched: the sensor hub buffers them and the application processor stays
     * asleep between deliveries instead of waking fifty times a second. The cost is up to
     * [BATCH_LATENCY_US] of detection delay, which the compressed fall response window absorbs.
     * Devices without a FIFO fall back to unbatched delivery.
     */
    private fun registerForFallDetection(sensor: Sensor) {
        val latencyUs = if (sensor.fifoMaxEventCount > 0) BATCH_LATENCY_US else 0
        sensorManager.registerListener(this, sensor, FALL_SAMPLING_PERIOD_US, latencyUs)
    }

    private fun isCurrentlyCharging(): Boolean {
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SEENior Monitoring",
                NotificationManager.IMPORTANCE_MIN
            ).apply { description = "Keeps passive wellness monitoring running in the background." }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SEENior is watching over you")
            .setContentText("Passive monitoring is active. All data stays on this phone.")
            .setSmallIcon(R.drawable.ic_stat_seenior)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "sensor_collection_channel"
        private const val NOTIFICATION_ID = 1001
        private const val POLL_INTERVAL_MS = 5 * 60 * 1000L
        private const val MOVEMENT_THRESHOLD = 0.05

        /** 50 Hz — fast enough to resolve a fall's free-fall and impact phases. */
        private const val FALL_SAMPLING_PERIOD_US = 20_000

        /** How long the sensor hub may buffer samples before waking the CPU with them. */
        private const val BATCH_LATENCY_US = 3_000_000

        /** Keeps the Layer 1 movement signals sampling at their original 5 Hz. */
        private const val MOVEMENT_SAMPLE_INTERVAL_NANOS = 200_000_000L

        /**
         * Whether this service is alive in the current process.
         *
         * Read by [com.pup.seenior.sensors.MonitoringWatchdogJobService] to decide whether
         * monitoring needs restarting. A process kill resets it to false along with everything
         * else in the process, which is exactly the answer the watchdog wants: no process, no
         * monitoring.
         */
        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, SensorCollectionService::class.java)
            context.startForegroundService(intent)
        }
    }

}

