package com.pup.seenior.network

import com.pup.seenior.network.dto.CreateSeniorRequest
import com.pup.seenior.network.dto.FamilyContactDto
import com.pup.seenior.network.dto.InviteCodeDto
import com.pup.seenior.network.dto.PairRequest
import com.pup.seenior.network.dto.PairResponseDto
import com.pup.seenior.network.dto.SeniorDto
import com.pup.seenior.network.dto.VerifyCodeRequest
import com.pup.seenior.network.dto.VerifyCodeResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {

    // ---- Senior side (no auth — identified by sync_id) ----

    @POST("seniors")
    suspend fun createSenior(@Body body: CreateSeniorRequest): SeniorDto

    @POST("seniors/{syncId}/invite")
    suspend fun generateInvite(@Path("syncId") syncId: String): InviteCodeDto

    @GET("seniors/{syncId}/family-contacts")
    suspend fun getFamilyContacts(@Path("syncId") syncId: String): List<FamilyContactDto>

    @DELETE("seniors/{syncId}/family-contacts/{contactId}")
    suspend fun removeFamilyContact(
        @Path("syncId") syncId: String,
        @Path("contactId") contactId: Int
    )

    // ---- Family side ----

    @POST("contacts/verify")
    suspend fun verifyCode(@Body body: VerifyCodeRequest): VerifyCodeResponse

    @POST("contacts/pair")
    suspend fun pairContact(@Body body: PairRequest): PairResponseDto
}
