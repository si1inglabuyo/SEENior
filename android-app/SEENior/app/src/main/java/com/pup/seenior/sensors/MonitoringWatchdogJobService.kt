package com.pup.seenior.sensors

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.pup.seenior.alerts.EscalationScheduler
import com.pup.seenior.database.SeniorAppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Restarts passive monitoring when it has stopped, on a repeating schedule that survives a reboot.
 *
 * This exists because [BootReceiver] does not run on every handset. Measured on an Infinix X6885
 * (Android 15, XOS): after a reboot there is no app process, no sensor service and no `rearmAll`
 * log — twice, the second time with the OEM's own Auto-launch toggle enabled — while every
 * Android-side gate was open (permission declared, receiver registered with the system,
 * `stopped=false`, battery-optimisation whitelisted, standby bucket EXEMPTED). Transsion drops
 * `BOOT_COMPLETED` below the level Android itself controls, and the autostart list that decides
 * this is owned by `com.transsion.phonemaster` and is neither readable nor settable over adb. So
 * the app was simply *not monitoring* after a restart until somebody opened it by hand — which
 * contradicts the "fully passive" claim in CLAUDE.md §1 and belongs in §12 as well.
 *
 * A **persisted** JobScheduler job is a different road to the same place. The system writes it to
 * its own store, outside this app, and restores and runs it after a reboot — starting this process
 * in order to do so. That restore does not depend on our receiver being allowed to hear the boot
 * broadcast. `setPersisted(true)` requires RECEIVE_BOOT_COMPLETED, which the manifest already
 * declares for [BootReceiver].
 *
 * This is deliberately raw JobScheduler rather than WorkManager, which is what the first attempt
 * used. WorkManager does not persist its jobs: `dumpsys jobscheduler` on the same handset showed
 * our `PeriodicWorkRequest` registered with neither the `PERSISTED` nor the `PERIODIC` flag, on a
 * device where 171 other jobs carried `PERSISTED`. It reschedules its own work after a restart
 * from a `BOOT_COMPLETED` receiver of its own — the very broadcast this class exists to route
 * around. Its retry and constraint handling are better than what is here, and irrelevant if the
 * job is gone.
 *
 * It is a weaker promise than an alarm, and deliberately so: this is a recovery net, not a
 * deadline. The platform may run a periodic job late, and fifteen minutes is the shortest period
 * it accepts, so the worst case is a window of roughly that long with no monitoring after a
 * restart. That is a great deal better than "until the senior happens to open the app", and it
 * costs one short wake-up per quarter hour against the §10 battery budget. The escalation deadline
 * itself stays on [EscalationScheduler]'s alarm clock, which is the one thing a deferrable job is
 * measurably no good at.
 *
 * If a persisted job turns out not to survive a reboot here either, the fallback is an FCM wake
 * from the server, which needs the senior's device to register a push token first — it does not
 * today.
 */
class MonitoringWatchdogJobService : JobService() {

    // onStartJob is called on the main thread and must return promptly, so the pass runs here.
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pass: Job? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        pass = scope.launch {
            try {
                runPass()
            } catch (e: Exception) {
                Log.e(TAG, "Watchdog pass failed", e)
            } finally {
                // The reschedule flag is ignored for a periodic job: a pass that could not do its
                // work waits for the next period rather than backing off. Fifteen minutes is an
                // acceptable wait for a net that only matters when something else has already
                // gone wrong.
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        pass?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runPass() {
        val app = applicationContext
        val db = SeniorAppDatabase.getInstance(app)

        // Nothing to monitor before onboarding finishes, and starting the service early would put
        // a permanent notification in front of a senior who has not agreed to anything yet.
        if (db.seniorDao().getOnboardedSenior() == null) {
            Log.i(TAG, "No onboarded senior; watchdog standing down")
            return
        }

        if (SensorCollectionService.isRunning) {
            Log.i(TAG, "Sensor service already running")
        } else {
            try {
                SensorCollectionService.start(app)
                Log.i(TAG, "Sensor service was down; restarted by watchdog")
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException (API 31+) is an IllegalStateException,
                // so catching the parent keeps this compiling at minSdk 26. A running job is a
                // background proc state, so the start is only permitted because of the
                // battery-optimisation exemption asked for during onboarding — if the senior
                // declined, or an OEM ignores it, this is where that shows up. Logged rather than
                // swallowed: the degradation is otherwise invisible and looks exactly like the
                // reboot bug this class was written for.
                Log.w(TAG, "Not allowed to start the sensor service from the background", e)
            }
        }

        // Alarms are lost on reboot and on force-stop. Anything still open needs its deadline put
        // back, or it waits for a wake-up that is never coming.
        EscalationScheduler.rearmAll(app)
    }

    companion object {
        private const val TAG = "MonitoringWatchdog"

        /** Stable across reboots by definition — a persisted job is restored under this id. */
        private const val JOB_ID = 4201

        private val INTERVAL_MS = TimeUnit.MINUTES.toMillis(15)

        /**
         * Registers the watchdog, unless an equivalent one is already registered.
         *
         * Re-scheduling an existing periodic job restarts its clock, so blindly calling this on
         * every launch would push the next run fifteen minutes out each time the senior opened
         * the app. The comparison below leaves a matching job alone and replaces one whose shape
         * has changed, which is what makes an edit to the interval take effect without leaving a
         * stale definition behind.
         */
        fun schedule(context: Context) {
            val scheduler =
                context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

            val existing = scheduler.getPendingJob(JOB_ID)
            if (existing != null &&
                existing.intervalMillis == INTERVAL_MS &&
                existing.isPersisted
            ) {
                Log.i(TAG, "Watchdog job already registered")
                return
            }

            val info = JobInfo.Builder(
                JOB_ID,
                ComponentName(context, MonitoringWatchdogJobService::class.java)
            )
                .setPeriodic(INTERVAL_MS)
                // The whole point. Without this the system drops the job at shutdown and the app
                // is back to needing a boot broadcast it does not receive.
                .setPersisted(true)
                .build()

            val result = scheduler.schedule(info)
            if (result == JobScheduler.RESULT_SUCCESS) {
                Log.i(TAG, "Watchdog job registered (persisted, ${INTERVAL_MS / 60_000} min)")
            } else {
                Log.w(TAG, "Watchdog job was refused by JobScheduler")
            }
        }
    }
}
