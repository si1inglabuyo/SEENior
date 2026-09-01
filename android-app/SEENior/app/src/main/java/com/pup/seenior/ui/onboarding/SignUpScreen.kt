package com.pup.seenior.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pup.seenior.ui.onboarding.components.LabeledDropdownField
import com.pup.seenior.ui.onboarding.components.LabeledTextField
import com.pup.seenior.ui.onboarding.components.OnboardingHeading
import com.pup.seenior.ui.onboarding.components.OnboardingTopBar
import com.pup.seenior.ui.onboarding.components.PrimaryPillButton
import com.pup.seenior.ui.onboarding.components.SearchableDropdownField
import com.pup.seenior.ui.theme.SeniorColors
import com.pup.seenior.validation.PhilippinePhone

@Composable
fun SignUpScreen(
    viewModel: OnboardingViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onPickOnMap: () -> Unit
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
                onValueChange = { input ->
                    // Digits only, plus an optional leading "+"; capped at "+639XXXXXXXXX" length.
                    viewModel.mobileNumber = input
                        .filterIndexed { index, c -> c.isDigit() || (c == '+' && index == 0) }
                        .take(13)
                },
                placeholder = "09XX XXX XXXX",
                keyboardType = KeyboardType.Phone,
                isError = viewModel.mobileNumber.isNotBlank() &&
                    !PhilippinePhone.isValid(viewModel.mobileNumber),
                errorText = "Enter a valid PH mobile number (09XXXXXXXXX or +639XXXXXXXXX)"
            )

            LabeledDropdownField(
                label = "LIVING ARRANGEMENT",
                selected = viewModel.livingArrangementLabel,
                options = OnboardingOptions.livingArrangements.map { it.first },
                onSelect = { viewModel.livingArrangementLabel = it },
                optionLabel = { it },
                placeholder = "Select"
            )

            // Offered above the dropdowns, not below them: it exists to save a senior from four
            // cascading lists, and placed after them it would only be found by someone who had
            // already done the work.
            PickOnMapRow(onPickOnMap)

            SearchableDropdownField(
                label = "REGION",
                selected = viewModel.region,
                options = viewModel.regionOptions,
                onSelect = viewModel::onRegionSelected
            )

            SearchableDropdownField(
                label = "PROVINCE",
                selected = viewModel.province,
                options = viewModel.provinceOptions,
                onSelect = viewModel::onProvinceSelected,
                enabled = viewModel.region != null
            )

            SearchableDropdownField(
                label = "CITY / MUNICIPALITY",
                selected = viewModel.city,
                options = viewModel.cityOptions,
                onSelect = viewModel::onCitySelected,
                enabled = viewModel.province != null
            )

            SearchableDropdownField(
                label = "BARANGAY",
                selected = viewModel.barangay,
                options = viewModel.barangayOptions,
                onSelect = { viewModel.barangay = it },
                enabled = viewModel.city != null
            )

            LabeledTextField(
                label = "STREET ADDRESS",
                value = viewModel.streetAddress,
                onValueChange = { viewModel.streetAddress = it },
                placeholder = "House No., Street, Subdivision"
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

/**
 * The shortcut into [AddressMapPickerScreen].
 *
 * Deliberately a link beside the dropdowns rather than a replacement for them. OpenStreetMap's
 * coverage of Philippine barangays is uneven, and a senior whose street it has never heard of must
 * still be able to finish signing up — so typing the address stays the guaranteed path.
 */
@Composable
private fun PickOnMapRow(onPickOnMap: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .background(SeniorColors.GreenLightBg, RoundedCornerShape(14.dp))
            .clickable(onClick = onPickOnMap)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Filled.Map,
            contentDescription = null,
            tint = SeniorColors.GreenDark,
            modifier = Modifier.size(22.dp)
        )
        Column {
            Text(
                "Find my address on a map",
                color = SeniorColors.GreenDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Drag a pin to your house instead of filling in the boxes below.",
                color = SeniorColors.TextSecondary,
                fontSize = 13.sp
            )
        }
    }
}
