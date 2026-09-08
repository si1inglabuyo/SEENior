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
import com.pup.seenior.ui.LocalOnboardingCopy
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
    val copy = LocalOnboardingCopy.current
    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            OnboardingTopBar(currentStep = 3, onBack = onBack)
            OnboardingHeading(title = copy.questionnaireTitle, subtitle = copy.questionnaireSubtitle)

            // Language is no longer asked here — it is the senior's first onboarding choice now
            // (SeniorRoutes.LANGUAGE), so by this screen the whole form is already in it.

            LabeledTimeField(
                label = copy.qWakeTime,
                value = viewModel.wakeTime,
                onValueChange = { viewModel.wakeTime = it },
                questionStyle = true
            )
            LabeledTimeField(
                label = copy.qSleepTime,
                value = viewModel.sleepTime,
                onValueChange = { viewModel.sleepTime = it },
                questionStyle = true
            )
            LabeledDropdownField(
                label = copy.qHasNap,
                selected = viewModel.hasNap,
                options = OnboardingOptions.yesNo,
                onSelect = {
                    viewModel.hasNap = it
                    if (it == "No") {
                        viewModel.napTime = null
                        viewModel.napDuration = null
                    }
                },
                optionLabel = { copy.yesNo(it) },
                placeholder = copy.optionPlaceholder,
                questionStyle = true
            )

            if (viewModel.hasNap == "Yes") {
                LabeledTimeField(
                    label = copy.qNapTime,
                    value = viewModel.napTime,
                    onValueChange = { viewModel.napTime = it },
                    questionStyle = true
                )
                LabeledDropdownField(
                    label = copy.qNapDuration,
                    selected = viewModel.napDuration,
                    options = OnboardingOptions.napDurations,
                    onSelect = { viewModel.napDuration = it },
                    optionLabel = { copy.napDuration(it) },
                    placeholder = copy.optionPlaceholder,
                    questionStyle = true
                )
            }

            LabeledDropdownField(
                label = copy.qActivityLevel,
                selected = viewModel.activityLevelLabel,
                options = OnboardingOptions.activityLevels.map { it.first },
                onSelect = { viewModel.activityLevelLabel = it },
                optionLabel = { copy.activityLevel(it) },
                placeholder = copy.optionPlaceholder,
                questionStyle = true
            )
            LabeledDropdownField(
                label = copy.qGoesOutside,
                selected = viewModel.goesOutside,
                options = OnboardingOptions.yesNo,
                onSelect = {
                    viewModel.goesOutside = it
                    if (it == "No") viewModel.outsideTime = null
                },
                optionLabel = { copy.yesNo(it) },
                placeholder = copy.optionPlaceholder,
                questionStyle = true
            )

            if (viewModel.goesOutside == "Yes") {
                LabeledDropdownField(
                    label = copy.qOutsideTime,
                    selected = viewModel.outsideTime,
                    options = OnboardingOptions.outsideTimes,
                    onSelect = { viewModel.outsideTime = it },
                    optionLabel = { copy.outsideTime(it) },
                    placeholder = copy.optionPlaceholder,
                    questionStyle = true
                )
            }

            LabeledDropdownField(
                label = copy.qChargesOvernight,
                selected = viewModel.chargesOvernight,
                options = OnboardingOptions.yesNo,
                onSelect = { viewModel.chargesOvernight = it },
                optionLabel = { copy.yesNo(it) },
                placeholder = copy.optionPlaceholder,
                questionStyle = true
            )

            PrimaryPillButton(
                text = copy.next,
                onClick = onNext,
                enabled = viewModel.isQuestionnaireValid,
                modifier = Modifier.padding(top = 32.dp, bottom = 32.dp)
            )
        }
    }
}
