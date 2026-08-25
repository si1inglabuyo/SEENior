package com.pup.seenior

import android.app.Activity
import android.app.Application
import android.os.Bundle
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

    override fun onCreate() {
        super.onCreate()
        scheduleNightlyAggregation()
        scheduleMonitoringWatchdog()
        trackForegroundState()
    }

    /**
     * Counts started activities rather than using ProcessLifecycleOwner, which would mean pulling
     * in lifecycle-process for a single boolean this app can observe directly.
     */
    private fun trackForegroundState() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var startedActivities = 0

            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                AppForeground.isForeground = true
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

    private fun scheduleNightlyAggregation() {
        val request = PeriodicWorkRequestBuilder<NightlyAggregationWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(millisUntilNext2AM(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "nightly_aggregation",
            ExistingPeriodicWorkPolicy.KEEP,
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
}
