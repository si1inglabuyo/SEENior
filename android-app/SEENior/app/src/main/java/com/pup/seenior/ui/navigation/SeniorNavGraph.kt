package com.pup.seenior.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pup.seenior.ui.home.HomeScreen
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
}

@Composable
fun SeniorNavGraph(navController: NavHostController = rememberNavController()) {
    val onboardingViewModel: OnboardingViewModel = viewModel()

    NavHost(navController = navController, startDestination = SeniorRoutes.SPLASH) {
        composable(SeniorRoutes.SPLASH) {
            SplashScreen(
                onTimeout = {
                    navController.navigate(SeniorRoutes.WELCOME) {
                        popUpTo(SeniorRoutes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(SeniorRoutes.WELCOME) {
            WelcomeScreen(onGetStarted = { navController.navigate(SeniorRoutes.ROLE_SELECT) })
        }
        composable(SeniorRoutes.ROLE_SELECT) {
            RoleSelectionScreen(
                onSeniorSelected = { navController.navigate(SeniorRoutes.SIGN_UP) },
                onFamilyMemberSelected = { /* Family member onboarding is out of scope for this flow. */ }
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
                    navController.navigate(SeniorRoutes.HOME) {
                        popUpTo(SeniorRoutes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(SeniorRoutes.HOME) {
            HomeScreen()
        }
    }
}
