package com.pup.seenior.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pup.seenior.ui.LocalOnboardingCopy
import com.pup.seenior.ui.onboarding.components.PrimaryPillButton
import com.pup.seenior.ui.theme.SeniorColors

/**
 * The senior's first onboarding choice: the language of every later screen and wellness
 * prompt. It was once one field among nine, three screens in, which made a Filipino-only
 * reader work through an English form first. The answer lands on
 * [OnboardingViewModel.languageLabel], which `SeniorNavGraph` resolves into
 * `LocalOnboardingCopy` above the NavHost. No step dots, since it's a preference set before
 * the numbered flow, and the prompt stays bilingual because the choice isn't made yet.
 */
@Composable
fun SeniorLanguageChoiceScreen(
    viewModel: OnboardingViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val copy = LocalOnboardingCopy.current
    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = SeniorColors.TextPrimary
                    )
                }
            }

            Text(
                copy.qLanguage,
                color = SeniorColors.Green,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                "Ito ang wikang gagamitin sa lahat ng bahagi ng app at sa mga mensaheng may " +
                    "kinalaman sa inyong kalagayan.",
                color = SeniorColors.TextSecondary,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(Modifier.height(20.dp))

            OnboardingOptions.languages.forEach { (label, _) ->
                val selected = viewModel.languageLabel == label
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .background(
                            if (selected) SeniorColors.GreenLightBg else Color.White,
                            RoundedCornerShape(14.dp)
                        )
                        .border(
                            1.dp,
                            if (selected) SeniorColors.Green else SeniorColors.FieldBorder,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { viewModel.languageLabel = label }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected, onClick = { viewModel.languageLabel = label })
                    Spacer(Modifier.size(8.dp))
                    Text(
                        label,
                        fontSize = 19.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = SeniorColors.TextPrimary
                    )
                }
            }

            PrimaryPillButton(
                text = copy.next,
                onClick = onNext,
                enabled = viewModel.languageLabel != null,
                modifier = Modifier.padding(top = 32.dp, bottom = 32.dp)
            )
        }
    }
}
