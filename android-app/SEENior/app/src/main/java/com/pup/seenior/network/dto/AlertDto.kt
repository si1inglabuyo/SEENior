package com.pup.seenior.network.dto

/** Mirrors backend AlertOut. escalationSteps is left loosely typed (List<Map<String, String>>)
 *  since it's a free-form JSON timeline (step/at/reason/notes) rather than a fixed shape. */
data class AlertDto(
    val syncId: String,
    val riskLevel: String,
    val triggerType: String,
    val status: String,
    val locationClusterId: String?,
    val escalationSteps: List<Map<String, String>>?,
    val createdAt: String,
    val resolvedAt: String?
)

/**
 * Mirrors backend AlertCreate — the senior's phone pushing one alert's METADATA up.
 *
 * Deliberately carries no sensor readings and no deviation score: CLAUDE.md §11 keeps raw
 * behavioural data on the device, and the cloud row exists only so family/barangay can see
 * that something happened, not why in numeric detail.
 *
 * The backend mints its own `sync_id` and ignores any the device might send, so the returned
 * [AlertDto.syncId] is the one both sides must agree on afterwards.
 */
data class CreateAlertRequest(
    val seniorSyncId: String,
    val riskLevel: String,
    val triggerType: String,
    val locationClusterId: String? = null,
    val escalationSteps: List<Map<String, String>>? = null
)

/** Mirrors backend AlertDispatchRequest (family requesting a barangay welfare check). */
/** Mirrors backend AlertCancel. The senior's id travels with the alert's so the server can
 *  check the two belong together — the senior has no account, so this pairing is the credential. */
data class CancelAlertRequest(
    val seniorSyncId: String
)

/** Mirrors backend AlertSeverityUpdate -- an already-sent alert that Layer 1 has re-classified
 *  upwards. Carries the senior's id alongside the alert's for the same reason
 *  [CancelAlertRequest] does. */
data class UpdateSeverityRequest(
    val seniorSyncId: String,
    val riskLevel: String
)

/** Mirrors backend AlertLocationUpdate -- a fix that arrived after its alert had already been
 *  posted. Carries the senior's id alongside the alert's for the same reason
 *  [CancelAlertRequest] does. */
data class UpdateLocationRequest(
    val seniorSyncId: String,
    val locationClusterId: String
)

data class AlertDispatchRequest(
    val reason: String,
    val notes: String?
)
