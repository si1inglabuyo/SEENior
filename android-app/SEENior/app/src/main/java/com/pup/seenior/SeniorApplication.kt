package com.pup.seenior

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.diagnostics.CrashReporting
import com.pup.seenior.network.HeartbeatReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.pup.seenior.aggregation.NightlyAggregationWorker
import com.pup.seenior.sensors.MonitoringWatchdogJobService
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Whether any of this app's screens is in front of the senior. Read by
 * [com.pup.seenior.alerts.AlertResponder] to decide whether a new alert needs a notification.
 */
object AppForeground {
    @Volatile
    var isForeground: Boolean = false
        internal set
}

class SeniorApplication : Application() {

    /** Outlives every screen, so a check-in isn't cancelled when the senior navigates away. */
    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var lastForegroundHeartbeatAt = 0L

    override fun onCreate() {
        super.onCreate()
        // First, so a crash in anything below it is still reported.
        CrashReporting.start(this)
        scheduleNightlyAggregation()
        scheduleMonitoringWatchdog()
        trackForegroundState()
        removeDuplicateSeniors()
    }

    /**
     * Clears the extra senior rows left by an old duplicate-onboarding bug (re-entering the
     * final screen inserted a new senior each time). The row kept is whatever
     * `getOnboardedSenior()` returns, the same selector the rest of the app uses. Rows with an
     * Alert or Daily_Aggregate are left alone (see [SeniorDao.findDuplicateSeniorIds]). It is a
     * data repair, not a Room migration, and does nothing on later runs.
     */
    private fun removeDuplicateSeniors() {
        appScope.launch {
            runCatching {
                val db = SeniorAppDatabase.getInstance(this@SeniorApplication)
                val keep = db.seniorDao().getOnboardedSenior() ?: return@runCatching
                val duplicates = db.seniorDao().findDuplicateSeniorIds(keep.seniorId)
                if (duplicates.isEmpty()) return@runCatching
                db.seniorDao().deleteByIds(duplicates)
                Log.i(TAG, "Removed ${duplicates.size} duplicate senior row(s): $duplicates")
            }.onFailure {
                // A tidy-up must never be the reason the app fails to start.
                Log.w(TAG, "Duplicate senior cleanup failed", it)
            }
        }
    }

    /** Counts started activities instead of using ProcessLifecycleOwner, to avoid a dependency for one boolean. */
    private fun trackForegroundState() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var startedActivities = 0

            override fun onActivityStarted(activity: Activity) {
                val wasInBackground = startedActivities == 0
                startedActivities++
                AppForeground.isForeground = true
                if (wasInBackground) reportHeartbeatOnForeground()
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities--
                if (startedActivities <= 0) AppForeground.isForeground = false
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    /**
     * Schedules the recovery net for passive monitoring (see [MonitoringWatchdogJobService]).
     * It runs on every process start but the job is persisted, so JobScheduler already holds
     * it across reboots; a matching registration is left alone. This is raw JobScheduler
     * rather than WorkManager, which doesn't persist jobs and recovers them from the boot
     * broadcast the watchdog routes around. Nightly aggregation has no such need.
     */
    private fun scheduleMonitoringWatchdog() {
        MonitoringWatchdogJobService.schedule(this)
    }

    /**
     * Checks in the moment the senior opens the app. The watchdog's 15-minute pass remains the
     * reliable channel; this makes the family's view answer on demand. Rate limited to once a
     * minute, since activity starts are frequent and the value won't have changed.
     */
    private fun reportHeartbeatOnForeground() {
        val now = System.currentTimeMillis()
        if (now - lastForegroundHeartbeatAt < FOREGROUND_HEARTBEAT_MIN_INTERVAL_MS) return
        lastForegroundHeartbeatAt = now

        appScope.launch {
            HeartbeatReporter.report(this@SeniorApplication, SeniorAppDatabase.getInstance(this@SeniorApplication))
        }
    }

    private fun scheduleNightlyAggregation() {
        // Twice a day: the night block doesn't close until wake time, so a single 02:00 run
        // would only roll it up 24 hours later. The second run closes it the same day.
        val request = PeriodicWorkRequestBuilder<NightlyAggregationWorker>(12, TimeUnit.HOURS)
            .setInitialDelay(millisUntilNext2AM(), TimeUnit.MILLISECONDS)
            .build()

        // UPDATE, not KEEP: the 24-hour version is already enqueued on installed builds.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "nightly_aggregation",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun millisUntilNext2AM(): Long {
        val now = Calendar.getInstance()
        val next2AM = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        return next2AM.timeInMillis - now.timeInMillis
    }

    private companion object {
        const val TAG = "SeniorApplication"

        /** Shortest gap between two foreground-triggered check-ins. */
        const val FOREGROUND_HEARTBEAT_MIN_INTERVAL_MS = 60_000L
    }
}
