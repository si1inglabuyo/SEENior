package com.pup.seenior.ui.family

import android.app.Activity.RESULT_OK
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.pup.seenior.R

/**
 * Returns a function that launches Google's account picker. On success it calls [onIdToken]
 * with the ID token to send to POST /auth/google, and on failure or cancel it calls [onError].
 * Needs `google_web_client_id` in strings.xml (the "Web application" OAuth Client ID).
 */
@Composable
fun rememberGoogleSignInLauncher(
    onIdToken: (String) -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val webClientId = stringResource(R.string.google_web_client_id)

    val client = remember(webClientId) {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, options)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            onError("Google Sign-In was cancelled.")
            return@rememberLauncherForActivityResult
        }
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) onIdToken(idToken)
            else onError("Google didn't return a sign-in token. Please try again.")
        } catch (e: ApiException) {
            onError("Google Sign-In failed (${e.statusCode}).")
        }
    }

    return { launcher.launch(client.signInIntent) }
}
