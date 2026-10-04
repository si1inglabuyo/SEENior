package com.pup.seenior.network.dto

/** Mirrors backend AlertOut. escalationSteps is loosely typed since it is a free-form JSON timeline. */
data class AlertDto(
    val syncId: String,
    val riskLevel: String,
    val triggerType: String,
    val status: String,
    val locationClusterId: String?,
    val escalationSteps: List<Map<String, String>>?,
    val createdAt: String,
    val triggeredAt: String?,
    val resolvedAt: String?
)

/**
 * Mirrors backend AlertCreate: the senior's phone pushing one alert's metadata up. It carries
 * no sensor readings or deviation score (spec section 11). The backend mints its own
 * `sync_id`, so the returned [AlertDto.syncId] is the one both sides use afterwards.
 */
data class CreateAlertRequest(
    val seniorSyncId: String,
    val riskLevel: String,
    val triggerType: String,
    val locationClusterId: String? = null,
    val escalationSteps: List<Map<String, String>>? = null,
    // When this device's detector fired (Alert.triggeredAt), as an Instant so it always has an
    // explicit UTC offset. Different from created_at, which the server stamps on arrival (they
    // were 47 minutes apart once). Null only on the retry path, where the original moment is gone.
    val triggeredAt: String? = null
)

/** Mirrors backend AlertDispatchRequest (family requesting a barangay welfare check). */
/** Mirrors backend AlertCancel. The senior's id travels with the alert's so the server can
 *  check they belong together; the senior has no account, so this pairing is the credential. */
data class CancelAlertRequest(
    val seniorSyncId: String
)

/** Mirrors backend AlertSeverityUpdate: an already-sent alert that Layer 1 re-classified
 *  upwards. Carries the senior's id like [CancelAlertRequest]. */
data class UpdateSeverityRequest(
    val seniorSyncId: String,
    val riskLevel: String
)

/** Mirrors backend AlertLocationUpdate: a fix that arrived after the alert was posted.
 *  Carries the senior's id like [CancelAlertRequest]. */
data class UpdateLocationRequest(
    val seniorSyncId: String,
    val locationClusterId: String
)

data class AlertDispatchRequest(
    val reason: String,
    val notes: String?
)

/** Mirrors backend ClosedAlertOut: an alert a family contact or the barangay closed. Just the
 *  id and status, never who closed it or why. */
data class ClosedAlertDto(
    val syncId: String,
    val status: String
)
