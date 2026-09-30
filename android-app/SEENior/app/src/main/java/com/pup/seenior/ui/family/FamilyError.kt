package com.pup.seenior.ui.family

/**
 * What went wrong in a family ViewModel action, kept as data rather than a formatted English
 * string. ViewModels are plain classes, not Composables, so they cannot read `LocalFamilyCopy`
 * themselves to translate a message at the point of failure -- they store one of these instead,
 * and the screen calls [errorMessage] to render it in the account's language.
 */
sealed interface FamilyError {

    enum class Action {
        LoadAlerts, Acknowledge, DispatchBarangay, Resolve, FalseAlarm,
        LoadProfile, SaveProfile, ChangePassword, SetPassword, DeleteAccount,
        LoadSeniors, Unlink, LoadHomeActivity, VerifyCode, Connect
    }

    /** The four distinct "can't reach the server" wordings already in use across the app --
     *  kept as-is rather than unified, since that would be a copy change nobody asked for. */
    enum class NetworkVariant { Plain, CheckConnection, TryAgain, HasInternet }

    data class Server(val action: Action, val code: Int) : FamilyError
    data class Network(val variant: NetworkVariant = NetworkVariant.Plain) : FamilyError
    object SessionExpired : FamilyError
    object CurrentPasswordIncorrect : FamilyError
    object AccountAlreadyHasPassword : FamilyError
    object InvalidOrExpiredCode : FamilyError
    object CodeExpiredOrLimitReached : FamilyError
}

/** Null-safe on purpose -- most call sites hold a nullable `error: FamilyError?` and only
 *  render when it's set, so this lets them write `copy.errorMessage(viewModel.error)` directly. */
fun FamilyStrings.Copy.errorMessage(error: FamilyError?): String? = when (error) {
    null -> null
    is FamilyError.Server -> when (error.action) {
        FamilyError.Action.LoadAlerts -> couldNotLoadAlerts(error.code)
        FamilyError.Action.Acknowledge -> couldNotAcknowledge(error.code)
        FamilyError.Action.DispatchBarangay -> couldNotDispatchBarangay(error.code)
        FamilyError.Action.Resolve -> couldNotResolve(error.code)
        FamilyError.Action.FalseAlarm -> couldNotMarkFalseAlarm(error.code)
        FamilyError.Action.LoadProfile -> couldNotLoadProfile(error.code)
        FamilyError.Action.SaveProfile -> couldNotSaveProfile(error.code)
        FamilyError.Action.ChangePassword -> couldNotChangePassword(error.code)
        FamilyError.Action.SetPassword -> couldNotSetPassword(error.code)
        FamilyError.Action.DeleteAccount -> couldNotDeleteAccount(error.code)
        FamilyError.Action.LoadSeniors -> couldNotLoadSeniors(error.code)
        FamilyError.Action.Unlink -> couldNotUnlink(error.code)
        FamilyError.Action.LoadHomeActivity -> couldNotLoadHomeActivity(error.code)
        FamilyError.Action.VerifyCode -> couldNotVerifyCode(error.code)
        FamilyError.Action.Connect -> couldNotConnect(error.code)
    }
    is FamilyError.Network -> when (error.variant) {
        FamilyError.NetworkVariant.Plain -> couldNotReachServer
        FamilyError.NetworkVariant.CheckConnection -> couldNotReachServerCheckConnection
        FamilyError.NetworkVariant.TryAgain -> couldNotReachServerTryAgain
        FamilyError.NetworkVariant.HasInternet -> couldNotReachServerHasInternet
    }
    FamilyError.SessionExpired -> sessionExpiredMessage
    FamilyError.CurrentPasswordIncorrect -> currentPasswordIncorrect
    FamilyError.AccountAlreadyHasPassword -> accountAlreadyHasPassword
    FamilyError.InvalidOrExpiredCode -> invalidOrExpiredCode
    FamilyError.CodeExpiredOrLimitReached -> codeExpiredOrLimitReached
}
