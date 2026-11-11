package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sms_jobs")
data class JobEntity(
    @PrimaryKey
    val jobId: String,
    val leaseId: String,
    val leaseExpiresAt: String,
    val destinationE164: String,
    val body: String,
    val subscriptionPreference: String,
    val createdAt: String,
    val localState: String, // from LocalJobState
    val startEventId: String,
    val startConfirmedAt: Long? = null,
    val partsTotal: Int = 1,
    val selectedSubscriptionId: Int = -1,
    val selectedSimLabel: String = "",
    val isAmbiguous: Boolean = false,
    val ambiguityReason: String? = null,
    val sentToCarrierAt: Long? = null,
    val deliveredAt: Long? = null,
    val finalResultCode: String? = null,
    val finalFailureCode: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
