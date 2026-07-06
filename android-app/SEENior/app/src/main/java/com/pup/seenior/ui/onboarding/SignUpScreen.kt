package com.pup.seenior.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pup.seenior.ui.onboarding.components.LabeledDropdownField
import com.pup.seenior.ui.onboarding.components.LabeledTextField
import com.pup.seenior.ui.onboarding.components.OnboardingHeading
import com.pup.seenior.ui.onboarding.components.OnboardingTopBar
import com.pup.seenior.ui.onboarding.components.PrimaryPillButton

@Composable
fun SignUpScreen(
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
            OnboardingTopBar(currentStep = 1, onBack = onBack)
            OnboardingHeading(title = "Tell Us About Yourself", subtitle = "Proceed with your setup.")

            LabeledTextField(
                label = "FIRST NAME",
                value = viewModel.firstName,
                onValueChange = { viewModel.firstName = it },
                placeholder = "First Name"
            )
            LabeledTextField(
                label = "LAST NAME",
                value = viewModel.lastName,
                onValueChange = { viewModel.lastName = it },
                placeholder = "Last Name"
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                LabeledTextField(
                    label = "AGE",
                    value = viewModel.age,
                    onValueChange = { if (it.length <= 3) viewModel.age = it.filter(Char::isDigit) },
                    placeholder = "00",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f).padding(end = 12.dp)
                )
                LabeledDropdownField(
                    label = "GENDER",
                    selected = viewModel.gender,
                    options = OnboardingOptions.genders,
                    onSelect = { viewModel.gender = it },
                    optionLabel = { it },
                    placeholder = "Select",
                    modifier = Modifier.weight(1f)
                )
            }

            LabeledTextField(
                label = "MOBILE NUMBER",
                value = viewModel.mobileNumber,
                onValueChange = { viewModel.mobileNumber = it },
                placeholder = "+63 000-000-0000",
                keyboardType = KeyboardType.Phone
            )

            LabeledDropdownField(
                label = "LIVING ARRANGEMENT",
                selected = viewModel.livingArrangementLabel,
                options = OnboardingOptions.livingArrangements.map { it.first },
                onSelect = { viewModel.livingArrangementLabel = it },
                optionLabel = { it },
                placeholder = "Select"
            )

            LabeledTextField(
                label = "ADDRESS",
                value = viewModel.address,
                onValueChange = { viewModel.address = it },
                placeholder = "1234 Sampaguita Street"
            )

            LabeledTextField(
                label = "BARANGAY",
                value = viewModel.barangay,
                onValueChange = { viewModel.barangay = it },
                placeholder = "e.g. UP Campus"
            )

            PrimaryPillButton(
                text = "NEXT",
                onClick = onNext,
                enabled = viewModel.isSignUpValid,
                modifier = Modifier.padding(top = 32.dp, bottom = 32.dp)
            )
        }
    }
}
