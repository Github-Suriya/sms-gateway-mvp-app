package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outbound_events")
data class OutboundEventEntity(
    @PrimaryKey
    val eventId: String, // Stable UUID
    val jobId: String,
    val leaseId: String,
    val eventType: String, // sms_sent, sms_send_failed, sms_delivered, sms_delivery_failed
    val occurredAt: String, // ISO8601 UTC timestamp ending in Z
    val resultCode: String,
    val failureCode: String? = null,
    val partsTotal: Int = 1,
    val uploadState: String, // from EventUploadState
    val attemptCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val lastHttpCode: Int? = null,
    val lastErrorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
