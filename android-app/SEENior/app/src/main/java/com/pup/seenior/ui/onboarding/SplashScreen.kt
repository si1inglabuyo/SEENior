package com.pup.seenior.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pup.seenior.R
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.sensors.SensorCollectionService
import com.pup.seenior.session.FamilySession
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onOnboarded: () -> Unit,
    onFamilyPaired: () -> Unit,
    onNotOnboarded: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val db = SeniorAppDatabase.getInstance(context.applicationContext)
        val isOnboarded = db.seniorDao().getOnboardedSenior() != null
        // A family user counts as set up once they're logged in (sign up / login / Google
        // all issue a token immediately) — linking a senior is optional, done later from
        // the dashboard's Link tab.
        val isFamily = FamilySession.isPaired(context.applicationContext)
        delay(1400)
        when {
            isOnboarded -> {
                SensorCollectionService.start(context.applicationContext)
                onOnboarded()
            }
            isFamily -> onFamilyPaired()
            else -> onNotOnboarded()
        }
    }
    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 60.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_seenior),
                contentDescription = "SEENior"
            )
        }
    }
}
