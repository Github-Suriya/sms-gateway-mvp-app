package com.example.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import com.example.data.local.AppDatabase
import com.example.data.local.JobEntity
import com.example.data.local.JobPartCallbackEntity
import com.example.data.local.OutboundEventEntity
import com.example.data.model.AllowedCodes
import com.example.data.model.EventUploadState
import com.example.data.model.LocalJobState
import com.example.telemetry.SubscriptionHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.math.abs

class SmsDispatchManager(
    private val context: Context,
    private val database: AppDatabase,
    private val subscriptionHelper: SubscriptionHelper
) {

    private val jobDao = database.jobDao()

    companion object {
        @Volatile
        private var instance: SmsDispatchManager? = null

        fun getInstance(context: Context): SmsDispatchManager {
            return instance ?: synchronized(this) {
                instance ?: SmsDispatchManager(
                    context.applicationContext,
                    AppDatabase.getInstance(context),
                    SubscriptionHelper(context.applicationContext)
                ).also { instance = it }
            }
        }

        fun currentIsoUtc(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.format(Date())
        }
    }

    /**
     * Segments and transmits an SMS using SmsManager once after start is acknowledged.
     * Guaranteed single invocation with per-part tracking.
     */
    suspend fun executeSend(job: JobEntity) {
        val jobId = job.jobId
        val leaseId = job.leaseId

        // Persist confirmed execution intent marker before invoking SmsManager
        jobDao.updateJobState(
            jobId = jobId,
            newState = LocalJobState.SEND_INTENT_PERSISTED.name,
            updatedAt = System.currentTimeMillis()
        )

        val smsManager = subscriptionHelper.getSmsManagerForSubscription(job.selectedSubscriptionId)
        val text = job.body
        val destination = job.destinationE164

        val parts: ArrayList<String> = try {
            smsManager.divideMessage(text)
        } catch (e: Exception) {
            arrayListOf(text)
        }
        val partsTotal = parts.size.coerceAtLeast(1)

        // Initialize part records in Room
        val partEntities = (0 until partsTotal).map { index ->
            JobPartCallbackEntity(
                jobId = jobId,
                partIndex = index,
                partsTotal = partsTotal
            )
        }
        jobDao.insertParts(partEntities)

        // Update job with total parts and transition to SEND_INVOKED
        val updatedJob = job.copy(
            partsTotal = partsTotal,
            localState = LocalJobState.SEND_INVOKED.name,
            updatedAt = System.currentTimeMillis()
        )
        jobDao.updateJob(updatedJob)

        val sentIntents = ArrayList<PendingIntent>(partsTotal)
        val deliveryIntents = ArrayList<PendingIntent>(partsTotal)

        val baseRequestCode = abs(jobId.hashCode())

        for (i in 0 until partsTotal) {
            val sentIntent = Intent(context, SentCallbackReceiver::class.java).apply {
                action = SentCallbackReceiver.ACTION
                `package` = context.packageName
                putExtra(SentCallbackReceiver.EXTRA_JOB_ID, jobId)
                putExtra(SentCallbackReceiver.EXTRA_LEASE_ID, leaseId)
                putExtra(SentCallbackReceiver.EXTRA_PART_INDEX, i)
                putExtra(SentCallbackReceiver.EXTRA_PARTS_TOTAL, partsTotal)
            }
            val sentPi = PendingIntent.getBroadcast(
                context,
                baseRequestCode + i * 2,
                sentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            sentIntents.add(sentPi)

            val deliveryIntent = Intent(context, DeliveryCallbackReceiver::class.java).apply {
                action = DeliveryCallbackReceiver.ACTION
                `package` = context.packageName
                putExtra(DeliveryCallbackReceiver.EXTRA_JOB_ID, jobId)
                putExtra(DeliveryCallbackReceiver.EXTRA_LEASE_ID, leaseId)
                putExtra(DeliveryCallbackReceiver.EXTRA_PART_INDEX, i)
                putExtra(DeliveryCallbackReceiver.EXTRA_PARTS_TOTAL, partsTotal)
            }
            val deliveryPi = PendingIntent.getBroadcast(
                context,
                baseRequestCode + i * 2 + 1,
                deliveryIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            deliveryIntents.add(deliveryPi)
        }

        try {
            if (partsTotal == 1) {
                smsManager.sendTextMessage(
                    destination,
                    null,
                    parts[0],
                    sentIntents[0],
                    deliveryIntents[0]
                )
            } else {
                smsManager.sendMultipartTextMessage(
                    destination,
                    null,
                    parts,
                    sentIntents,
                    deliveryIntents
                )
            }
            // Mark job waiting for callbacks
            jobDao.updateJobState(
                jobId = jobId,
                newState = LocalJobState.CALLBACK_PROCESSING.name,
                updatedAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            // An exception during/after invocation without an authoritative callback indicates uncertainty!
            // Never fabricate a sent failure or auto-resend.
            jobDao.markJobAmbiguous(
                jobId = jobId,
                reason = "SmsManager invocation threw exception after start acknowledgment: ${e.javaClass.simpleName}"
            )
        }
    }

    /**
     * Process sent callback from BroadcastReceiver
     */
    suspend fun onSentCallback(
        jobId: String,
        leaseId: String,
        partIndex: Int,
        partsTotal: Int,
        rawResultCode: Int
    ) {
        val job = jobDao.getJob(jobId) ?: return
        if (job.leaseId != leaseId) {
            // Stale callback from an older lease
            return
        }

        val mappedCode = AllowedCodes.mapAndroidSentResultCode(rawResultCode)
        val now = System.currentTimeMillis()

        // Update part record
        val existingPart = jobDao.getPart(jobId, partIndex)
        val updatedPart = (existingPart ?: JobPartCallbackEntity(jobId, partIndex, partsTotal)).copy(
            sentResultCode = mappedCode,
            sentFailureCode = AllowedCodes.inferFailureCode(mappedCode),
            sentReceivedAt = now
        )
        jobDao.insertOrUpdatePart(updatedPart)

        // Evaluate all parts
        val allParts = jobDao.getPartsForJob(jobId)
        val totalExpected = if (job.partsTotal > 0) job.partsTotal else partsTotal

        if (allParts.size >= totalExpected && allParts.all { it.sentResultCode != null }) {
            // All parts have arrived
            val allSuccess = allParts.all { it.sentResultCode == AllowedCodes.RESULT_OK }
            if (allSuccess) {
                jobDao.updateJob(
                    job.copy(
                        localState = LocalJobState.SENT_TO_CARRIER.name,
                        sentToCarrierAt = now,
                        finalResultCode = AllowedCodes.RESULT_OK,
                        updatedAt = now
                    )
                )

                val event = OutboundEventEntity(
                    eventId = UUID.randomUUID().toString(),
                    jobId = jobId,
                    leaseId = leaseId,
                    eventType = AllowedCodes.EVENT_SMS_SENT,
                    occurredAt = currentIsoUtc(),
                    resultCode = AllowedCodes.RESULT_OK,
                    failureCode = null,
                    partsTotal = totalExpected,
                    uploadState = EventUploadState.PENDING.name,
                    createdAt = now
                )
                jobDao.insertEvent(event)
            } else {
                // At least one part failed
                val firstFailed = allParts.firstOrNull { it.sentResultCode != AllowedCodes.RESULT_OK }
                val failureResultCode = firstFailed?.sentResultCode ?: AllowedCodes.RESULT_ERROR_GENERIC_FAILURE
                val failureCode = AllowedCodes.inferFailureCode(failureResultCode)
                    ?: AllowedCodes.FAILURE_GENERIC_FAILURE

                jobDao.updateJob(
                    job.copy(
                        localState = LocalJobState.SEND_FAILED.name,
                        finalResultCode = failureResultCode,
                        finalFailureCode = failureCode,
                        updatedAt = now
                    )
                )

                val event = OutboundEventEntity(
                    eventId = UUID.randomUUID().toString(),
                    jobId = jobId,
                    leaseId = leaseId,
                    eventType = AllowedCodes.EVENT_SMS_SEND_FAILED,
                    occurredAt = currentIsoUtc(),
                    resultCode = failureResultCode,
                    failureCode = failureCode,
                    partsTotal = totalExpected,
                    uploadState = EventUploadState.PENDING.name,
                    createdAt = now
                )
                jobDao.insertEvent(event)
            }
        }
    }

    /**
     * Process delivery callback from BroadcastReceiver
     */
    suspend fun onDeliveryCallback(
        jobId: String,
        leaseId: String,
        partIndex: Int,
        partsTotal: Int,
        rawResultCode: Int
    ) {
        val job = jobDao.getJob(jobId) ?: return
        if (job.leaseId != leaseId) return

        val mappedCode = AllowedCodes.mapAndroidSentResultCode(rawResultCode)
        val now = System.currentTimeMillis()

        val existingPart = jobDao.getPart(jobId, partIndex)
        val updatedPart = (existingPart ?: JobPartCallbackEntity(jobId, partIndex, partsTotal)).copy(
            deliveryResultCode = mappedCode,
            deliveryReceivedAt = now
        )
        jobDao.insertOrUpdatePart(updatedPart)

        val allParts = jobDao.getPartsForJob(jobId)
        val totalExpected = if (job.partsTotal > 0) job.partsTotal else partsTotal

        if (mappedCode == AllowedCodes.RESULT_OK) {
            // Check if ALL parts are delivered
            if (allParts.size >= totalExpected && allParts.all { it.deliveryResultCode == AllowedCodes.RESULT_OK }) {
                jobDao.updateJob(
                    job.copy(
                        localState = LocalJobState.DELIVERED.name,
                        deliveredAt = now,
                        updatedAt = now
                    )
                )

                val event = OutboundEventEntity(
                    eventId = UUID.randomUUID().toString(),
                    jobId = jobId,
                    leaseId = leaseId,
                    eventType = AllowedCodes.EVENT_SMS_DELIVERED,
                    occurredAt = currentIsoUtc(),
                    resultCode = AllowedCodes.RESULT_OK,
                    failureCode = null,
                    partsTotal = totalExpected,
                    uploadState = EventUploadState.PENDING.name,
                    createdAt = now
                )
                jobDao.insertEvent(event)
            }
        } else {
            // Delivery failed callback
            jobDao.updateJob(
                job.copy(
                    localState = LocalJobState.DELIVERY_FAILED.name,
                    updatedAt = now
                )
            )

            val event = OutboundEventEntity(
                eventId = UUID.randomUUID().toString(),
                jobId = jobId,
                leaseId = leaseId,
                eventType = AllowedCodes.EVENT_SMS_DELIVERY_FAILED,
                occurredAt = currentIsoUtc(),
                resultCode = mappedCode,
                failureCode = AllowedCodes.FAILURE_DELIVERY_FAILED,
                partsTotal = totalExpected,
                uploadState = EventUploadState.PENDING.name,
                createdAt = now
            )
            jobDao.insertEvent(event)
        }
    }
}
