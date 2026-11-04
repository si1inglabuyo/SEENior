package com.pup.seenior.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pup.seenior.afterhours.AfterHoursConfig
import com.pup.seenior.afterhours.AfterHoursStore
import com.pup.seenior.ui.wellness.WellnessMessages

private val NoticeBackground = Color(0xFFFFF4E0)
private val NoticeBorder = Color(0xFFE0A030)
private val NoticeTitle = Color(0xFF7A4B00)
private val NoticeText = Color(0xFF3B2A10)

/** English and Filipino wording, kept apart from the screens that show the notice. */
internal object AfterHoursStrings {
    fun title(language: String) =
        if (language == WellnessMessages.FILIPINO) "Sarado ang barangay hall ngayon" else "The barangay hall is closed right now"

    fun body(language: String, days: String, open: String, close: String) =
        if (language == WellnessMessages.FILIPINO)
            "Bukas ito $days, $open hanggang $close, kaya maghihintay ang alerto hanggang magbukas ito. Sa emergency, tumawag sa:"
        else
            "It is staffed $days, $open to $close, so an alert waits there until it opens. In an emergency, call:"

    fun call(language: String, number: String) =
        if (language == WellnessMessages.FILIPINO) "Tawagan ang $number" else "Call $number"
}

/**
 * Tells the person the barangay hall is closed and lists who to call instead, with tap-to-call
 * numbers. Draws nothing while the hall is open, so it can sit on any screen that talks about
 * the barangay. The list and hours come from the server, cached on the phone with bundled
 * defaults (see [AfterHoursStore]), so it works offline.
 *
 * Numbers open the dialler pre-filled and the person presses call, so the app needs no call
 * permission and a stray tap cannot ring anybody.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AfterHoursNotice(language: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(AfterHoursStore.current(context)) }
    LaunchedEffect(Unit) {
        AfterHoursStore.refreshIfStale(context)
        config = AfterHoursStore.current(context)
    }
    // Judged when the screen is drawn. Good enough: these screens are not left open across 8:00 or 17:00.
    val closed = remember(config) { !config.isHallOpen(System.currentTimeMillis()) }
    if (!closed) return

    val filipino = language == WellnessMessages.FILIPINO
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(NoticeBackground, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(AfterHoursStrings.title(language), color = NoticeTitle, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            AfterHoursStrings.body(
                language,
                AfterHoursConfig.daysText(config.hall.openWeekdays, filipino),
                AfterHoursConfig.hourText(config.hall.openHour),
                AfterHoursConfig.hourText(config.hall.closeHour)
            ),
            color = NoticeText,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        config.hotlines.forEach { hotline ->
            Spacer(Modifier.height(12.dp))
            Text(hotline.name, color = NoticeText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                hotline.numbers.forEach { number ->
                    OutlinedButton(
                        onClick = { dial(context, number.dial) },
                        border = BorderStroke(1.dp, NoticeBorder),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(AfterHoursStrings.call(language, number.display), color = NoticeTitle, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

private fun dial(context: Context, number: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // A tablet or emulator with no dialler: nothing to open, and nothing useful to say.
    }
}
