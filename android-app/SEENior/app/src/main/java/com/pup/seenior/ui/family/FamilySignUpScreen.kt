package com.pup.seenior.ui.family

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.lifecycle.viewmodel.compose.viewModel

/** Family Sign Up (designs/family_contact/account_setup/Sign Up Screen.png). Creates the
 *  account up front, before any senior is paired - pairing happens later via the Link tab. */
@Composable
fun FamilySignUpScreen(
    viewModel: FamilyAuthViewModel,
    onBack: () -> Unit,
    onSignedUp: () -> Unit,
    onNeedsPhone: () -> Unit,
    onGoToLogin: () -> Unit
) {
    val launchGoogleSignIn = rememberGoogleSignInLauncher(
        // Google gives us no phone number, so a new Google account needs one collected before
        // it's usable as a family contact — see FamilyAuthViewModel.onGoogleIdToken.
        onIdToken = { token ->
            viewModel.onGoogleIdToken(token) { needsPhone ->
                if (needsPhone) onNeedsPhone() else onSignedUp()
            }
        },
        onError = { viewModel.onGoogleError(it) }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = FamilyColors.TextPrimary)
            }
        }

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
        ) {
            Text(
                "Tell Us About Yourself",
                color = FamilyColors.Blue,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text("Proceed with your setup.", color = FamilyColors.TextSecondary, fontSize = 15.sp)

            Spacer(Modifier.height(16.dp))
            FamilyTextField("First Name", viewModel.signUpFirstName, { viewModel.signUpFirstName = it })
            Spacer(Modifier.height(12.dp))
            FamilyTextField("Last Name", viewModel.signUpLastName, { viewModel.signUpLastName = it })
            Spacer(Modifier.height(12.dp))
            FamilyTextField(
                "Mobile Number",
                viewModel.signUpPhone,
                { input ->
                    viewModel.signUpPhone = input
                        .filterIndexed { index, c -> c.isDigit() || (c == '+' && index == 0) }
                        .take(13)
                },
                keyboardType = KeyboardType.Phone,
                isError = viewModel.signUpPhone.isNotBlank() && !com.pup.seenior.validation.PhilippinePhone.isValid(viewModel.signUpPhone),
                errorText = "Enter a valid PH mobile number (09XXXXXXXXX or +639XXXXXXXXX)"
            )
            Spacer(Modifier.height(12.dp))
            FamilyTextField("Email Address", viewModel.signUpEmail, { viewModel.signUpEmail = it }, keyboardType = KeyboardType.Email)
            Spacer(Modifier.height(12.dp))
            FamilyTextField("Password", viewModel.signUpPassword, { viewModel.signUpPassword = it }, keyboardType = KeyboardType.Password, isPassword = true)

            viewModel.signUpError?.let {
                Text(it, color = FamilyColors.ErrorRed, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            }

            Text(
                "By continuing, you agree to Terms of Use and Privacy Policy.",
                color = FamilyColors.TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
            )

            Spacer(Modifier.height(14.dp))
            BluePillButton(
                text = if (viewModel.isSigningUp) "SIGNING UP…" else "SIGN UP",
                enabled = viewModel.isSignUpValid && !viewModel.isSigningUp,
                onClick = { viewModel.signUp(onSignedUp) }
            )

            Text(
                "or sign up with",
                color = FamilyColors.TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .border(1.dp, FamilyColors.Blue, RoundedCornerShape(26.dp))
                    .clickable(enabled = !viewModel.isGoogleSigningIn) { launchGoogleSignIn() },
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (viewModel.isGoogleSigningIn) {
                    CircularProgressIndicator(color = FamilyColors.Blue, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    GoogleGlyph()
                    Text(
                        "Sign Up With Google",
                        color = FamilyColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            }

            viewModel.googleError?.let {
                Text(it, color = FamilyColors.ErrorRed, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 24.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Text("Already have an account? ", color = FamilyColors.TextSecondary, fontSize = 14.sp)
                Text(
                    "Log in",
                    color = FamilyColors.Blue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onGoToLogin)
                )
            }
        }
    }
}

/** Google's official multi-color "G" mark, per Google Identity branding guidelines for
 *  "Sign in with Google" buttons. */
@Composable
fun GoogleGlyph() {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(com.pup.seenior.R.drawable.ic_google_logo),
        contentDescription = null,
        modifier = Modifier.size(20.dp)
    )
}
