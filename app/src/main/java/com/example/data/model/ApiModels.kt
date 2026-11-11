package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GatewayConfigDto(
    @Json(name = "poll_interval_seconds") val pollIntervalSeconds: Long = 15,
    @Json(name = "heartbeat_interval_seconds") val heartbeatIntervalSeconds: Long = 30
)

@JsonClass(generateAdapter = true)
data class PairRequest(
    @Json(name = "pairing_code") val pairingCode: String,
    @Json(name = "device_name") val deviceName: String,
    @Json(name = "installation_id") val installationId: String,
    @Json(name = "app_version") val appVersion: String,
    @Json(name = "android_version") val androidVersion: String
)

@JsonClass(generateAdapter = true)
data class PairResponse(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "device_token") val deviceToken: String,
    @Json(name = "token_type") val tokenType: String = "Bearer",
    @Json(name = "server_time") val serverTime: String,
    @Json(name = "config") val config: GatewayConfigDto
)

@JsonClass(generateAdapter = true)
data class HeartbeatRequest(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "app_version") val appVersion: String,
    @Json(name = "android_version") val androidVersion: String,
    @Json(name = "battery_pct") val batteryPct: Int,
    @Json(name = "sms_permission_granted") val smsPermissionGranted: Boolean,
    @Json(name = "gateway_active") val gatewayActive: Boolean,
    @Json(name = "sim_label") val simLabel: String,
    @Json(name = "network_type") val networkType: String? = null
)

@JsonClass(generateAdapter = true)
data class HeartbeatResponse(
    @Json(name = "server_time") val serverTime: String,
    @Json(name = "device_status") val deviceStatus: String,
    @Json(name = "sending_enabled") val sendingEnabled: Boolean,
    @Json(name = "config") val config: GatewayConfigDto
)

@JsonClass(generateAdapter = true)
data class JobDto(
    @Json(name = "id") val id: String,
    @Json(name = "lease_id") val leaseId: String,
    @Json(name = "lease_expires_at") val leaseExpiresAt: String,
    @Json(name = "destination_e164") val destinationE164: String,
    @Json(name = "body") val body: String,
    @Json(name = "subscription_preference") val subscriptionPreference: String = "user_selected_sim",
    @Json(name = "created_at") val createdAt: String
)

@JsonClass(generateAdapter = true)
data class PollJobResponse(
    @Json(name = "server_time") val serverTime: String,
    @Json(name = "job") val job: JobDto? = null,
    @Json(name = "retry_after_seconds") val retryAfterSeconds: Long? = 15
)

@JsonClass(generateAdapter = true)
data class StartJobRequest(
    @Json(name = "lease_id") val leaseId: String,
    @Json(name = "event_id") val eventId: String,
    @Json(name = "occurred_at") val occurredAt: String
)

@JsonClass(generateAdapter = true)
data class StartJobResponse(
    @Json(name = "job_id") val jobId: String,
    @Json(name = "status") val status: String,
    @Json(name = "accepted") val accepted: Boolean
)

@JsonClass(generateAdapter = true)
data class JobEventMetadataDto(
    @Json(name = "parts_total") val partsTotal: Int? = null
)

@JsonClass(generateAdapter = true)
data class JobEventRequest(
    @Json(name = "lease_id") val leaseId: String,
    @Json(name = "event_id") val eventId: String,
    @Json(name = "event_type") val eventType: String,
    @Json(name = "occurred_at") val occurredAt: String,
    @Json(name = "result_code") val resultCode: String,
    @Json(name = "failure_code") val failureCode: String? = null,
    @Json(name = "metadata") val metadata: JobEventMetadataDto? = null
)

@JsonClass(generateAdapter = true)
data class JobEventResponse(
    @Json(name = "job_id") val jobId: String,
    @Json(name = "status") val status: String,
    @Json(name = "event_accepted") val eventAccepted: Boolean
)

@JsonClass(generateAdapter = true)
data class ReconcileJobResponse(
    @Json(name = "job_id") val jobId: String,
    @Json(name = "status") val status: String,
    @Json(name = "lease_id") val leaseId: String,
    @Json(name = "last_event_at") val lastEventAt: String? = null
)

@JsonClass(generateAdapter = true)
data class ApiErrorEnvelope(
    @Json(name = "error") val error: ApiErrorBody,
    @Json(name = "request_id") val requestId: String? = null
)

@JsonClass(generateAdapter = true)
data class ApiErrorBody(
    @Json(name = "code") val code: String,
    @Json(name = "message") val message: String
)

@JsonClass(generateAdapter = true)
data class PairQrPayload(
    @Json(name = "base_url") val baseUrl: String,
    @Json(name = "pairing_code") val pairingCode: String
)
