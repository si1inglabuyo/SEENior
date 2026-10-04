package com.pup.seenior.network

import com.pup.seenior.network.dto.AccountDeletionRequest
import com.pup.seenior.network.dto.AlertDispatchRequest
import com.pup.seenior.network.dto.AlertDto
import com.pup.seenior.network.dto.CancelAlertRequest
import com.pup.seenior.network.dto.ClosedAlertDto
import com.pup.seenior.network.dto.ChangePasswordRequest
import com.pup.seenior.network.dto.HeartbeatRequest
import com.pup.seenior.network.dto.ContactDto
import com.pup.seenior.network.dto.CreateAlertRequest
import com.pup.seenior.network.dto.CreateSeniorRequest
import com.pup.seenior.network.dto.DeviceDto
import com.pup.seenior.network.dto.FamilyContactDto
import com.pup.seenior.network.dto.FirebaseSignInRequest
import com.pup.seenior.network.dto.GoogleSignInRequest
import com.pup.seenior.network.dto.InviteCodeDto
import com.pup.seenior.network.dto.LanguagePreferenceRequest
import com.pup.seenior.network.dto.PairRequest
import com.pup.seenior.network.dto.PairResponseDto
import com.pup.seenior.network.dto.RegisterDeviceRequest
import com.pup.seenior.network.dto.RegisterRequest
import com.pup.seenior.network.dto.SeniorDto
import com.pup.seenior.network.dto.SetPasswordRequest
import com.pup.seenior.network.dto.TokenDto
import com.pup.seenior.network.dto.UpdateLocationRequest
import com.pup.seenior.network.dto.UpdateProfileRequest
import com.pup.seenior.network.dto.UpdateSeniorRequest
import com.pup.seenior.network.dto.UpdateSeverityRequest
import com.pup.seenior.network.dto.UserDto
import com.pup.seenior.network.dto.VerifyCodeRequest
import com.pup.seenior.network.dto.VerifyCodeResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    // ---- Senior side (no auth — identified by sync_id) ----

    @POST("seniors")
    suspend fun createSenior(@Body body: CreateSeniorRequest): SeniorDto

    @PATCH("seniors/{syncId}")
    suspend fun updateSenior(
        @Path("syncId") syncId: String,
        @Body body: UpdateSeniorRequest
    ): SeniorDto

    // Which of the senior's recent alerts a family contact or the barangay has closed; the
    // phone has no other way to learn that.
    @GET("seniors/{syncId}/closed-alerts")
    suspend fun getClosedAlerts(@Path("syncId") syncId: String): List<ClosedAlertDto>

    @PATCH("alerts/{syncId}/cancel")
    suspend fun cancelAlert(
        @Path("syncId") syncId: String,
        @Body body: CancelAlertRequest
    ): AlertDto

    // Upgrade-only and idempotent on the server, so a retry can't talk a level back down.
    @PATCH("alerts/{syncId}/severity")
    suspend fun updateAlertSeverity(
        @Path("syncId") syncId: String,
        @Body body: UpdateSeverityRequest
    ): AlertDto

    // Set-once and idempotent on the server. The fix can land after the alert was posted (an
    // SOS posts at the end of a 10 s cancel window while GPS gets 20 s).
    @PATCH("alerts/{syncId}/location")
    suspend fun updateAlertLocation(
        @Path("syncId") syncId: String,
        @Body body: UpdateLocationRequest
    ): AlertDto

    @POST("seniors/{syncId}/heartbeat")
    suspend fun sendHeartbeat(
        @Path("syncId") syncId: String,
        @Body body: HeartbeatRequest
    ): SeniorDto

    @POST("seniors/{syncId}/invite")
    suspend fun generateInvite(@Path("syncId") syncId: String): InviteCodeDto

    // Soft-deletes the senior's cloud record and unlinks their contacts. No auth; the sync_id is
    // the credential. The phone wipes its own database separately. Idempotent.
    @POST("seniors/{syncId}/delete")
    suspend fun deleteSenior(
        @Path("syncId") syncId: String,
        @Body body: AccountDeletionRequest
    )

    @GET("seniors/{syncId}/family-contacts")
    suspend fun getFamilyContacts(@Path("syncId") syncId: String): List<FamilyContactDto>

    @DELETE("seniors/{syncId}/family-contacts/{contactId}")
    suspend fun removeFamilyContact(
        @Path("syncId") syncId: String,
        @Path("contactId") contactId: Int
    )

    // No auth: the senior has no account and is identified by sync_id.
    @POST("alerts")
    suspend fun postAlert(@Body body: CreateAlertRequest): AlertDto

    // ---- Family side ----

    @POST("contacts/verify")
    suspend fun verifyCode(@Body body: VerifyCodeRequest): VerifyCodeResponse

    // Requires a logged-in caller; account creation always happens first.
    @POST("contacts/pair")
    suspend fun pairContact(
        @Body body: PairRequest,
        @Header("Authorization") auth: String
    ): PairResponseDto

    @GET("contacts/me")
    suspend fun getMyContacts(@Header("Authorization") auth: String): List<ContactDto>

    @DELETE("contacts/{contactId}")
    suspend fun unlinkContact(
        @Header("Authorization") auth: String,
        @Path("contactId") contactId: Int
    )

    // ---- Account / profile (family side) ----

    // The backend expects form-encoded "username"/"password" fields; "username" holds the
    // email for family accounts or the pre-assigned username for barangay responders.
    @FormUrlEncoded
    @POST("auth/login")
    suspend fun login(
        @Field("username") emailOrUsername: String,
        @Field("password") password: String
    ): TokenDto

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): TokenDto

    @POST("auth/google")
    suspend fun googleSignIn(@Body body: GoogleSignInRequest): TokenDto

    @POST("auth/firebase")
    suspend fun firebaseSignIn(@Body body: FirebaseSignInRequest): TokenDto

    @GET("auth/me")
    suspend fun getMe(@Header("Authorization") auth: String): UserDto

    @PATCH("auth/me")
    suspend fun updateProfile(
        @Header("Authorization") auth: String,
        @Body body: UpdateProfileRequest
    ): UserDto

    // Family app's Language toggle. Saves immediately, as on the senior side.
    @PATCH("auth/me/language")
    suspend fun updateLanguage(
        @Header("Authorization") auth: String,
        @Body body: LanguagePreferenceRequest
    ): UserDto

    @POST("auth/change-password")
    suspend fun changePassword(
        @Header("Authorization") auth: String,
        @Body body: ChangePasswordRequest
    )

    // Adds a password to a Google-only account. 400 if it already has one.
    @POST("auth/set-password")
    suspend fun setPassword(
        @Header("Authorization") auth: String,
        @Body body: SetPasswordRequest
    )

    // Soft-deletes the caller's own family account: deactivates it, unlinks every pairing,
    // drops device tokens and frees the email/username.
    @POST("auth/me/delete")
    suspend fun deleteMyAccount(
        @Header("Authorization") auth: String,
        @Body body: AccountDeletionRequest
    )

    // ---- Push registration (family side) ----

    @POST("devices/register")
    suspend fun registerDevice(
        @Body body: RegisterDeviceRequest,
        @Header("Authorization") auth: String
    ): DeviceDto

    @DELETE("devices/{token}")
    suspend fun unregisterDevice(
        @Path("token") token: String,
        @Header("Authorization") auth: String
    )

    @GET("devices")
    suspend fun getMyDevices(@Header("Authorization") auth: String): List<DeviceDto>

    // ---- Alerts (family side) ----

    @GET("alerts")
    suspend fun getAlerts(
        @Query("senior_sync_id") seniorSyncId: String,
        @Header("Authorization") auth: String
    ): List<AlertDto>

    /** Tells the server this phone got the alert push, so it does not also send an SMS. */
    @POST("alerts/{syncId}/received")
    suspend fun confirmAlertPushReceived(
        @Path("syncId") syncId: String,
        @Header("Authorization") auth: String
    ): AlertDto

    /** Family says the alert was raised in error. Closes it as `false_positive`, which the
     *  senior's phone reads as evidence for loosening that block's trigger. */
    @PATCH("alerts/{syncId}/false-positive")
    suspend fun markAlertFalsePositive(
        @Path("syncId") syncId: String,
        @Header("Authorization") auth: String
    ): AlertDto

    @PATCH("alerts/{syncId}/acknowledge")
    suspend fun acknowledgeAlert(
        @Path("syncId") syncId: String,
        @Header("Authorization") auth: String
    ): AlertDto

    @PATCH("alerts/{syncId}/dispatch")
    suspend fun dispatchAlert(
        @Path("syncId") syncId: String,
        @Body body: AlertDispatchRequest,
        @Header("Authorization") auth: String
    ): AlertDto

    @PATCH("alerts/{syncId}/resolve")
    suspend fun resolveAlert(
        @Path("syncId") syncId: String,
        @Header("Authorization") auth: String
    ): AlertDto
}
