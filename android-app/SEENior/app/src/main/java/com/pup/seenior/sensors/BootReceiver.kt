package com.pup.seenior.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
            } finally {
                pendingResult.finish()
            }
        }
    }
}
