package com.pup.seenior.network

import com.pup.seenior.network.dto.AlertDispatchRequest
import com.pup.seenior.network.dto.AlertDto
import com.pup.seenior.network.dto.ChangePasswordRequest
import com.pup.seenior.network.dto.ContactDto
import com.pup.seenior.network.dto.CreateAlertRequest
import com.pup.seenior.network.dto.CreateSeniorRequest
import com.pup.seenior.network.dto.FamilyContactDto
import com.pup.seenior.network.dto.GoogleSignInRequest
import com.pup.seenior.network.dto.InviteCodeDto
import com.pup.seenior.network.dto.PairRequest
import com.pup.seenior.network.dto.PairResponseDto
import com.pup.seenior.network.dto.RegisterRequest
import com.pup.seenior.network.dto.SeniorDto
import com.pup.seenior.network.dto.TokenDto
import com.pup.seenior.network.dto.UpdateProfileRequest
import com.pup.seenior.network.dto.UpdateSeniorRequest
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

    @POST("seniors/{syncId}/invite")
    suspend fun generateInvite(@Path("syncId") syncId: String): InviteCodeDto

    @GET("seniors/{syncId}/family-contacts")
    suspend fun getFamilyContacts(@Path("syncId") syncId: String): List<FamilyContactDto>

    @DELETE("seniors/{syncId}/family-contacts/{contactId}")
    suspend fun removeFamilyContact(
        @Path("syncId") syncId: String,
        @Path("contactId") contactId: Int
    )

    // No auth, same posture as the other senior-side routes — the senior has no account
    // (CLAUDE.md §2) and is identified purely by sync_id.
    @POST("alerts")
    suspend fun postAlert(@Body body: CreateAlertRequest): AlertDto

    // ---- Family side ----

    @POST("contacts/verify")
    suspend fun verifyCode(@Body body: VerifyCodeRequest): VerifyCodeResponse

    // Requires the caller to already be logged in — account creation (register/
    // login/Google) always happens first, as its own step.
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

    // OAuth2PasswordRequestForm on the backend expects standard form-encoded
    // fields named "username"/"password" - "username" holds the email address
    // for family accounts (or the pre-assigned username for barangay responders).
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

    @GET("auth/me")
    suspend fun getMe(@Header("Authorization") auth: String): UserDto

    @PATCH("auth/me")
    suspend fun updateProfile(
        @Header("Authorization") auth: String,
        @Body body: UpdateProfileRequest
    ): UserDto

    @POST("auth/change-password")
    suspend fun changePassword(
        @Header("Authorization") auth: String,
        @Body body: ChangePasswordRequest
    )

    // ---- Alerts (family side) ----

    @GET("alerts")
    suspend fun getAlerts(
        @Query("senior_sync_id") seniorSyncId: String,
        @Header("Authorization") auth: String
    ): List<AlertDto>

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
