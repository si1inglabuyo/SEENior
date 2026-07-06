package com.pup.seenior.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pup.seenior.ui.onboarding.components.LabeledDropdownField
import com.pup.seenior.ui.onboarding.components.LabeledTimeField
import com.pup.seenior.ui.onboarding.components.OnboardingHeading
import com.pup.seenior.ui.onboarding.components.OnboardingTopBar
import com.pup.seenior.ui.onboarding.components.PrimaryPillButton

@Composable
fun OnboardingQuestionnaireScreen(
    viewModel: OnboardingViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            OnboardingTopBar(currentStep = 3, onBack = onBack)
            OnboardingHeading(title = "Onboarding", subtitle = "Let's get started")

            LabeledTimeField(
                label = "What time do you usually wake up?",
                value = viewModel.wakeTime,
                onValueChange = { viewModel.wakeTime = it },
                questionStyle = true
            )
            LabeledTimeField(
                label = "What time do you usually go to sleep?",
                value = viewModel.sleepTime,
                onValueChange = { viewModel.sleepTime = it },
                questionStyle = true
            )
            LabeledDropdownField(
                label = "Do you take naps during the day?",
                selected = viewModel.hasNap,
                options = OnboardingOptions.yesNo,
                onSelect = {
                    viewModel.hasNap = it
                    if (it == "No") {
                        viewModel.napTime = null
                        viewModel.napDuration = null
                    }
                },
                optionLabel = { it },
                placeholder = "-Select Option-",
                questionStyle = true
            )

            if (viewModel.hasNap == "Yes") {
                LabeledTimeField(
                    label = "What time do you usually nap?",
                    value = viewModel.napTime,
                    onValueChange = { viewModel.napTime = it },
                    questionStyle = true
                )
                LabeledDropdownField(
                    label = "How long is your usual nap?",
                    selected = viewModel.napDuration,
                    options = OnboardingOptions.napDurations,
                    onSelect = { viewModel.napDuration = it },
                    optionLabel = { it },
                    placeholder = "-Select Option-",
                    questionStyle = true
                )
            }

            LabeledDropdownField(
                label = "How active are you during the day?",
                selected = viewModel.activityLevelLabel,
                options = OnboardingOptions.activityLevels.map { it.first },
                onSelect = { viewModel.activityLevelLabel = it },
                optionLabel = { it },
                placeholder = "-Select Option-",
                questionStyle = true
            )
            LabeledDropdownField(
                label = "Do you go outside regularly?",
                selected = viewModel.goesOutside,
                options = OnboardingOptions.yesNo,
                onSelect = {
                    viewModel.goesOutside = it
                    if (it == "No") viewModel.outsideTime = null
                },
                optionLabel = { it },
                placeholder = "-Select Option-",
                questionStyle = true
            )

            if (viewModel.goesOutside == "Yes") {
                LabeledDropdownField(
                    label = "What time do you usually go outside?",
                    selected = viewModel.outsideTime,
                    options = OnboardingOptions.outsideTimes,
                    onSelect = { viewModel.outsideTime = it },
                    optionLabel = { it },
                    placeholder = "-Select Option-",
                    questionStyle = true
                )
            }

            LabeledDropdownField(
                label = "Do you charge your phone overnight?",
                selected = viewModel.chargesOvernight,
                options = OnboardingOptions.yesNo,
                onSelect = { viewModel.chargesOvernight = it },
                optionLabel = { it },
                placeholder = "-Select Option-",
                questionStyle = true
            )

            PrimaryPillButton(
                text = "NEXT",
                onClick = onNext,
                enabled = viewModel.isQuestionnaireValid,
                modifier = Modifier.padding(top = 32.dp, bottom = 32.dp)
            )
        }
    }
}
