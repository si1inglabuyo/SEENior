package com.pup.seenior.sensors

import android.app.KeyguardManager
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
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
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

    /**
     * Whether the one-shot trigger below is currently armed.
     *
     * Volatile rather than under [stateLock]: it is set from the trigger callback and read from
     * [collectAndStore] on another thread, and it guards nothing but itself.
     */
    @Volatile
    private var significantMotionArmed = false

    private var movementSampleSum = 0.0
    private var movementSampleCount = 0
    private var lastSignificantMovementAt = System.currentTimeMillis()
    private var latestStepCount = 0
    private var screenUnlockCount = 0
    private var screenOffSince: Long? = null

    /**
     * Whether the keyguard was up at the previous sample, so a lock-then-unlock across the gap can
     * be spotted without [Intent.ACTION_USER_PRESENT] ever arriving. See [snapshotAndReset].
     */
    private var keyguardUpAtLastSample = false

    private lateinit var powerManager: PowerManager
    private lateinit var keyguardManager: KeyguardManager

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

    /** Serialises [collectAndStore] so the timer and a server-wake poll cannot interleave. */
    private val collectionMutex = Mutex()

    private data class SensorSnapshot(
        val movementScore: Double,
        val inactivityDurationSeconds: Long,
        val screenIdleDurationSeconds: Long,
        val screenUnlockCount: Int,
        val stepCount: Int,
        /**
         * Whether [movementScore] was measured at all, as opposed to defaulting to zero because
         * no accelerometer callback had arrived.
         *
         * The two are not the same claim and only one of them is evidence. A senior lying
         * perfectly still still produces callbacks -- gravity keeps the sensor reporting at its
         * registered rate -- so no callbacks means nobody was listening, not that nobody moved.
         */
        val movementMeasured: Boolean,
    )

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            synchronized(stateLock) {
                // INFO, not DEBUG: this ROM drops DEBUG system-wide (see the FallDetector note in
                // onCreate). Read with: adb logcat -s SeeniorScreen
                Log.i(TAG_SCREEN, "screen broadcast: " + intent.action)
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

    /**
     * The one witness to movement that keeps working while this process is frozen.
     *
     * [lastSignificantMovementAt] is otherwise only ever written from an accelerometer callback,
     * and the accelerometer is a *non-wake-up* sensor: when Doze or the OEM freezer suspends this
     * process, its samples stop being delivered at all. Inactivity then keeps climbing for a
     * reason that has nothing to do with the senior — nobody was listening. That is the same
     * interference already documented as killing the five-minute polling loop and the alarms.
     *
     * Measured on the pilot handset 2026-09-02: alert 25 claimed sixty-eight minutes of stillness
     * across a period the phone was in use. The step counter, the existing witness in
     * [reconcileInactivity], could not correct it because the phone was on a desk rather than
     * carried, so it counted no steps either.
     *
     * TYPE_SIGNIFICANT_MOTION is detected inside the sensor hub and is a **wake-up** sensor: it
     * wakes the application processor to deliver, so it reports movement the rest of this class
     * is asleep for. Running in hardware is also why it can be left on permanently against the
     * ≤10% battery target (CLAUDE.md §10).
     *
     * It deliberately does **not** feed [movementSampleSum]. `movement_score` stays a pure
     * accelerometer statistic, so a baseline built before this change stays comparable with
     * readings taken after it — the same argument [recordMovementSample] makes for decimating
     * back to 5 Hz.
     */
    private val significantMotionListener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent) {
            // A one-shot trigger disables itself on firing and reports nothing further until it
            // is asked again, so this flag drops before anything else can read it.
            significantMotionArmed = false

            // System.currentTimeMillis() rather than wallClockOf(event.timestamp): that helper
            // exists to undo the batching latency on accelerometer samples, which can be seconds
            // old by the time they arrive. A wake-up trigger has none to undo — it wakes the CPU
            // to deliver — and reading a HAL timestamp here only adds a way to be wrong.
            val movedAt = System.currentTimeMillis()
            synchronized(stateLock) { lastSignificantMovementAt = movedAt }
            // Read with: adb logcat -s SensorWake
            Log.i(TAG_WAKE, "significant motion — inactivity clock reset")

            armSignificantMotion()
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        isRunning = true

        // The service can start while the screen is already off (boot, or a restart with the
        // phone in a pocket). Without this the idle clock never starts, because the SCREEN_OFF
        // that would have started it already happened.
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        screenOffSince = if (powerManager.isInteractive) null else System.currentTimeMillis()
        keyguardUpAtLastSample = keyguardManager.isKeyguardLocked

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        significantMotion = sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)

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

        // Stated up front so an absent sensor is visible in the log rather than inferred later
        // from an inactivity reading that never resets. Read with: adb logcat -s SensorWake
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_POLL_NOW) pollOnce()
        return START_STICKY
    }

    /**
     * Takes one sample now, because the server said this phone had gone quiet.
     *
     * **The listening window is the point of this method.** A frozen process receives no
     * accelerometer callbacks, so waking and sampling immediately would read `movementScore`
     * as 0.0 -- indistinguishable from a senior who has not moved a muscle, and pointed at
     * exactly the half of the distribution [MedianMadDetector] treats as worrying. That
     * would manufacture the false alarms this app spent 2026-08-29 removing. Listening for
     * a few seconds first means the number written is one that was actually measured.
     *
     * A partial wake lock holds the CPU up for that window. Without it the handset is free
     * to suspend again the moment FCM's brief allowlist lapses, halfway through the sample.
     * It is released in `finally`: a leaked wake lock on a senior's phone is a flat battery
     * by morning, which is a worse failure than the one this is fixing.
     */
    private fun pollOnce() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)

        serviceScope.launch {
            wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
            try {
                delay(LISTEN_WINDOW_MS)
                collectAndStore()
                // Tells the server the nudge worked, which is also what stops it nudging
                // again on the next sweep.
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
        sensorManager.unregisterListener(this)
        // A trigger sensor is not covered by unregisterListener; it is cancelled by its own call
        // or it stays armed against a listener whose service is gone.
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
        // Ask the system what it is doing rather than trusting that it told us.
        //
        // Both screen figures below were kept only by broadcasts, and on 2026-09-01 the pilot
        // handset produced 55 consecutive rows reading idle = 0 and unlocks = 0 across thirteen
        // hours -- with the service process alive the whole time and inactivity_duration, kept in
        // this same object under this same lock, climbing normally. The receiver is registered and
        // its other counters work; the broadcasts are not arriving. That is the same OEM
        // interference that took out the alarms and the five-minute polling loop, and it is not
        // something this app can argue with. It can stop depending on it.
        //
        // The receiver stays -- when it fires it is exact, and this only fills the gaps.
        val interactive = powerManager.isInteractive
        if (interactive) {
            screenOffSince = null
        } else if (screenOffSince == null) {
            // The screen is off and we never saw it go off, so the honest starting point is now.
            // Costs at most one sampling interval of idle time and corrects itself next sample.
            screenOffSince = now
        }

        // A lower bound, deliberately, and never a count: at one sample every fifteen minutes this
        // can see at most one unlock per sample. Used only when the receiver produced nothing, so
        // a working USER_PRESENT is never doubled up. isKeyguardLocked rather than isDeviceLocked
        // because the latter is always false on a handset with no PIN, which would read zero for
        // exactly the seniors least likely to have one.
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
            movementMeasured = movementMeasured,
        )
        movementSampleSum = 0.0
        movementSampleCount = 0
        screenUnlockCount = 0
        snapshot
    }

    /**
     * Takes one sample and writes it, unless there is nothing new to say.
     *
     * Two callers race here in practice: [pollingJob]'s timer and the server-wake [pollOnce].
     * When a frozen handset thaws, the suspended `delay` completes and the queued FCM nudge runs
     * within milliseconds of each other. Both used to write, and because [snapshotAndReset] drains
     * the accelerometer accumulator, the second row always claimed `movement_score = 0.0`.
     * Measured on the pilot handset on 2026-09-01: **21 of 79 rows** were such phantoms, dragging
     * every block's average movement toward zero and teaching the baseline a senior who moves half
     * as much as she does.
     *
     * Both guards below are needed. The mutex stops the two collections interleaving; the
     * interval check stops the second one writing at all; and refusing to store an unmeasured
     * movement score means that even if a duplicate slips through both, it cannot invent stillness.
     */
    private suspend fun collectAndStore() = collectionMutex.withLock {
        val database = SeniorAppDatabase.getInstance(applicationContext)
        val senior = database.seniorDao().getOnboardedSenior() ?: return@withLock
        val onboarding = database.seniorOnboardingDao().getBySeniorId(senior.seniorId)
            ?: return@withLock

        val now = System.currentTimeMillis()
        val previous = database.sensorDataDao().getLatest(senior.seniorId)

        // Safety net, not the normal path: the trigger re-arms itself the instant it fires. This
        // only catches an arming that was refused while the process was in a state the sensor
        // service would not accept it, which would otherwise leave the witness permanently mute
        // with nothing but one warning line to say so.
        armSignificantMotion()

        // Checked before the snapshot, never after: snapshotAndReset() drains the accumulator, so
        // bailing out afterwards would throw away real movement the next sample should have had.
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
        // An alert that got worse while it was open. Its chain is already running, so nothing is
        // started again -- but the family app and the barangay dashboard are still showing the
        // level it was posted with, and only this corrects that.
        findings.upgraded.forEach { alertId -> AlertEscalator.syncSeverity(database, alertId) }
    }

    /**
     * Corrects an inactivity reading taken across a gap the process slept through.
     *
     * `inactivityDuration` is measured from the last accelerometer callback that crossed
     * [MOVEMENT_THRESHOLD]. While the CPU is suspended there are no callbacks, so after a
     * two-hour freeze the figure reads 7200 seconds -- not because the senior was still,
     * but because nobody was listening. Handing that to Layer 1 would raise an inactivity
     * alert about a period the phone did not observe.
     *
     * The step counter is the witness. TYPE_STEP_COUNTER is a hardware counter: it keeps
     * counting through a suspend and reports its running total when the CPU comes back, so
     * a rise across the gap is proof the senior moved during it. That is precisely the
     * complementary role CLAUDE.md 4 gives it.
     *
     * So: **time that could not be measured is not counted as stillness.** If steps rose
     * across a slept gap the reading is capped at the listening window, because the last
     * proof of movement lies somewhere inside the gap and its exact moment is unknowable.
     * If steps did not rise the long reading stands, because the counter was awake and
     * agrees with it.
     *
     * Erring towards "she moved" is deliberate. The cost is a detection delayed by one
     * nudge interval, since the next sample finds the steps flat and the clock running
     * again from here. The opposite error is an alarm about a senior who was walking
     * around, and this system has already been measured doing that.
     */
    private fun reconcileInactivity(
        now: Long,
        previous: SensorData?,
        snapshot: SensorSnapshot,
    ): Long {
        if (previous == null) return snapshot.inactivityDurationSeconds

        val gapMillis = now - previous.timestamp
        if (gapMillis <= POLL_INTERVAL_MS * 2) return snapshot.inactivityDurationSeconds

        // A reboot restarts the counter from zero, so a decrease is a reboot boundary and
        // not a negative number of steps. Same rule the nightly aggregation applies to the
        // same sensor.
        val stepsDuringGap = snapshot.stepCount - previous.stepCount
        if (stepsDuringGap <= 0) return snapshot.inactivityDurationSeconds

        Log.i(
            TAG_WAKE,
            "Slept " + (gapMillis / 1000) + "s with " + stepsDuringGap + " step(s); " +
                "capping inactivity at " + (LISTEN_WINDOW_MS / 1000) + "s"
        )
        return minOf(snapshot.inactivityDurationSeconds, LISTEN_WINDOW_MS / 1000)
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
    /**
     * Arms the one-shot significant-motion trigger, if this handset has one.
     *
     * Not every device implements it and there is no fallback worth building: without it the
     * service behaves exactly as it did before, which is the situation this improves on rather
     * than depends on. Both failure paths are logged, because a witness that silently stopped
     * reporting looks identical to a senior who genuinely has not moved.
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

        /**
         * Shortest gap between two stored samples.
         *
         * Well under [POLL_INTERVAL_MS], so it never suppresses a scheduled sample, and far above
         * the milliseconds that separate a timer poll from a server-wake poll landing together.
         */
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

        /** Log tag for the server-woken sampling path, which has no screen to report to. */
        private const val TAG_WAKE = "SensorWake"

        const val ACTION_POLL_NOW = "com.pup.seenior.action.POLL_NOW"

        /**
         * How long to listen to the accelerometer before sampling on a server wake.
         *
         * Long enough for the 5 Hz movement sampler to gather a real average, short enough
         * to finish inside the allowlist a high-priority FCM message grants its receiver.
         */
        private const val LISTEN_WINDOW_MS = 12_000L

        /** Ceiling on the wake lock, so a sample that hangs cannot hold the CPU up all night. */
        private const val WAKE_LOCK_TIMEOUT_MS = 60_000L

        private const val WAKE_LOCK_TAG = "seenior:wake-sample"

        fun start(context: Context) {
            val intent = Intent(context, SensorCollectionService::class.java)
            context.startForegroundService(intent)
        }

        /**
         * Asks for one immediate sample, starting the service first if it is not running.
         *
         * Called from [com.pup.seenior.alerts.SeeniorMessagingService] when the server says
         * this phone has gone quiet. Starting a foreground service from the background is
         * allowed here on two grounds that both have to hold: the battery-optimisation
         * exemption taken during onboarding, and the temporary allowlist a high-priority
         * FCM message grants its receiver.
         */
        fun pollNow(context: Context) {
            val intent = Intent(context, SensorCollectionService::class.java)
                .setAction(ACTION_POLL_NOW)
            context.startForegroundService(intent)
        }
    }

}

