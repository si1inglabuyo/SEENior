package com.pup.seenior.sensors

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.pup.seenior.alerts.AlertEscalator
import com.pup.seenior.alerts.EscalationScheduler
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.network.HeartbeatReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Restarts passive monitoring when it has stopped, on a repeating job that survives a reboot.
 *
 * [BootReceiver] doesn't run on every handset. On the Infinix X6885 (Android 15, XOS) there
 * was no app process after a reboot, even with the OEM's Auto-launch toggle on, so the app
 * wasn't monitoring until someone opened it, which contradicts "fully passive".
 *
 * A persisted JobScheduler job is stored by the system outside this app and restored after a
 * reboot, starting this process, with no dependence on the boot broadcast. `setPersisted(true)`
 * needs RECEIVE_BOOT_COMPLETED, which the manifest already declares.
 *
 * This is raw JobScheduler rather than WorkManager, which doesn't persist its jobs (on the
 * same handset its periodic request had neither the PERSISTED nor the PERIODIC flag) and
 * reschedules itself from the very boot broadcast this class routes around.
 *
 * It is a recovery net, not a deadline: the platform may run a periodic job late and 15
 * minutes is the shortest period, so monitoring can be down for about that long after a
 * restart. The escalation deadline stays on [EscalationScheduler]'s alarm clock. If a
 * persisted job doesn't survive a reboot either, the fallback is an FCM wake from the server.
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
                // The reschedule flag is ignored for a periodic job; a failed pass waits for the next period.
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

        // Nothing to monitor before onboarding finishes, and starting early would show a permanent notification too soon.
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
                // so catching the parent compiles at minSdk 26. A running job is a background
                // state, so the start only works because of the battery exemption asked for in
                // onboarding. Logged so a refusal isn't invisible.
                Log.w(TAG, "Not allowed to start the sensor service from the background", e)
            }
        }

        // Alarms are lost on reboot and force-stop, so put back the deadline of anything still open.
        EscalationScheduler.rearmAll(app)

        // Retry any self-cancel the cloud was never told about (usually answered with no signal).
        db.seniorDao().getOnboardedSenior()?.let { senior ->
            AlertEscalator.reconcileCancelledAlerts(db, senior.seniorId)
            // And any missed severity upgrade.
            AlertEscalator.reconcileSeverity(db, senior.seniorId)
            // And any location cell that landed after its alert had already gone out.
            AlertEscalator.reconcileLocation(db, senior.seniorId)
        }

        // Last, because it is the only part that touches the network and monitoring should be
        // restored first. It never throws.
        HeartbeatReporter.report(app, db)
    }

    companion object {
        private const val TAG = "MonitoringWatchdog"

        /** Stable across reboots by definition — a persisted job is restored under this id. */
        private const val JOB_ID = 4201

        private val INTERVAL_MS = TimeUnit.MINUTES.toMillis(15)

        /**
         * Registers the watchdog unless an equivalent one exists. Rescheduling a periodic job
         * restarts its clock, so this leaves a matching job alone and replaces one whose
         * shape changed.
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
                // The point of this class: without it the system drops the job at shutdown.
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
