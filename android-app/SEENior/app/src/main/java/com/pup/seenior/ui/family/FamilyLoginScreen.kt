package com.pup.seenior.ui.family

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Family Log In (designs/family_contact/account_setup/Log in Screen.png). */
@Composable
fun FamilyLoginScreen(
    viewModel: FamilyAuthViewModel,
    onBack: () -> Unit,
    onLoggedIn: () -> Unit,
    onGoToSignUp: () -> Unit
) {
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
                "Log In",
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
        ) {
            Text(
                "Proceed with your setup.",
                color = FamilyColors.TextSecondary,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 20.dp)
            )

            FamilyTextField("Email Address", viewModel.loginEmail, { viewModel.loginEmail = it }, keyboardType = KeyboardType.Email)
            Spacer(Modifier.height(12.dp))
            FamilyTextField("Password", viewModel.loginPassword, { viewModel.loginPassword = it }, keyboardType = KeyboardType.Password, isPassword = true)

            viewModel.loginError?.let {
                Text(it, color = FamilyColors.ErrorRed, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            }

            // Not wired to a real reset flow — no email-sending infra exists in this system.
            Text(
                "Forget Password",
                color = FamilyColors.Blue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                textAlign = TextAlign.End
            )

            Spacer(Modifier.height(24.dp))
            BluePillButton(
                text = if (viewModel.isLoggingIn) "LOGGING IN…" else "LOG IN",
                enabled = viewModel.isLoginValid && !viewModel.isLoggingIn,
                onClick = { viewModel.logIn(onLoggedIn) }
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 24.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Text("Don't have an account? ", color = FamilyColors.TextSecondary, fontSize = 14.sp)
                Text(
                    "Sign up",
                    color = FamilyColors.Blue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onGoToSignUp)
                )
            }
        }
    }
}
