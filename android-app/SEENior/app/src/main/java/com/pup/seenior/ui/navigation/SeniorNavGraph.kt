package com.pup.seenior.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pup.seenior.ui.family.FamilyAuthViewModel
import com.pup.seenior.ui.family.FamilyLoginScreen
import com.pup.seenior.ui.family.FamilySignUpScreen
import com.pup.seenior.ui.onboarding.AllSetScreen
import com.pup.seenior.ui.onboarding.OnboardingQuestionnaireScreen
import com.pup.seenior.ui.onboarding.OnboardingViewModel
import com.pup.seenior.ui.onboarding.PermissionsScreen
import com.pup.seenior.ui.onboarding.RoleSelectionScreen
import com.pup.seenior.ui.onboarding.SignUpScreen
import com.pup.seenior.ui.onboarding.SplashScreen
import com.pup.seenior.ui.onboarding.TermsConditionsScreen
import com.pup.seenior.ui.onboarding.WelcomeScreen

object SeniorRoutes {
    const val SPLASH = "splash"
    const val WELCOME = "welcome"
    const val ROLE_SELECT = "role_select"
    const val SIGN_UP = "sign_up"
    const val TERMS = "terms"
    const val QUESTIONNAIRE = "questionnaire"
    const val PERMISSIONS = "permissions"
    const val ALL_SET = "all_set"
    const val HOME = "home"

    // Family flow (linking a senior happens later, inside the dashboard's Link tab)
    const val FAMILY_SIGNUP = "family_signup"
    const val FAMILY_LOGIN = "family_login"
    const val FAMILY_HOME = "family_home"
}

@Composable
fun SeniorNavGraph(navController: NavHostController = rememberNavController()) {
    val onboardingViewModel: OnboardingViewModel = viewModel()
    // Shared across Sign Up / Log In so switching between them (via their cross-links)
    // doesn't lose anything already typed.
    val familyAuthViewModel: FamilyAuthViewModel = viewModel()

    NavHost(navController = navController, startDestination = SeniorRoutes.SPLASH) {
        composable(SeniorRoutes.SPLASH) {
            SplashScreen(
                onOnboarded = {
                    navController.navigate(SeniorRoutes.HOME) { popUpTo(SeniorRoutes.SPLASH) { inclusive = true } }
                },
                onFamilyPaired = {
                    navController.navigate(SeniorRoutes.FAMILY_HOME) { popUpTo(SeniorRoutes.SPLASH) { inclusive = true } }
                },
                onNotOnboarded = {
                    navController.navigate(SeniorRoutes.WELCOME) { popUpTo(SeniorRoutes.SPLASH) { inclusive = true } }
                }
            )
        }
        composable(SeniorRoutes.WELCOME) {
            WelcomeScreen(onGetStarted = { navController.navigate(SeniorRoutes.ROLE_SELECT) })
        }
        composable(SeniorRoutes.ROLE_SELECT) {
            RoleSelectionScreen(
                onSeniorSelected = { navController.navigate(SeniorRoutes.SIGN_UP) },
                onFamilyMemberSelected = { navController.navigate(SeniorRoutes.FAMILY_SIGNUP) }
            )
        }
        composable(SeniorRoutes.SIGN_UP) {
            SignUpScreen(
                viewModel = onboardingViewModel,
                onBack = { navController.popBackStack() },
                onNext = { navController.navigate(SeniorRoutes.TERMS) }
            )
        }
        composable(SeniorRoutes.TERMS) {
            TermsConditionsScreen(
                onBack = { navController.popBackStack() },
                onNext = { navController.navigate(SeniorRoutes.QUESTIONNAIRE) }
            )
        }
        composable(SeniorRoutes.QUESTIONNAIRE) {
            OnboardingQuestionnaireScreen(
                viewModel = onboardingViewModel,
                onBack = { navController.popBackStack() },
                onNext = { navController.navigate(SeniorRoutes.PERMISSIONS) }
            )
        }
        composable(SeniorRoutes.PERMISSIONS) {
            PermissionsScreen(
                onBack = { navController.popBackStack() },
                onAllGranted = { navController.navigate(SeniorRoutes.ALL_SET) }
            )
        }
        composable(SeniorRoutes.ALL_SET) {
            AllSetScreen(
                viewModel = onboardingViewModel,
                onContinue = {
                    navController.navigate(SeniorRoutes.HOME) { popUpTo(SeniorRoutes.SPLASH) { inclusive = true } }
                }
            )
        }
        composable(SeniorRoutes.HOME) {
            SeniorDashboard()
        }

        // ---- Family flow ----
        // Sign up / log in land directly on the family home; linking a senior is optional
        // and done later from the dashboard's Link tab (or the home screen's empty state).
        composable(SeniorRoutes.FAMILY_SIGNUP) {
            FamilySignUpScreen(
                viewModel = familyAuthViewModel,
                onBack = { navController.popBackStack() },
                onSignedUp = {
                    navController.navigate(SeniorRoutes.FAMILY_HOME) {
                        popUpTo(SeniorRoutes.ROLE_SELECT) { inclusive = true }
                    }
                },
                onGoToLogin = { navController.navigate(SeniorRoutes.FAMILY_LOGIN) }
            )
        }
        composable(SeniorRoutes.FAMILY_LOGIN) {
            FamilyLoginScreen(
                viewModel = familyAuthViewModel,
                onBack = { navController.popBackStack() },
                onLoggedIn = {
                    navController.navigate(SeniorRoutes.FAMILY_HOME) {
                        popUpTo(SeniorRoutes.ROLE_SELECT) { inclusive = true }
                    }
                },
                onGoToSignUp = { navController.navigate(SeniorRoutes.FAMILY_SIGNUP) }
            )
        }
        composable(SeniorRoutes.FAMILY_HOME) {
            FamilyDashboard(
                onLoggedOut = {
                    navController.navigate(SeniorRoutes.WELCOME) {
                        popUpTo(SeniorRoutes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
    }
}
