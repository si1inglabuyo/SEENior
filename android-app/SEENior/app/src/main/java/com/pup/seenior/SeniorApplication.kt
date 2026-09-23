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
 * Whether any of this app's screens is currently in front of the senior.
 *
 * Read by [com.pup.seenior.alerts.AlertResponder] to decide whether a new alert needs a
 * notification or whether the wellness prompt will surface it on its own.
 */
object AppForeground {
    @Volatile
    var isForeground: Boolean = false
        internal set
}

class SeniorApplication : Application() {

    /** Outlives every screen on purpose — a check-in must not be cancelled by the senior
     *  navigating away a moment after opening the app. */
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
     * Clears the extra senior rows left behind by the duplicate-onboarding bug.
     *
     * Onboarding used to insert a new senior every time its final screen re-entered composition,
     * so a senior who walked back through the permission chain ended up as several people in
     * their own database -- five, in six minutes, on the realme tester handset on 2026-09-18.
     * [com.pup.seenior.ui.onboarding.OnboardingViewModel.submitOnboarding] no longer does that,
     * but every phone already running the old build carries the rows, and each dead one holds
     * twenty seed Baseline rows and possibly an orphan Sensor_Data row that no nightly pass will
     * ever roll up, because aggregation only sweeps the senior the app considers current.
     *
     * The row kept is whatever `getOnboardedSenior()` returns -- deliberately the same selector
     * every other caller in the app already follows, so this can never delete the row the rest
     * of the app is using. Everything with an Alert or a Daily_Aggregate against it is left
     * alone regardless (see [SeniorDao.findDuplicateSeniorIds]).
     *
     * Runs on the app's own scope rather than as a Room migration: it is a data repair, not a
     * schema change, and it has to be safe to run on every start and do nothing on the second.
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

    /**
     * Counts started activities rather than using ProcessLifecycleOwner, which would mean pulling
     * in lifecycle-process for a single boolean this app can observe directly.
     */
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
     * The recovery net for passive monitoring (see [MonitoringWatchdogJobService]).
     *
     * Called from here, which means it runs on every process start — but the job is not
     * *created* here in any meaningful sense. It is persisted, so JobScheduler already holds it
     * across reboots, and that is the whole point: it has to be in the system's store before the
     * reboot that this app's own boot receiver will not survive. [MonitoringWatchdogJobService]
     * leaves a matching registration alone rather than restarting its clock.
     *
     * Note this is raw JobScheduler, not WorkManager like the aggregation job below. That is not
     * inconsistency: WorkManager does not persist its jobs and recovers them from a
     * `BOOT_COMPLETED` receiver, which is the mechanism the watchdog exists to route around.
     * Nightly aggregation has no such requirement — a missed run rolls into the next one.
     */
    private fun scheduleMonitoringWatchdog() {
        MonitoringWatchdogJobService.schedule(this)
    }

    /**
     * Checks in the moment the senior opens the app.
     *
     * The watchdog's fifteen-minute pass is the reliable channel and stays the reliable channel;
     * this exists because fifteen minutes is a long time to wait to find out a phone is at 4%,
     * and because opening the app is the one moment we know for certain the phone is awake, has
     * a live process and is probably on a network. It is also what makes the family's view
     * answer on demand rather than on a timer.
     *
     * Rate limited because [android.app.Activity] starts are not rare — a senior flicking
     * between apps would otherwise post a check-in per flick, and the number will not have
     * changed. A minute is far below the fifteen the watchdog runs at and far above anything a
     * person does by hand.
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
        // Twice a day, not once. A night block does not close until wake time, so a single 02:00
        // run always finds it still open, defers it correctly, and only rolls it up twenty-four
        // hours later. The second run lands after the senior is up and closes the night the same
        // day.
        val request = PeriodicWorkRequestBuilder<NightlyAggregationWorker>(12, TimeUnit.HOURS)
            .setInitialDelay(millisUntilNext2AM(), TimeUnit.MILLISECONDS)
            .build()

        // UPDATE, not KEEP: the 24-hour version is already enqueued under this name on every
        // installed build, and KEEP would silently leave it there.
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
