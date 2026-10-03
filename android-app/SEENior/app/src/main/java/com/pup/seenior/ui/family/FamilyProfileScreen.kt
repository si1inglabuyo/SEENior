package com.pup.seenior.ui.family

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Language
import com.pup.seenior.ui.wellness.WellnessMessages
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pup.seenior.network.PushTokenRegistrar

/** Family "Profile" tab (designs/family_contact/profile). Account card + MY INFO (Edit profile,
 *  functional) + HELP & INFORMATION (static rows — no sub-screens built, out of scope here)
 *  + Log Out (clears the session, [onLoggedOut] returns to the pre-auth flow). */
private enum class FamilyProfilePage {
    HOME, EDIT, DELETE_ACCOUNT, LANGUAGE, ABOUT, HOW_TO_USE, FAQS, FEEDBACK, SUPPORT, TERMS, PRIVACY
}

@Composable
fun FamilyProfileScreen(viewModel: FamilyProfileViewModel = viewModel(), onLoggedOut: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    var page by remember { mutableStateOf(FamilyProfilePage.HOME) }
    val goHome = { page = FamilyProfilePage.HOME }

    when (page) {
        FamilyProfilePage.EDIT -> FamilyEditProfileScreen(viewModel = viewModel, onBack = goHome)
        FamilyProfilePage.DELETE_ACCOUNT -> FamilyDeleteAccountScreen(
            viewModel = viewModel,
            onBack = goHome,
            // Account is gone server-side; onLoggedOut already routes to the pre-auth flow.
            onDeleted = onLoggedOut
        )
        FamilyProfilePage.LANGUAGE -> FamilyLanguageScreen(viewModel = viewModel, onBack = goHome)
        FamilyProfilePage.ABOUT -> FamilyAboutScreen(onBack = goHome)
        FamilyProfilePage.HOW_TO_USE -> FamilyHowToUseScreen(onBack = goHome)
        FamilyProfilePage.FAQS -> FamilyFaqsScreen(onBack = goHome)
        FamilyProfilePage.FEEDBACK -> FamilyFeedbackScreen(onBack = goHome)
        FamilyProfilePage.SUPPORT -> FamilyContactSupportScreen(onBack = goHome)
        FamilyProfilePage.TERMS -> FamilyTermsScreen(onBack = goHome)
        FamilyProfilePage.PRIVACY -> FamilyPrivacyScreen(onBack = goHome)
        FamilyProfilePage.HOME -> FamilyProfileHome(
            viewModel = viewModel,
            onEditProfile = { page = FamilyProfilePage.EDIT },
            onDeleteAccount = { page = FamilyProfilePage.DELETE_ACCOUNT },
            onChooseLanguage = { page = FamilyProfilePage.LANGUAGE },
            onAbout = { page = FamilyProfilePage.ABOUT },
            onHowToUse = { page = FamilyProfilePage.HOW_TO_USE },
            onFaqs = { page = FamilyProfilePage.FAQS },
            onFeedback = { page = FamilyProfilePage.FEEDBACK },
            onContactSupport = { page = FamilyProfilePage.SUPPORT },
            onTerms = { page = FamilyProfilePage.TERMS },
            onPrivacy = { page = FamilyProfilePage.PRIVACY },
            onLoggedOut = onLoggedOut
        )
    }
}

@Composable
private fun FamilyProfileHome(
    viewModel: FamilyProfileViewModel,
    onEditProfile: () -> Unit,
    onDeleteAccount: () -> Unit,
    onChooseLanguage: () -> Unit,
    onAbout: () -> Unit,
    onHowToUse: () -> Unit,
    onFaqs: () -> Unit,
    onFeedback: () -> Unit,
    onContactSupport: () -> Unit,
    onTerms: () -> Unit,
    onPrivacy: () -> Unit,
    onLoggedOut: () -> Unit
) {
    val copy = LocalFamilyCopy.current
    val context = LocalContext.current
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val name = viewModel.user?.fullName?.takeIf { it.isNotBlank() } ?: copy.defaultFamilyMemberName

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
    ) {
        BlueHeader(Icons.Filled.Person, copy.profileHeader)

        Column(modifier = Modifier.padding(24.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, FamilyColors.FieldBorder, RoundedCornerShape(18.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(64.dp).border(2.dp, FamilyColors.Blue, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(initials(name), color = FamilyColors.Blue, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Text(name, color = FamilyColors.Blue, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                Text(copy.familyMemberRoleLabel, color = FamilyColors.TextSecondary, fontSize = 14.sp)
            }

            Text(copy.myInfoLabel, color = FamilyColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
            ProfileRow(
                icon = Icons.Filled.Edit,
                title = copy.editProfileTitle,
                subtitle = copy.editProfileSubtitle,
                onClick = onEditProfile
            )
            Spacer(Modifier.height(10.dp))
            ProfileRow(
                icon = Icons.Filled.Language,
                title = copy.languageRowTitle,
                subtitle = copy.languageValueLabel,
                onClick = onChooseLanguage
            )

            Text(copy.helpInfoLabel, color = FamilyColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
            ProfileRow(icon = Icons.Filled.Info, title = copy.aboutAppLabel, onClick = onAbout)
            Spacer(Modifier.height(10.dp))
            ProfileRow(icon = Icons.AutoMirrored.Filled.MenuBook, title = copy.howToUseLabel, onClick = onHowToUse)
            Spacer(Modifier.height(10.dp))
            ProfileRow(icon = Icons.AutoMirrored.Filled.HelpOutline, title = copy.faqsLabel, onClick = onFaqs)
            Spacer(Modifier.height(10.dp))
            ProfileRow(icon = Icons.AutoMirrored.Outlined.Chat, title = copy.feedbackLabel, onClick = onFeedback)
            if (FAMILY_SUPPORT_CONTACT_CONFIGURED) {
                Spacer(Modifier.height(10.dp))
                ProfileRow(icon = Icons.Filled.SupportAgent, title = copy.contactSupportLabel, onClick = onContactSupport)
            }
            Spacer(Modifier.height(10.dp))
            ProfileRow(icon = Icons.Filled.Gavel, title = copy.termsLabel, onClick = onTerms)
            Spacer(Modifier.height(10.dp))
            ProfileRow(icon = Icons.Filled.PrivacyTip, title = copy.privacyLabel, onClick = onPrivacy)

            Spacer(Modifier.height(24.dp))
            ProfileRow(
                icon = Icons.AutoMirrored.Filled.Logout,
                title = copy.logOutLabel,
                titleColor = FamilyColors.ErrorRed,
                iconTint = FamilyColors.ErrorRed,
                iconBackground = FamilyColors.ErrorRed.copy(alpha = 0.1f),
                showChevron = false,
                onClick = { showLogoutConfirm = true }
            )

            Spacer(Modifier.height(10.dp))
            ProfileRow(
                icon = Icons.Filled.DeleteForever,
                title = copy.deleteAccountTitle,
                subtitle = copy.deleteAccountSubtitle,
                titleColor = FamilyColors.ErrorRed,
                iconTint = FamilyColors.ErrorRed,
                iconBackground = FamilyColors.ErrorRed.copy(alpha = 0.1f),
                onClick = onDeleteAccount
            )

            viewModel.error?.let {
                Text(copy.errorMessage(it) ?: "", color = FamilyColors.ErrorRed, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text(copy.logoutConfirmTitle) },
            text = { Text(copy.logoutConfirmBody) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    // Clears the session and releases this device's push token, in that
                    // order and on a scope that survives the navigation below. Leaving the
                    // token behind would keep this handset receiving the previous account's
                    // alerts, which name the senior (spec §11).
                    PushTokenRegistrar.signOutAsync(context)
                    onLoggedOut()
                }) {
                    Text(copy.logOutLabel, color = FamilyColors.ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text(copy.cancelButton, color = FamilyColors.TextSecondary)
                }
            }
        )
    }
}

/**
 * Profile -> Language. Mirrors the senior side's own picker (SeniorLanguageScreen):
 * writes through immediately via [FamilyProfileViewModel.setLanguage], no Save button,
 * and each option is labelled in its own language so a Filipino-only reader can find
 * their option without reading English first.
 */
@Composable
private fun FamilyLanguageScreen(viewModel: FamilyProfileViewModel, onBack: () -> Unit) {
    val copy = LocalFamilyCopy.current
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FamilyColors.HeaderBlue)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = copy.backContentDescription, tint = Color.White)
            }
            Text(copy.languageRowTitle, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                copy.languagePickerHeading,
                color = FamilyColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            Text(copy.languagePickerBodyEn, color = FamilyColors.TextSecondary, fontSize = 13.sp)
            Text(
                copy.languagePickerBodyFil,
                color = FamilyColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
            listOf(
                copy.englishOptionLabel to WellnessMessages.ENGLISH,
                copy.filipinoOptionLabel to WellnessMessages.FILIPINO
            ).forEach { (label, code) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setLanguage(code) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = viewModel.language == code,
                        onClick = { viewModel.setLanguage(code) }
                    )
                    Text(label, color = FamilyColors.TextPrimary, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    titleColor: Color = FamilyColors.TextPrimary,
    iconTint: Color = FamilyColors.Blue,
    iconBackground: Color = FamilyColors.BlueLightBg,
    showChevron: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FamilyColors.FieldBorder, RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(iconBackground, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, color = titleColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, color = FamilyColors.TextSecondary, fontSize = 13.sp) }
        }
        if (showChevron) {
            Icon(Icons.Filled.ChevronRight, null, tint = FamilyColors.TextSecondary)
        }
    }
}

@Composable
private fun FamilyEditProfileScreen(viewModel: FamilyProfileViewModel, onBack: () -> Unit) {
    val copy = LocalFamilyCopy.current
    var showPasswordDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FamilyColors.HeaderBlue)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = copy.backContentDescription, tint = Color.White)
            }
            Text(copy.editProfileTitle, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Box(
                modifier = Modifier.size(72.dp).border(2.dp, FamilyColors.Blue, CircleShape).align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                Text(initials(viewModel.fullName), color = FamilyColors.Blue, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(24.dp))
            FamilyTextField(copy.fullNameLabel, viewModel.fullName, { viewModel.fullName = it })

            Spacer(Modifier.height(14.dp))
            FamilyTextField(
                copy.mobileNumberLabel,
                viewModel.phone,
                { viewModel.phone = it },
                keyboardType = KeyboardType.Phone,
                isError = viewModel.phone.isNotBlank() && !com.pup.seenior.validation.PhilippinePhone.isValid(viewModel.phone),
                errorText = copy.invalidPhoneError
            )

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .border(1.dp, FamilyColors.BlueBorder, RoundedCornerShape(14.dp))
                    .clickable {
                        viewModel.resetPasswordDialogState()
                        showPasswordDialog = true
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // A Google-only account has no password to change — it sets one instead, which
                // then also unlocks email + password sign-in.
                Text(
                    if (viewModel.hasPassword) copy.changePasswordButton else copy.setAPasswordButton,
                    color = FamilyColors.Blue,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            viewModel.error?.let {
                Text(copy.errorMessage(it) ?: "", color = FamilyColors.ErrorRed, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp))
            }

            Spacer(Modifier.height(28.dp))
            BluePillButton(
                text = if (viewModel.isSaving) copy.savingEllipsis else copy.saveChangesButton,
                enabled = viewModel.isEditValid && !viewModel.isSaving,
                onClick = { viewModel.saveProfile(onSaved = onBack) }
            )
        }
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            viewModel = viewModel,
            onDismiss = { showPasswordDialog = false }
        )
    }
}

@Composable
private fun ChangePasswordDialog(viewModel: FamilyProfileViewModel, onDismiss: () -> Unit) {
    val copy = LocalFamilyCopy.current
    // Same dialog, two modes: an account with a password *changes* it (needs the current one);
    // a Google-only account *sets* one for the first time (no current password, and it also
    // turns on email + password sign-in).
    val setting = !viewModel.hasPassword
    val formValid = if (setting) viewModel.isSetPasswordFormValid else viewModel.isPasswordFormValid
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    viewModel.passwordChanged && setting -> copy.passwordSetTitle
                    viewModel.passwordChanged -> copy.passwordChangedTitle
                    setting -> copy.setAPasswordButton
                    else -> copy.changePasswordButton
                }
            )
        },
        text = {
            if (viewModel.passwordChanged) {
                Text(if (setting) copy.passwordSetBody else copy.passwordChangedBody)
            } else {
                Column {
                    if (setting) {
                        Text(
                            copy.googleSignupNotice(viewModel.user?.email ?: copy.yourEmailFallback),
                            color = FamilyColors.TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    } else {
                        FamilyTextField(
                            copy.currentPasswordLabel,
                            viewModel.currentPassword,
                            { viewModel.currentPassword = it },
                            keyboardType = KeyboardType.Password,
                            isPassword = true
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    FamilyTextField(
                        copy.newPasswordLabel,
                        viewModel.newPassword,
                        { viewModel.newPassword = it },
                        keyboardType = KeyboardType.Password,
                        isPassword = true
                    )
                    Spacer(Modifier.height(10.dp))
                    FamilyTextField(
                        copy.confirmNewPasswordLabel,
                        viewModel.confirmPassword,
                        { viewModel.confirmPassword = it },
                        keyboardType = KeyboardType.Password,
                        isPassword = true,
                        isError = viewModel.confirmPassword.isNotBlank() && viewModel.confirmPassword != viewModel.newPassword,
                        errorText = copy.passwordsDontMatch
                    )
                    viewModel.passwordError?.let {
                        Text(copy.errorMessage(it) ?: "", color = FamilyColors.ErrorRed, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            if (viewModel.passwordChanged) {
                TextButton(onClick = onDismiss) { Text(copy.doneButton, color = FamilyColors.Blue) }
            } else {
                TextButton(
                    onClick = { if (setting) viewModel.setPassword() else viewModel.changePassword() },
                    enabled = formValid && !viewModel.isChangingPassword
                ) {
                    Text(if (viewModel.isChangingPassword) copy.savingDots else copy.saveButton, color = FamilyColors.Blue)
                }
            }
        },
        dismissButton = {
            if (!viewModel.passwordChanged) {
                TextButton(onClick = onDismiss) { Text(copy.cancelButton, color = FamilyColors.TextSecondary) }
            }
        }
    )
}

/** The codes the server stores as `deletion_reason` — never translated. The label shown to
 *  the family member comes from [FamilyStrings.Copy.deleteReasonLabel] instead. */
private val DELETE_REASON_CODES = listOf(
    "senior_no_longer_needs", "not_caregiver", "duplicate", "privacy", "not_useful", "other"
)

@Composable
private fun FamilyDeleteAccountScreen(
    viewModel: FamilyProfileViewModel,
    onBack: () -> Unit,
    onDeleted: () -> Unit
) {
    val copy = LocalFamilyCopy.current
    var selectedReason by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }

    val noteRequired = selectedReason == "other"
    val canDelete = selectedReason != null &&
        (!noteRequired || note.isNotBlank()) &&
        !viewModel.isDeleting

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FamilyColors.HeaderBlue)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = copy.backContentDescription, tint = Color.White)
            }
            Text(copy.deleteAccountTitle, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(
                copy.deleteAccountWarning,
                color = FamilyColors.TextPrimary,
                fontSize = 15.sp
            )

            Text(
                copy.tellUsWhyRequired,
                color = FamilyColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 22.dp, bottom = 4.dp)
            )

            DELETE_REASON_CODES.forEach { code ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedReason = code }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selectedReason == code, onClick = { selectedReason = code })
                    Text(copy.deleteReasonLabel(code), color = FamilyColors.TextPrimary, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            FamilyTextField(
                copy.tellUsMoreLabel,
                note,
                { note = it },
                isError = noteRequired && note.isBlank(),
                errorText = copy.tellUsMoreError
            )

            viewModel.deleteError?.let {
                Text(copy.errorMessage(it) ?: "", color = FamilyColors.ErrorRed, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp))
            }

            Spacer(Modifier.height(28.dp))
            BluePillButton(
                text = if (viewModel.isDeleting) copy.deletingEllipsis else copy.deleteMyAccountButton,
                enabled = canDelete,
                onClick = { showConfirm = true }
            )
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(copy.deleteConfirmTitle) },
            text = { Text(copy.deleteConfirmBody) },
            confirmButton = {
                TextButton(onClick = {
                    val reason = selectedReason
                    if (reason != null) {
                        showConfirm = false
                        viewModel.deleteAccount(reason, note.trim().ifBlank { null }, onDeleted)
                    }
                }) {
                    Text(copy.deleteButton, color = FamilyColors.ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text(copy.cancelButton, color = FamilyColors.TextSecondary)
                }
            }
        )
    }
}

private fun initials(name: String): String =
    name.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
