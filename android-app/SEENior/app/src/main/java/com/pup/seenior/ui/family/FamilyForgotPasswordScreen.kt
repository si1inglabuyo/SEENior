package com.pup.seenior.ui.family

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Family Forgot Password — reached from the Log In screen's "Forget Password" link.
 *  One email field; Firebase itself sends the reset email and hosts the reset page, so
 *  there is nothing for this screen to do after the call succeeds except say so. */
@Composable
fun FamilyForgotPasswordScreen(
    viewModel: FamilyAuthViewModel,
    onBack: () -> Unit
) {
    // Clears any stale sent/error state from a previous visit this session, and again
    // when the screen is left, so coming back to it starts clean.
    DisposableEffect(Unit) {
        viewModel.resetForgotPasswordState()
        onDispose { viewModel.resetForgotPasswordState() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = FamilyColors.TextPrimary)
            }
            Text(
                "Forgot Password",
                color = FamilyColors.Blue,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(end = 48.dp)
            )
        }

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            if (viewModel.resetSent) {
                Text(
                    "If an account exists for that email, a password reset link is on its way. " +
                        "Check your inbox (and spam folder) and follow the link to set a new password.",
                    color = FamilyColors.TextSecondary,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 16.dp)
                )
            } else {
                Text(
                    "Enter the email address on your account. We'll send a link to reset your password.",
                    color = FamilyColors.TextSecondary,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 12.dp, bottom = 20.dp)
                )

                FamilyTextField(
                    "Email Address",
                    viewModel.forgotPasswordEmail,
                    { viewModel.forgotPasswordEmail = it },
                    keyboardType = KeyboardType.Email
                )

                viewModel.resetError?.let {
                    Text(it, color = FamilyColors.ErrorRed, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
                }

                Spacer(Modifier.height(24.dp))
                BluePillButton(
                    text = if (viewModel.isSendingReset) "SENDING…" else "SEND RESET LINK",
                    enabled = viewModel.isForgotPasswordValid && !viewModel.isSendingReset,
                    onClick = { viewModel.sendPasswordReset() }
                )
            }
        }
    }
}
