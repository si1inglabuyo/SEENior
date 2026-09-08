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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
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
@Composable
fun FamilyProfileScreen(onLoggedOut: () -> Unit) {
    val viewModel: FamilyProfileViewModel = viewModel()
    LaunchedEffect(Unit) { viewModel.refresh() }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    when {
        editing -> FamilyEditProfileScreen(viewModel = viewModel, onBack = { editing = false })
        deleting -> FamilyDeleteAccountScreen(
            viewModel = viewModel,
            onBack = { deleting = false },
            // Account is gone server-side; onLoggedOut already routes to the pre-auth flow.
            onDeleted = onLoggedOut
        )
        else -> FamilyProfileHome(
            viewModel = viewModel,
            onEditProfile = { editing = true },
            onDeleteAccount = { deleting = true },
            onLoggedOut = onLoggedOut
        )
    }
}

@Composable
private fun FamilyProfileHome(
    viewModel: FamilyProfileViewModel,
    onEditProfile: () -> Unit,
    onDeleteAccount: () -> Unit,
    onLoggedOut: () -> Unit
) {
    val context = LocalContext.current
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val name = viewModel.user?.fullName?.takeIf { it.isNotBlank() } ?: "Family Member"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
    ) {
        BlueHeader(Icons.Filled.Person, "Profile")

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
                Text("Family Member", color = FamilyColors.TextSecondary, fontSize = 14.sp)
            }

            Text("MY INFO", color = FamilyColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
            ProfileRow(
                icon = Icons.Filled.Edit,
                title = "Edit profile",
                subtitle = "Full name, mobile number, password",
                onClick = onEditProfile
            )

            Text("HELP & INFORMATION", color = FamilyColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
            ProfileRow(icon = Icons.Filled.Info, title = "About this app")
            Spacer(Modifier.height(10.dp))
            ProfileRow(icon = Icons.AutoMirrored.Filled.MenuBook, title = "How to use")
            Spacer(Modifier.height(10.dp))
            ProfileRow(icon = Icons.AutoMirrored.Filled.HelpOutline, title = "FAQs")
            Spacer(Modifier.height(10.dp))
            ProfileRow(icon = Icons.AutoMirrored.Outlined.Chat, title = "Feedback & requests")

            Spacer(Modifier.height(24.dp))
            ProfileRow(
                icon = Icons.AutoMirrored.Filled.Logout,
                title = "Log Out",
                titleColor = FamilyColors.ErrorRed,
                iconTint = FamilyColors.ErrorRed,
                iconBackground = FamilyColors.ErrorRed.copy(alpha = 0.1f),
                showChevron = false,
                onClick = { showLogoutConfirm = true }
            )

            Spacer(Modifier.height(10.dp))
            ProfileRow(
                icon = Icons.Filled.DeleteForever,
                title = "Delete account",
                subtitle = "Permanently remove your account and unlink your seniors",
                titleColor = FamilyColors.ErrorRed,
                iconTint = FamilyColors.ErrorRed,
                iconBackground = FamilyColors.ErrorRed.copy(alpha = 0.1f),
                onClick = onDeleteAccount
            )

            viewModel.error?.let {
                Text(it, color = FamilyColors.ErrorRed, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Log out?") },
            text = { Text("You'll need to sign in again to see your linked seniors.") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    // Clears the session and releases this device's push token, in that
                    // order and on a scope that survives the navigation below. Leaving the
                    // token behind would keep this handset receiving the previous account's
                    // alerts, which name the senior (CLAUDE.md §11).
                    PushTokenRegistrar.signOutAsync(context)
                    onLoggedOut()
                }) {
                    Text("Log Out", color = FamilyColors.ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Cancel", color = FamilyColors.TextSecondary)
                }
            }
        )
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Edit profile", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
            FamilyTextField("Full name", viewModel.fullName, { viewModel.fullName = it })

            Spacer(Modifier.height(14.dp))
            FamilyTextField(
                "Mobile number",
                viewModel.phone,
                { viewModel.phone = it },
                keyboardType = KeyboardType.Phone,
                isError = viewModel.phone.isNotBlank() && !com.pup.seenior.validation.PhilippinePhone.isValid(viewModel.phone),
                errorText = "Enter a valid PH mobile number (09XXXXXXXXX or +639XXXXXXXXX)"
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
                    if (viewModel.hasPassword) "Change Password" else "Set a Password",
                    color = FamilyColors.Blue,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            viewModel.error?.let {
                Text(it, color = FamilyColors.ErrorRed, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp))
            }

            Spacer(Modifier.height(28.dp))
            BluePillButton(
                text = if (viewModel.isSaving) "SAVING…" else "SAVE CHANGES",
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
                    viewModel.passwordChanged && setting -> "Password set"
                    viewModel.passwordChanged -> "Password changed"
                    setting -> "Set a Password"
                    else -> "Change Password"
                }
            )
        },
        text = {
            if (viewModel.passwordChanged) {
                Text(
                    if (setting)
                        "You can now sign in with your email and this password, or keep using Google."
                    else
                        "Your password was updated successfully."
                )
            } else {
                Column {
                    if (setting) {
                        Text(
                            "You signed up with Google. Add a password to also sign in with " +
                                (viewModel.user?.email ?: "your email") + ".",
                            color = FamilyColors.TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    } else {
                        FamilyTextField(
                            "Current password",
                            viewModel.currentPassword,
                            { viewModel.currentPassword = it },
                            keyboardType = KeyboardType.Password,
                            isPassword = true
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    FamilyTextField(
                        "New password",
                        viewModel.newPassword,
                        { viewModel.newPassword = it },
                        keyboardType = KeyboardType.Password,
                        isPassword = true
                    )
                    Spacer(Modifier.height(10.dp))
                    FamilyTextField(
                        "Confirm new password",
                        viewModel.confirmPassword,
                        { viewModel.confirmPassword = it },
                        keyboardType = KeyboardType.Password,
                        isPassword = true,
                        isError = viewModel.confirmPassword.isNotBlank() && viewModel.confirmPassword != viewModel.newPassword,
                        errorText = "Passwords don't match"
                    )
                    viewModel.passwordError?.let {
                        Text(it, color = FamilyColors.ErrorRed, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            if (viewModel.passwordChanged) {
                TextButton(onClick = onDismiss) { Text("Done", color = FamilyColors.Blue) }
            } else {
                TextButton(
                    onClick = { if (setting) viewModel.setPassword() else viewModel.changePassword() },
                    enabled = formValid && !viewModel.isChangingPassword
                ) {
                    Text(if (viewModel.isChangingPassword) "Saving…" else "Save", color = FamilyColors.Blue)
                }
            }
        },
        dismissButton = {
            if (!viewModel.passwordChanged) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = FamilyColors.TextSecondary) }
            }
        }
    )
}

/** code → label. The code is what the server stores; the label is display only. */
private val DELETE_REASONS = listOf(
    "senior_no_longer_needs" to "The senior I monitored no longer needs this",
    "not_caregiver" to "I'm no longer a caregiver for this senior",
    "duplicate" to "I made this account by mistake or it's a duplicate",
    "privacy" to "Privacy concerns",
    "not_useful" to "It didn't work the way I expected",
    "other" to "Another reason",
)

@Composable
private fun FamilyDeleteAccountScreen(
    viewModel: FamilyProfileViewModel,
    onBack: () -> Unit,
    onDeleted: () -> Unit
) {
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Delete account", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(
                "This removes your account and unlinks every senior you monitor. They will no " +
                    "longer send alerts to you, and you will need to sign up again to use the app. " +
                    "This cannot be undone.",
                color = FamilyColors.TextPrimary,
                fontSize = 15.sp
            )

            Text(
                "Please tell us why (required)",
                color = FamilyColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 22.dp, bottom = 4.dp)
            )

            DELETE_REASONS.forEach { (code, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedReason = code }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selectedReason == code, onClick = { selectedReason = code })
                    Text(label, color = FamilyColors.TextPrimary, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            FamilyTextField(
                "Tell us more",
                note,
                { note = it },
                isError = noteRequired && note.isBlank(),
                errorText = "Please tell us your reason"
            )

            viewModel.deleteError?.let {
                Text(it, color = FamilyColors.ErrorRed, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp))
            }

            Spacer(Modifier.height(28.dp))
            BluePillButton(
                text = if (viewModel.isDeleting) "DELETING…" else "DELETE MY ACCOUNT",
                enabled = canDelete,
                onClick = { showConfirm = true }
            )
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Delete your account?") },
            text = { Text("Your account is removed and every senior is unlinked. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    val reason = selectedReason
                    if (reason != null) {
                        showConfirm = false
                        viewModel.deleteAccount(reason, note.trim().ifBlank { null }, onDeleted)
                    }
                }) {
                    Text("Delete", color = FamilyColors.ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("Cancel", color = FamilyColors.TextSecondary)
                }
            }
        )
    }
}

private fun initials(name: String): String =
    name.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
