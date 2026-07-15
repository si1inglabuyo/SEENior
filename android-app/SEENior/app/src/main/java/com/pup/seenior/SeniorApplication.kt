package com.pup.seenior

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.pup.seenior.aggregation.NightlyAggregationWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit

class SeniorApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        scheduleNightlyAggregation()
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
