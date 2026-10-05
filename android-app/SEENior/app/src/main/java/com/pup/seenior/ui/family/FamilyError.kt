package com.pup.seenior.ui.family

/**
 * What went wrong in a family ViewModel action, as data instead of a formatted string.
 * ViewModels can't read `LocalFamilyCopy`, so they store one of these and the screen calls
 * [errorMessage] to render it in the account's language.
 */
sealed interface FamilyError {

    enum class Action {
        LoadAlerts, Acknowledge, DispatchBarangay, Resolve, FalseAlarm,
        LoadProfile, SaveProfile, ChangePassword, SetPassword, DeleteAccount,
        LoadSeniors, Unlink, LoadHomeActivity, VerifyCode, Connect
    }

    /** The four "can't reach the server" wordings already in use, kept as they are. */
    enum class NetworkVariant { Plain, CheckConnection, TryAgain, HasInternet }

    data class Server(val action: Action, val code: Int) : FamilyError
    data class Network(val variant: NetworkVariant = NetworkVariant.Plain) : FamilyError
    object SessionExpired : FamilyError
    object CurrentPasswordIncorrect : FamilyError
    object AccountAlreadyHasPassword : FamilyError
    object InvalidOrExpiredCode : FamilyError
    object CodeExpiredOrLimitReached : FamilyError
    object SeniorContactLimit : FamilyError
    object FamilySeniorLimit : FamilyError
}

/** Null-safe, so call sites can write `copy.errorMessage(viewModel.error)` directly. */
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
    FamilyError.SeniorContactLimit -> seniorContactLimit
    FamilyError.FamilySeniorLimit -> familySeniorLimit
}
