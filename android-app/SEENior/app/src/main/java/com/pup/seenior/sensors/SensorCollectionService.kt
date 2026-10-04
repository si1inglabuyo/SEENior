package com.pup.seenior.sensors

import android.Manifest
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.pup.seenior.R
import com.pup.seenior.alerts.AlertEscalator
import com.pup.seenior.alerts.AlertResponder
import com.pup.seenior.baseline.SeedBaselineGenerator
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.SensorData
import com.pup.seenior.network.HeartbeatReporter
import com.pup.seenior.detection.FallDetector
import com.pup.seenior.detection.MedianMadDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
   private var significantMotion: Sensor? = null

    /** Whether the one-shot significant-motion trigger is armed. Volatile because it is set from the trigger callback and read on another thread. */
    @Volatile
    private var significantMotionArmed = false

    private var movementSampleSum = 0.0
    private var movementSampleCount = 0
    private var lastSignificantMovementAt = System.currentTimeMillis()
    private var latestStepCount = 0

    /**
     * Whether [onCreate] ran all the way through. False if it stopped early because the
     * foreground service was refused, so [onDestroy] must not unregister anything.
     */
    private var startedUp = false

    /**
     * Whether TYPE_STEP_COUNTER has delivered anything this run. The sensor can exist and
     * still never report (for example if ACTIVITY_RECOGNITION was denied), so this differs
     * from `stepCounter != null`.
     */
    private var stepCounterReported = false
    private var screenUnlockCount = 0
    private var screenOffSince: Long? = null

    /** Whether the keyguard was up at the previous sample, to spot a lock-then-unlock between samples. See [snapshotAndReset]. */
    private var keyguardUpAtLastSample = false

    private lateinit var powerManager: PowerManager
    private lateinit var keyguardManager: KeyguardManager

    /** Layer 0. Only used on the sensor callback thread, so it needs no lock. */
    private lateinit var fallDetector: FallDetector
    private var lastMovementSampleNanos = 0L

    // onSensorChanged and screenReceiver run on the main thread; collectAndStore() runs on
    // Dispatchers.Default. Access the counters above only while holding this lock.
    private val stateLock = Any()

    /** Serialises [collectAndStore] so the timer and a server-wake poll cannot interleave. */
    private val collectionMutex = Mutex()

    private data class SensorSnapshot(
        val movementScore: Double,
        val inactivityDurationSeconds: Long,
        val screenIdleDurationSeconds: Long,
        val screenUnlockCount: Int,
        val stepCount: Int,
        /**
         * Whether [movementScore] was actually measured. With no accelerometer callbacks it
         * defaults to zero, which is not the same as "didn't move".
         */
        val movementMeasured: Boolean,
        /**
         * Whether the step counter has ever reported, as opposed to [stepCount] being zero
         * because nothing feeds it. See [reconcileInactivity].
         */
        val stepCountObserved: Boolean,
    )

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            synchronized(stateLock) {
                // INFO, not DEBUG: this ROM drops DEBUG logs. Read with: adb logcat -s SeeniorScreen
                Log.i(TAG_SCREEN, "screen broadcast: " + intent.action)
                when (intent.action) {
                    // Only the first SCREEN_OFF starts the clock; a repeat would restart it.
                    Intent.ACTION_SCREEN_OFF ->
                        if (screenOffSince == null) screenOffSince = System.currentTimeMillis()
                    Intent.ACTION_SCREEN_ON -> screenOffSince = null
                    Intent.ACTION_USER_PRESENT -> screenUnlockCount++
                }
            }
        }
    }

    /**
     * Detects movement while this process is frozen.
     *
     * The accelerometer is a non-wake-up sensor, so when Doze or the OEM freezer suspends the
     * process its samples stop, and inactivity keeps climbing even though the senior may be
     * moving. TYPE_SIGNIFICANT_MOTION is a wake-up sensor handled by the sensor hub, so it
     * still reports and is cheap enough to leave on.
     *
     * It does not feed [movementSampleSum], so `movement_score` stays a pure accelerometer
     * statistic and older baselines remain comparable.
     */
    private val significantMotionListener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent) {
            // A one-shot trigger disables itself when it fires, so clear the flag first.
            significantMotionArmed = false

            // Use the current time, not wallClockOf(event.timestamp): that helper corrects for
            // batching delay on accelerometer samples, and a wake-up trigger has none.
            val movedAt = System.currentTimeMillis()
            synchronized(stateLock) { lastSignificantMovementAt = movedAt }
            // Read with: adb logcat -s SensorWake
            Log.i(TAG_WAKE, "significant motion — inactivity clock reset")

            armSignificantMotion()
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!startForegroundWithLocationIfAllowed()) {
            // Nothing below is safe without a foreground service, so stop before registering
            // anything. The app still opens, which lets the dashboard ask for the permission.
            stopSelf()
            return
        }
        isRunning = true

        // The service can start with the screen already off (boot, or a restart in a pocket),
        // so the idle clock has to be started here.
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        screenOffSince = if (powerManager.isInteractive) null else System.currentTimeMillis()
        keyguardUpAtLastSample = keyguardManager.isKeyguardLocked

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        significantMotion = sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)

        // Rotation confirms a fall, but not every phone has a gyroscope; without one, don't
        // require rotation. The trace is logged from here so FallDetector has no Android
        // imports and can be unit tested. Read with: adb logcat -s FallDetector
        //
        // INFO, not DEBUG, because the test handset drops DEBUG logs.
        fallDetector = FallDetector(
            FallDetector.Config(requireRotation = gyroscope != null),
            trace = { Log.i("FallDetector", it) }
        )
        // Shows the trace path is alive, so an empty log isn't mistaken for broken logging.
        Log.i("FallDetector", "armed: requireRotation=${gyroscope != null}, accelerometer at 50 Hz")

        accelerometer?.let { registerForFallDetection(it) }
        gyroscope?.let { registerForFallDetection(it) }
        // The step counter reports a running total and isn't needed for fall detection, so it
        // stays at the low rate.
        stepCounter?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }

        // Logged so a missing sensor is visible. Read with: adb logcat -s SensorWake
        Log.i(TAG_WAKE, "significant motion available=" + (significantMotion != null))
        armSignificantMotion()

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

        startedUp = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Re-asserted on every start, because location may be granted after the service is
        // already running. Calling startForeground again widens the type in place. The return
        // value is ignored: if widening is refused, only the alert GPS is lost for this run.
        startForegroundWithLocationIfAllowed()
        if (intent?.action == ACTION_POLL_NOW) pollOnce()
        return START_STICKY
    }

    /**
     * Goes to the foreground, claiming the `location` type only when it is allowed.
     *
     * The manifest declares `health|location`, but from Android 14 claiming `location`
     * without a location permission throws, and even with a "while using" grant Android only
     * allows it while the app is in the foreground. So each type is tried in turn
     * (location, health, then no type), any refusal is caught and logged, and the last
     * attempt returns false instead of throwing. [onCreate] then stops the service cleanly,
     * the app still opens, and [MonitoringWatchdogJobService] retries later. On Android 15 the
     * refusal is a `ForegroundServiceStartNotAllowedException` (an IllegalStateException).
     *
     * Without the `location` type Android treats the service as background and refuses
     * the alert GPS request (see [com.pup.seenior.location.AlertLocationCapture]), so
     * [onStartCommand] re-asserts the type on every start.
     *
     * @return true if a foreground service of some type was started.
     */
    @Suppress("InlinedApi")
    private fun startForegroundWithLocationIfAllowed(): Boolean {
        val health = ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        val location = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION

        val preferred = buildList {
            if (hasLocationPermission()) add("health+location" to (health or location))
            add("health" to health)
        }
        for ((label, type) in preferred) {
            try {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
                return true
            } catch (e: RuntimeException) {
                // SecurityException and ForegroundServiceStartNotAllowedException both land here.
                // Read with: adb logcat -s SensorWake
                Log.w(TAG_WAKE, "foreground service type '$label' refused; trying a plainer one", e)
            }
        }
        return try {
            Log.w(TAG_WAKE, "starting monitoring as a typeless foreground service (no alert GPS until next eligible start)")
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), 0)
            true
        } catch (e: RuntimeException) {
            Log.e(TAG_WAKE, "every foreground service type refused, including typeless; monitoring cannot start", e)
            false
        }
    }

    /** Either location permission will do, matching [com.pup.seenior.location.AlertLocationCapture]. */
    private fun hasLocationPermission(): Boolean =
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).any {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * Takes one sample now, because the server said this phone had gone quiet.
     *
     * It listens for a few seconds first: a frozen process gets no accelerometer callbacks,
     * so sampling immediately would record a movement score of 0.0, which looks like a
     * senior who hasn't moved. A partial wake lock keeps the CPU up for that window and is
     * released in `finally`.
     */
    private fun pollOnce() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)

        serviceScope.launch {
            wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
            try {
                delay(LISTEN_WINDOW_MS)
                collectAndStore()
                // Tells the server the nudge worked, which stops further nudges.
                HeartbeatReporter.report(
                    applicationContext,
                    SeniorAppDatabase.getInstance(applicationContext)
                )
            } catch (e: Exception) {
                Log.w(TAG_WAKE, "Wake sample failed", e)
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // Cleared first, so nothing can read a stale `true` while the service tears down.
        isRunning = false
        if (!startedUp) {
            // onCreate stopped early, so there is nothing to unregister.
            super.onDestroy()
            return
        }
        sensorManager.unregisterListener(this)
        // A trigger sensor is cancelled by its own call, not by unregisterListener.
        significantMotion?.let { sensorManager.cancelTriggerSensor(significantMotionListener, it) }
        significantMotionArmed = false
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
                stepCounterReported = true
            }
        }
    }

    /**
     * Feeds the Layer 1 movement signals, decimated back to the 5 Hz used before fall
     * detection raised the accelerometer to 50 Hz, so baselines stay comparable.
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
     * Converts a sensor event's clock to wall-clock time. Batched samples can be seconds
     * old, and inactivity is measured from that instant.
     */
    private fun wallClockOf(eventNanos: Long): Long {
        val ageMillis = ((SystemClock.elapsedRealtimeNanos() - eventNanos) / 1_000_000)
            .coerceAtLeast(0)
        return System.currentTimeMillis() - ageMillis
    }

    /** Layer 0 confirmed a fall. The risk level is fixed at High, with no fuzzy classification. */
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
        // Ask the system for the screen state instead of trusting broadcasts, which this ROM
        // sometimes stops delivering (one run logged 55 rows of idle = 0). The receiver stays,
        // since it is exact when it fires.
        val interactive = powerManager.isInteractive
        if (interactive) {
            screenOffSince = null
        } else if (screenOffSince == null) {
            // Screen is off but we never saw it go off, so start counting from now.
            screenOffSince = now
        }

        // A lower bound, not a count: at most one unlock per sample. Only used when the
        // receiver produced nothing. isKeyguardLocked is used because isDeviceLocked is always
        // false with no PIN.
        val keyguardUp = keyguardManager.isKeyguardLocked
        if (screenUnlockCount == 0 && keyguardUpAtLastSample && !keyguardUp && interactive) {
            screenUnlockCount = 1
        }
        keyguardUpAtLastSample = keyguardUp

        val movementMeasured = movementSampleCount > 0
        val movementScore = if (movementMeasured) {
            (movementSampleSum / movementSampleCount).coerceIn(0.0, 1.0)
        } else 0.0
        val inactivityDurationSeconds = (now - lastSignificantMovementAt) / 1000

        // Seconds since the screen was last on: 0 while on, then growing. It is a running
        // counter and is not reset each poll, so it means the same thing whenever it is read
        // (a per-poll window varied from five minutes to hours because the OS suspends polling).
        val screenIdleDurationSeconds = screenOffSince?.let { (now - it) / 1000 } ?: 0L

        val snapshot = SensorSnapshot(
            movementScore = movementScore,
            inactivityDurationSeconds = inactivityDurationSeconds,
            screenIdleDurationSeconds = screenIdleDurationSeconds,
            screenUnlockCount = screenUnlockCount,
            stepCount = latestStepCount,
            movementMeasured = movementMeasured,
            stepCountObserved = stepCounterReported,
        )
        movementSampleSum = 0.0
        movementSampleCount = 0
        screenUnlockCount = 0
        snapshot
    }

    /**
     * Takes one sample and writes it, unless there is nothing new to say.
     *
     * [pollingJob] and the server-wake [pollOnce] can both run when a frozen phone thaws.
     * The mutex stops them interleaving, the interval check stops the second one writing,
     * and an unmeasured movement score is never stored, so a duplicate can't invent stillness.
     */
    private suspend fun collectAndStore() = collectionMutex.withLock {
        val database = SeniorAppDatabase.getInstance(applicationContext)
        val senior = database.seniorDao().getOnboardedSenior() ?: return@withLock
        val onboarding = database.seniorOnboardingDao().getBySeniorId(senior.seniorId)
            ?: return@withLock

        val now = System.currentTimeMillis()
        val previous = database.sensorDataDao().getLatest(senior.seniorId)

        // Safety net: the trigger normally re-arms itself. This catches an arming that was refused.
        armSignificantMotion()

        // Checked before the snapshot, because snapshotAndReset() drains the accumulator.
        if (previous != null && now - previous.timestamp < MIN_COLLECTION_INTERVAL_MS) {
            return@withLock
        }

        val snapshot = snapshotAndReset(now)
        if (!snapshot.movementMeasured) return@withLock
        val timeBlock = SeedBaselineGenerator.resolveTimeBlock(now, onboarding.wakeTime, onboarding.sleepTime)

        val inactivitySeconds = reconcileInactivity(now, previous, snapshot)

        val sensorData = SensorData(
            seniorId = senior.seniorId,
            timestamp = now,
            timeBlock = timeBlock.name.lowercase(),
            movementScore = snapshot.movementScore,
            inactivityDuration = inactivitySeconds,
            screenIdleDuration = snapshot.screenIdleDurationSeconds,
            screenUnlockCount = snapshot.screenUnlockCount,
            isCharging = isCurrentlyCharging(),
            stepCount = snapshot.stepCount
        )
        database.sensorDataDao().insert(sensorData)

        val findings = MedianMadDetector.evaluate(
            senior.seniorId,
            sensorData,
            onboarding,
            database.baselineDao(),
            database.alertDao()
        )
        findings.created.forEach { alert ->
            AlertResponder.onAlertCreated(applicationContext, database, alert)
        }
        // An alert that got worse while open. Its chain is already running; this only updates
        // the level the family app and dashboard show.
        findings.upgraded.forEach { alertId -> AlertEscalator.syncSeverity(database, alertId) }
    }

    /**
     * Corrects an inactivity reading taken across a gap the process slept through.
     *
     * While the CPU is suspended there are no accelerometer callbacks, so a two-hour freeze
     * reads as 7200 s of inactivity even if the senior was moving. The step counter is the
     * witness: it keeps counting through a suspend, so a rise across the gap proves movement.
     *
     * Time that could not be measured is not counted as stillness:
     * - If steps rose across a slept gap, the reading is capped at the listening window.
     * - If steps did not rise and the counter was observed, the long reading stands.
     * - If the counter never reported ([SensorSnapshot.stepCountObserved] is false), the gap
     *   is capped too, since a flat count then isn't evidence.
     *
     * Erring towards "moved" only delays a detection by one nudge interval; the opposite
     * error is a false alarm.
     */
    private fun reconcileInactivity(
        now: Long,
        previous: SensorData?,
        snapshot: SensorSnapshot,
    ): Long {
        // A decrease means the counter restarted at a reboot, not negative steps.
        val stepsDuringGap = previous?.let { snapshot.stepCount - it.stepCount } ?: 0

        val verdict = InactivityReconciler.reconcile(
            rawSeconds = snapshot.inactivityDurationSeconds,
            gapMillis = previous?.let { now - it.timestamp },
            pollIntervalMs = POLL_INTERVAL_MS,
            listenWindowMs = LISTEN_WINDOW_MS,
            stepCountObserved = snapshot.stepCountObserved,
            stepsDuringGap = stepsDuringGap,
        )
        if (verdict is InactivityReconciler.Verdict.Capped) Log.i(TAG_WAKE, verdict.reason)
        return verdict.seconds
    }

    /**
     * Registers the accelerometer at 50 Hz so a fall (free fall lasts a few hundred ms) isn't
     * missed. To protect the battery target, samples are batched in the sensor hub FIFO when
     * available, up to [BATCH_LATENCY_US] of delay, which the short fall response window
     * absorbs. Devices without a FIFO use unbatched delivery.
     */
    /**
     * Arms the one-shot significant-motion trigger, if the phone has one. There is no
     * fallback; without it the service behaves as before. Failures are logged.
     */
    private fun armSignificantMotion() {
        val sensor = significantMotion ?: return
        if (significantMotionArmed) return

        if (sensorManager.requestTriggerSensor(significantMotionListener, sensor)) {
            significantMotionArmed = true
        } else {
            Log.w(TAG_WAKE, "significant motion sensor refused to arm")
        }
    }

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

        /** Shortest gap between two stored samples. */
        private const val MIN_COLLECTION_INTERVAL_MS = 60 * 1000L
        private const val MOVEMENT_THRESHOLD = 0.05

        /** Trace tag for the screen-state broadcasts, whose delivery is not to be assumed. */
        private const val TAG_SCREEN = "SeeniorScreen"

        /** 50 Hz — fast enough to resolve a fall's free-fall and impact phases. */
        private const val FALL_SAMPLING_PERIOD_US = 20_000

        /** How long the sensor hub may buffer samples before waking the CPU with them. */
        private const val BATCH_LATENCY_US = 3_000_000

        /** Keeps the Layer 1 movement signals sampling at their original 5 Hz. */
        private const val MOVEMENT_SAMPLE_INTERVAL_NANOS = 200_000_000L

        /** Whether this service is alive in the current process; read by [com.pup.seenior.sensors.MonitoringWatchdogJobService]. */
        @Volatile
        var isRunning: Boolean = false
            private set

        /** Log tag for the server-woken sampling path, which has no screen to report to. */
        private const val TAG_WAKE = "SensorWake"

        const val ACTION_POLL_NOW = "com.pup.seenior.action.POLL_NOW"

        /** How long to listen to the accelerometer before sampling on a server wake. */
        private const val LISTEN_WINDOW_MS = 12_000L

        /** Ceiling on the wake lock, so a sample that hangs cannot hold the CPU up all night. */
        private const val WAKE_LOCK_TIMEOUT_MS = 60_000L

        private const val WAKE_LOCK_TAG = "seenior:wake-sample"

        fun start(context: Context) {
            val intent = Intent(context, SensorCollectionService::class.java)
            context.startForegroundService(intent)
        }

        /**
         * Stops passive monitoring, used when the senior deletes their account. The watchdog
         * won't restart it because the wiped database has no onboarded senior.
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, SensorCollectionService::class.java))
        }

        /**
         * Asks for one immediate sample, starting the service if needed. Called from
         * [com.pup.seenior.alerts.SeeniorMessagingService] when the server says this phone has
         * gone quiet.
         */
        fun pollNow(context: Context) {
            val intent = Intent(context, SensorCollectionService::class.java)
                .setAction(ACTION_POLL_NOW)
            context.startForegroundService(intent)
        }
    }

}

