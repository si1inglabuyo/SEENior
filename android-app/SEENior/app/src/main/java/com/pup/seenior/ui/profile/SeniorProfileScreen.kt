package com.pup.seenior.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pup.seenior.ui.contacts.GreenHeader
import com.pup.seenior.ui.onboarding.OnboardingOptions
import com.pup.seenior.ui.onboarding.components.LabeledDropdownField
import com.pup.seenior.ui.onboarding.components.LabeledTextField
import com.pup.seenior.ui.onboarding.components.PrimaryPillButton
import com.pup.seenior.ui.theme.SeniorColors
import com.pup.seenior.validation.PhilippinePhone

private enum class ProfilePage { HOME, EDIT, ABOUT, HOW_TO_USE, FAQS, SUPPORT, TERMS, PRIVACY }

/**
 * Senior "Profile" tab (designs/senior/profile). Account card + MY INFO (Edit profile) +
 * HELP & INFORMATION + ABOUT SEENIOR.
 *
 * Deliberately has no Log Out row, unlike the family Profile tab: the senior has no account
 * to sign out of — it's created locally during onboarding (CLAUDE.md §2) — and the only
 * session on the device belongs to the family app.
 */
@Composable
fun SeniorProfileScreen() {
    val viewModel: SeniorProfileViewModel = viewModel()
    LaunchedEffect(Unit) { viewModel.refresh() }
    var page by remember { mutableStateOf(ProfilePage.HOME) }

    when (page) {
        ProfilePage.HOME -> ProfileHome(viewModel) { page = it }
        ProfilePage.EDIT -> SeniorEditProfileScreen(
            viewModel = viewModel,
            onBack = {
                viewModel.discardEdits()
                page = ProfilePage.HOME
            },
            onSaved = { page = ProfilePage.HOME }
        )
        ProfilePage.ABOUT -> SeniorAboutScreen { page = ProfilePage.HOME }
        ProfilePage.HOW_TO_USE -> SeniorHowToUseScreen { page = ProfilePage.HOME }
        ProfilePage.FAQS -> SeniorFaqsScreen { page = ProfilePage.HOME }
        ProfilePage.SUPPORT -> SeniorContactSupportScreen { page = ProfilePage.HOME }
        ProfilePage.TERMS -> SeniorTermsScreen { page = ProfilePage.HOME }
        ProfilePage.PRIVACY -> SeniorPrivacyScreen { page = ProfilePage.HOME }
    }
}

@Composable
private fun ProfileHome(viewModel: SeniorProfileViewModel, onNavigate: (ProfilePage) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
    ) {
        GreenHeader(icon = { Icon(Icons.Filled.Person, null, tint = Color.White) }, title = "Profile")

        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {

            // ---- Account card ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SeniorColors.FieldBorder, RoundedCornerShape(20.dp))
                    .padding(vertical = 24.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(88.dp).border(3.dp, SeniorColors.GreenBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        initials(viewModel.fullName),
                        color = SeniorColors.Green,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    viewModel.fullName,
                    color = SeniorColors.Green,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 14.dp)
                )
                viewModel.senior?.let {
                    Text(
                        "Senior · ${it.age} years old",
                        color = SeniorColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            viewModel.error?.let {
                Text(it, color = ErrorRed, fontSize = 15.sp, modifier = Modifier.padding(top = 14.dp))
            }

            // Saving navigates straight back here, so the "saved locally but not in the cloud"
            // note has to surface on this screen — on the Edit screen alone it would be set
            // and then immediately dismissed with it, i.e. never actually read. Tap to dismiss.
            viewModel.syncWarning?.let {
                Text(
                    it,
                    color = SeniorColors.TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .clickable { viewModel.clearSyncWarning() }
                )
            }

            SectionLabel("MY INFO")
            ProfileGroup {
                ProfileRow(
                    icon = Icons.Filled.Edit,
                    title = "Edit profile",
                    subtitle = "First Name, Surname, Age, Gender, Mobile Number…",
                    onClick = { onNavigate(ProfilePage.EDIT) }
                )
            }

            SectionLabel("HELP & INFORMATION")
            ProfileGroup {
                ProfileRow(Icons.Filled.Info, "About this app") { onNavigate(ProfilePage.ABOUT) }
                RowDivider()
                ProfileRow(Icons.AutoMirrored.Filled.MenuBook, "How to use") { onNavigate(ProfilePage.HOW_TO_USE) }
                RowDivider()
                ProfileRow(Icons.AutoMirrored.Filled.HelpOutline, "FAQs") { onNavigate(ProfilePage.FAQS) }
                RowDivider()
                ProfileRow(Icons.Filled.SupportAgent, "Contact support") { onNavigate(ProfilePage.SUPPORT) }
            }

            SectionLabel("ABOUT SEENIOR")
            ProfileGroup {
                ProfileRow(Icons.Filled.Gavel, "Terms & conditions") { onNavigate(ProfilePage.TERMS) }
                RowDivider()
                ProfileRow(Icons.Filled.PrivacyTip, "Privacy policy") { onNavigate(ProfilePage.PRIVACY) }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SeniorEditProfileScreen(
    viewModel: SeniorProfileViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        GreenBackHeader(title = "Edit profile", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .border(3.dp, SeniorColors.GreenBorder, CircleShape)
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    initials("${viewModel.firstName} ${viewModel.lastName}"),
                    color = SeniorColors.Green,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            }

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
                    // Digits only, plus an optional leading "+"; same filter as sign-up.
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

            LabeledTextField(
                label = "ADDRESS",
                value = viewModel.address,
                onValueChange = { viewModel.address = it },
                placeholder = "House No., Street, Barangay, City"
            )

            // Barangay routes alerts to the correct responder and was picked from the PSGC list
            // at sign-up, so it's shown for confirmation but not editable here — a free-typed
            // typo would silently send alerts to the wrong barangay.
            viewModel.senior?.let {
                Text(
                    "Barangay: ${it.barangay}",
                    color = SeniorColors.TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            viewModel.syncWarning?.let {
                Text(
                    it,
                    color = SeniorColors.TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }

            PrimaryPillButton(
                text = if (viewModel.isSaving) "SAVING…" else "SAVE CHANGES",
                onClick = { viewModel.saveProfile(onSaved = onSaved) },
                enabled = viewModel.isEditValid && !viewModel.isSaving,
                modifier = Modifier.padding(top = 28.dp, bottom = 32.dp)
            )
        }
    }
}

// ---------------------------------------------------------------- shared bits

/** Green header with a back arrow, used by Edit Profile and every Profile sub-screen. */
@Composable
fun GreenBackHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SeniorColors.Green)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = SeniorColors.TextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp)
    )
}

@Composable
private fun ProfileGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SeniorColors.FieldBorder, RoundedCornerShape(18.dp))
    ) {
        content()
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = SeniorColors.FieldBorder, modifier = Modifier.padding(horizontal = 14.dp))
}

@Composable
private fun ProfileRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(42.dp).background(SeniorColors.GreenLightBg, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = SeniorColors.Green, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
            Text(title, color = SeniorColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            subtitle?.let {
                Text(it, color = SeniorColors.TextSecondary, fontSize = 14.sp, maxLines = 2)
            }
        }
        Icon(Icons.Filled.ChevronRight, null, tint = SeniorColors.TextSecondary)
    }
}

private fun initials(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotBlank() }
    if (parts.isEmpty()) return "?"
    return parts.take(2).joinToString("") { it.first().uppercase() }
}
