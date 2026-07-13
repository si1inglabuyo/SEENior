# Step 4 — SensorCollectionService (copy-in instructions)

Manifest is already wired (uncommitted): permissions + `<service android:name=".sensors.SensorCollectionService" .../>` in
`android-app/SEENior/app/src/main/AndroidManifest.xml`. Nothing to change there.

## What this service does

- Runs as a **Foreground Service** (required for continuous background sensor access on API 26+ without
  the OS killing the process — CLAUDE.md §9).
- Registers listeners for `TYPE_ACCELEROMETER` (movement) and `TYPE_STEP_COUNTER` (steps), and a
  `BroadcastReceiver` for `ACTION_SCREEN_ON` / `ACTION_SCREEN_OFF` / `ACTION_USER_PRESENT` (screen idle +
  unlock count).
- Every 5 minutes (`POLL_INTERVAL_MS`) it aggregates whatever it collected since the last poll into one
  `SensorData` row and inserts it via Room, then resets the accumulators for the next window.
- `movement_score` = average of `|accelerometer magnitude − gravity| / gravity`, clamped to 0.0–1.0 — a
  simple, cheap proxy for movement intensity that matches the field's documented range.
- `inactivity_duration` = seconds since the last sample that exceeded `MOVEMENT_THRESHOLD` — i.e. how long
  it's been since anything that looked like real movement.
- `time_block` is resolved with the `SeedBaselineGenerator.resolveTimeBlock(...)` helper you just added, using
  the senior's onboarding `wake_time` / `sleep_time`.
- Charging state is read from the sticky `ACTION_BATTERY_CHANGED` intent at poll time (no extra receiver
  needed for that one).

This intentionally does **not** touch gyroscope/fall detection — that's Layer 0, a separate real-time
listener (build-order step 10), not part of the 5-minute polling loop.

## 1. Create the new file

Create the folder `android-app/SEENior/app/src/main/java/com/pup/seenior/sensors/` and inside it a new file
`SensorCollectionService.kt`:

```kotlin
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

class SensorCollectionService : Service(), SensorEventListener {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var pollingJob: Job? = null

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var stepCounter: Sensor? = null

    // Accumulated since the last poll; reset after each write.
    private var movementSampleSum = 0.0
    private var movementSampleCount = 0
    private var lastSignificantMovementAt = System.currentTimeMillis()
    private var latestStepCount = 0
    private var screenUnlockCount = 0
    private var screenOffSince: Long? = null
    private var screenIdleSecondsThisWindow = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> screenOffSince = System.currentTimeMillis()
                Intent.ACTION_SCREEN_ON -> {
                    screenOffSince?.let { screenIdleSecondsThisWindow += (System.currentTimeMillis() - it) / 1000 }
                    screenOffSince = null
                }
                Intent.ACTION_USER_PRESENT -> screenUnlockCount++
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
                    .coerceIn(0.0, 1.0)
                movementSampleSum += deviation
                movementSampleCount++
                if (deviation > MOVEMENT_THRESHOLD) lastSignificantMovementAt = System.currentTimeMillis()
            }
            Sensor.TYPE_STEP_COUNTER -> latestStepCount = event.values[0].toInt()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private suspend fun collectAndStore() {
        val database = SeniorAppDatabase.getInstance(applicationContext)
        val senior = database.seniorDao().getOnboardedSenior() ?: return
        val onboarding = database.seniorOnboardingDao().getBySeniorId(senior.seniorId) ?: return

        val now = System.currentTimeMillis()
        val movementScore = if (movementSampleCount > 0) {
            (movementSampleSum / movementSampleCount).coerceIn(0.0, 1.0)
        } else 0.0
        val inactivityDurationSeconds = (now - lastSignificantMovementAt) / 1000
        screenOffSince?.let {
            screenIdleSecondsThisWindow += (now - it) / 1000
            screenOffSince = now
        }

        val timeBlock = SeedBaselineGenerator.resolveTimeBlock(now, onboarding.wakeTime, onboarding.sleepTime)

        database.sensorDataDao().insert(
            SensorData(
                seniorId = senior.seniorId,
                timestamp = now,
                timeBlock = timeBlock.name.lowercase(),
                movementScore = movementScore,
                inactivityDuration = inactivityDurationSeconds,
                screenIdleDuration = screenIdleSecondsThisWindow,
                screenUnlockCount = screenUnlockCount,
                isCharging = isCurrentlyCharging(),
                stepCount = latestStepCount
            )
        )

        movementSampleSum = 0.0
        movementSampleCount = 0
        screenIdleSecondsThisWindow = 0
        screenUnlockCount = 0
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
            .setSmallIcon(R.mipmap.ic_launcher)
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
```

## 2. Wire it up so it actually starts

Right now nothing calls `SensorCollectionService.start(context)`. The natural spot is
`AllSetScreen.kt` — it already shows a checklist row that says "Passive monitoring started", so this
just makes that claim true.

In `android-app/SEENior/app/src/main/java/com/pup/seenior/ui/onboarding/AllSetScreen.kt`:

**Add this import** near the top with the other imports:
```kotlin
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import com.pup.seenior.sensors.SensorCollectionService
```

**Inside the `AllSetScreen` composable**, grab the context and start the service once onboarding finishes.
Change:
```kotlin
@Composable
fun AllSetScreen(
    viewModel: OnboardingViewModel,
    onContinue: () -> Unit
) {
    var isSaving by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        viewModel.submitOnboarding()
        isSaving = false
    }
```
to:
```kotlin
@Composable
fun AllSetScreen(
    viewModel: OnboardingViewModel,
    onContinue: () -> Unit
) {
    var isSaving by remember { mutableStateOf(true) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.submitOnboarding()
        SensorCollectionService.start(context)
        isSaving = false
    }
```

That's the minimum to get data flowing into `Sensor_Data`. Note: this only starts the service for the
current app process lifetime — restarting it after a device reboot (`BOOT_COMPLETED` receiver) or after the
app is swiped away isn't handled yet; that's a small separate piece we can add later if you want it.

## 3. Sanity checks once you've typed it in

- Build the project (`./gradlew assembleDebug` from `android-app/SEENior`) — this is the fastest way to
  catch typos.
- Package name check: the file must live at
  `app/src/main/java/com/pup/seenior/sensors/SensorCollectionService.kt` so it matches
  `com.pup.seenior.sensors.SensorCollectionService` in the manifest.
- Run on a device/emulator, complete onboarding, then check Logcat or use DB Browser for SQLite on a pulled
  copy of `senior_app.db` — you should see a new `Sensor_Data` row roughly every 5 minutes.
