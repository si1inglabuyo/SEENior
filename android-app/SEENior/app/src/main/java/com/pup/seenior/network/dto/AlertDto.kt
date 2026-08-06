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

/** Mirrors backend AlertDispatchRequest (family requesting a barangay welfare check). */
data class AlertDispatchRequest(
    val reason: String,
    val notes: String?
)
