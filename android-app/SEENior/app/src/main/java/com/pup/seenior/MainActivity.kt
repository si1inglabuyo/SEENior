package com.pup.seenior

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pup.seenior.ui.navigation.SeniorNavGraph
import com.pup.seenior.ui.theme.SEENiorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SEENiorTheme {
                SeniorNavGraph()
            }
        }
    }
}
