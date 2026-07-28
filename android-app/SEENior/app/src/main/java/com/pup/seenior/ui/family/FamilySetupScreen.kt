package com.pup.seenior.ui.family

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Minimal family account setup. Stands in for the full designs/family_contact/account_setup
 * flow (signup/OTP/login/terms) which is a separate pipeline — this captures just what the
 * senior's Contacts list needs (name, phone) plus login credentials for the paired account.
 */
@Composable
fun FamilySetupScreen(viewModel: FamilyPairingViewModel, onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        BlueHeader(Icons.Filled.Link, "Set Up Your Account")

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Text(
                "Your details",
                color = FamilyColors.Blue,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp)
            )
            Text(
                "The senior will see your name and phone in their contacts list.",
                color = FamilyColors.TextSecondary,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
            )

            Spacer(Modifier.height(12.dp))
            FamilyTextField("Full name", viewModel.fullName, { viewModel.fullName = it })
            Spacer(Modifier.height(12.dp))
            FamilyTextField("Phone number", viewModel.phone, { viewModel.phone = it }, keyboardType = KeyboardType.Phone)
            Spacer(Modifier.height(12.dp))
            FamilyTextField("Choose a username", viewModel.username, { viewModel.username = it })
            Spacer(Modifier.height(12.dp))
            FamilyTextField("Choose a password", viewModel.password, { viewModel.password = it }, keyboardType = KeyboardType.Password, isPassword = true)

            Spacer(Modifier.height(28.dp))
            BluePillButton(text = "CONTINUE", enabled = viewModel.isSetupValid, onClick = onNext)
            Spacer(Modifier.height(24.dp))
        }
    }
}
