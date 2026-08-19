package com.pup.seenior.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pup.seenior.alerts.EscalationScheduler
import com.pup.seenior.database.SeniorAppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = SeniorAppDatabase.getInstance(context.applicationContext)
                if (db.seniorDao().getOnboardedSenior() != null) {
                    SensorCollectionService.start(context.applicationContext)
                }
                // Alarms do not survive a reboot. Any alert still awaiting an answer when the
                // phone restarted would otherwise wait forever for a wake-up that is never
                // coming — and it would fail silently, which is the worst way for this
                // particular thing to fail.
                EscalationScheduler.rearmAll(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
