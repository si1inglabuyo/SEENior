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
import androidx.core.app.NotificationCompat
import com.pup.seenior.R
import com.pup.seenior.baseline.SeedBaselineGenerator
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.SensorData
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

    private var movementSampleSum = 0.0
    private var movementSampleCount = 0
    private var lastSignificantMovementAt = System.currentTimeMillis()
    private var latestStepCount = 0
    private var screenUnlockCount = 0
    private var screenOffSince: Long? = null
    private var screenIdleSecondsThisWindow = 0L

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
                    Intent.ACTION_SCREEN_OFF -> screenOffSince = System.currentTimeMillis()
                    Intent.ACTION_SCREEN_ON -> {
                        screenOffSince?.let { screenIdleSecondsThisWindow +=
                            (System.currentTimeMillis() - it) / 1000 }
                        screenOffSince = null
                        }
                    Intent.ACTION_USER_PRESENT -> screenUnlockCount++
                    }
                }
            }
        }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
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
                val deviation = (abs(magnitude - SensorManager.GRAVITY_EARTH) / SensorManager.GRAVITY_EARTH)
                    .coerceIn(0.0f, 1.0f)
                synchronized(stateLock) {
                    movementSampleSum += deviation
                    movementSampleCount++
                    if (deviation > MOVEMENT_THRESHOLD) lastSignificantMovementAt = System.currentTimeMillis()
                }
            }
            Sensor.TYPE_STEP_COUNTER -> synchronized(stateLock) {
                latestStepCount = event.values[0].toInt()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun snapshotAndReset(now: Long): SensorSnapshot = synchronized(stateLock) {
        val movementScore = if (movementSampleCount > 0) {
            (movementSampleSum / movementSampleCount).coerceIn(0.0, 1.0)
        } else 0.0
        val inactivityDurationSeconds = (now - lastSignificantMovementAt) / 1000
        screenOffSince?.let {
            screenIdleSecondsThisWindow += (now - it) / 1000
            screenOffSince = now
        }
        val snapshot = SensorSnapshot(
            movementScore = movementScore,
            inactivityDurationSeconds = inactivityDurationSeconds,
            screenIdleDurationSeconds = screenIdleSecondsThisWindow,
            screenUnlockCount = screenUnlockCount,
            stepCount = latestStepCount,
        )
        movementSampleSum = 0.0
        movementSampleCount = 0
        screenIdleSecondsThisWindow = 0
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
            database.baselineDao(),
            database.alertDao()
        )
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
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "sensor_collection_channel"
        private const val NOTIFICATION_ID = 1001
        private const val POLL_INTERVAL_MS = 5 * 60 * 1000L
        private const val MOVEMENT_THRESHOLD = 0.05

        fun start(context: Context) {
            val intent = Intent(context, SensorCollectionService::class.java)
            context.startForegroundService(intent)
        }
    }

}

