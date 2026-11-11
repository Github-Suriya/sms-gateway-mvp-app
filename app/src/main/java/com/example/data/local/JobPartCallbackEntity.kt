package com.example.data.local

import androidx.room.Entity

@Entity(
    tableName = "job_part_callbacks",
    primaryKeys = ["jobId", "partIndex"]
)
data class JobPartCallbackEntity(
    val jobId: String,
    val partIndex: Int,
    val partsTotal: Int,
    val sentResultCode: String? = null,
    val sentFailureCode: String? = null,
    val sentReceivedAt: Long? = null,
    val deliveryResultCode: String? = null,
    val deliveryReceivedAt: Long? = null
)
