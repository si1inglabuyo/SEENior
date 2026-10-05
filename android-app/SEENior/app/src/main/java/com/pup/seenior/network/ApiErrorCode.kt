package com.pup.seenior.network

import org.json.JSONObject
import retrofit2.HttpException

/**
 * The stable `detail.code` the backend puts on errors the apps translate themselves, such as
 * the contact limits. Null for a plain-string `detail` or an unreadable body.
 */
fun HttpException.apiErrorCode(): String? = runCatching {
    JSONObject(response()?.errorBody()?.string().orEmpty()).getJSONObject("detail").getString("code")
}.getOrNull()

object ApiErrorCodes {
    const val SENIOR_CONTACT_LIMIT = "senior_contact_limit"
    const val FAMILY_SENIOR_LIMIT = "family_senior_limit"
}
