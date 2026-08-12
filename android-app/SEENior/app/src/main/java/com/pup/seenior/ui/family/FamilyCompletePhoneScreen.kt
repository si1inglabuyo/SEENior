package com.pup.seenior.ui.family

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shown once, straight after a Google sign-in that produced an account with no mobile number.
 *
 * Google never gives us a phone number, and the password Sign Up screen collects one as a
 * required field — so without this step Google accounts were the only ones landing in the
 * system unreachable by SMS, which is the fallback the family escalation tier depends on
 * (CLAUDE.md §7). There is no "skip": the number is the point of the screen. Backing out
 * still leaves Profile -> Edit profile as the way to set it later.
 */
@Composable
fun FamilyCompletePhoneScreen(
    viewModel: FamilyAuthViewModel,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        BlueHeader(Icons.Outlined.Person, "One last thing")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                "Add your mobile number",
                color = FamilyColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Google doesn't share your number with us. We need it so the senior you " +
                    "monitor can see who to contact, and so we can still reach you by text " +
                    "message if a phone alert doesn't get through.",
                color = FamilyColors.TextSecondary,
                fontSize = 15.sp
            )

            Spacer(Modifier.height(28.dp))
            val showError = viewModel.completePhone.isNotBlank() && !viewModel.isCompletePhoneValid
            FamilyTextField(
                label = "Mobile Number",
                value = viewModel.completePhone,
                onValueChange = { input ->
                    // Same filtered entry as the Sign Up screen: digits plus a leading +.
                    viewModel.completePhone = input.filter { it.isDigit() || it == '+' }
                },
                keyboardType = KeyboardType.Phone,
                isError = showError,
                errorText = "Enter a valid PH mobile number (09XXXXXXXXX or +639XXXXXXXXX)"
            )

            Spacer(Modifier.height(28.dp))
            BluePillButton(
                text = if (viewModel.isSavingPhone) "SAVING…" else "CONTINUE",
                enabled = viewModel.isCompletePhoneValid && !viewModel.isSavingPhone,
                onClick = { viewModel.saveMissingPhone(onDone) }
            )

            viewModel.completePhoneError?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = FamilyColors.ErrorRed, fontSize = 14.sp)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
