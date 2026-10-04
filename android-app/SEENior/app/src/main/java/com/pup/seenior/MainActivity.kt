package com.pup.seenior

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pup.seenior.alerts.PendingAlertNavigation
import com.pup.seenior.ui.navigation.SeniorNavGraph
import com.pup.seenior.ui.theme.SEENiorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Cold start from a notification tap: the alert id rides in on the launch Intent.
        PendingAlertNavigation.captureFrom(intent)
        enableEdgeToEdge()
        setContent {
            SEENiorTheme {
                SeniorNavGraph()
            }
        }
    }

    /**
     * Warm path: the Activity is already running (singleTop), so Android delivers a notification
     * tap here instead of onCreate. Without this the tapped alert would be ignored.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        PendingAlertNavigation.captureFrom(intent)
    }
}
