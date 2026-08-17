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
     * Warm path: the Activity is already running (launchMode is singleTop), so Android
     * delivers the tap here rather than through onCreate. Without this, tapping a
     * notification while the app was merely backgrounded would bring the dashboard forward
     * on whatever tab it was left on and quietly ignore which alert was tapped.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        PendingAlertNavigation.captureFrom(intent)
    }
}
