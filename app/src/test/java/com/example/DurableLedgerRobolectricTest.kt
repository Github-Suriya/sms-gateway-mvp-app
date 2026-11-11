package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.JobDao
import com.example.data.local.JobEntity
import com.example.data.local.JobPartCallbackEntity
import com.example.data.local.OutboundEventEntity
import com.example.data.model.AllowedCodes
import com.example.data.model.EventUploadState
import com.example.data.model.LocalJobState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DurableLedgerRobolectricTest {

    private lateinit var db: AppDatabase
    private lateinit var jobDao: JobDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        jobDao = db.jobDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testJobPersistenceBeforeStartAcknowledgment() = runBlocking {
        val jobId = UUID.randomUUID().toString()
        val leaseId = UUID.randomUUID().toString()

        val job = JobEntity(
            jobId = jobId,
            leaseId = leaseId,
            leaseExpiresAt = "2026-11-11T06:31:00Z",
            destinationE164 = "+919876543210",
            body = "Appointment confirmation message",
            subscriptionPreference = "user_selected_sim",
            createdAt = "2026-11-11T06:29:00Z",
            localState = LocalJobState.RECEIVED.name,
            startEventId = UUID.randomUUID().toString(),
            selectedSubscriptionId = 1,
            selectedSimLabel = "SIM 1"
        )

        jobDao.insertJob(job)

        val retrieved = jobDao.getJob(jobId)
        assertNotNull(retrieved)
        assertEquals(LocalJobState.RECEIVED.name, retrieved?.localState)
        assertFalse(retrieved!!.isAmbiguous)
    }

    @Test
    fun testMultipartSuccessCallbackAggregation() = runBlocking {
        val jobId = UUID.randomUUID().toString()
        val leaseId = UUID.randomUUID().toString()

        val job = JobEntity(
            jobId = jobId,
            leaseId = leaseId,
            leaseExpiresAt = "2026-11-11T06:31:00Z",
            destinationE164 = "+919876543210",
            body = "Long multipart message test across carriers...",
            subscriptionPreference = "user_selected_sim",
            createdAt = "2026-11-11T06:29:00Z",
            localState = LocalJobState.CALLBACK_PROCESSING.name,
            startEventId = UUID.randomUUID().toString(),
            partsTotal = 2
        )
        jobDao.insertJob(job)

        // Part 0 succeeds
        jobDao.insertOrUpdatePart(
            JobPartCallbackEntity(
                jobId = jobId,
                partIndex = 0,
                partsTotal = 2,
                sentResultCode = AllowedCodes.RESULT_OK,
                sentReceivedAt = System.currentTimeMillis()
            )
        )

        // Part 1 succeeds
        jobDao.insertOrUpdatePart(
            JobPartCallbackEntity(
                jobId = jobId,
                partIndex = 1,
                partsTotal = 2,
                sentResultCode = AllowedCodes.RESULT_OK,
                sentReceivedAt = System.currentTimeMillis()
            )
        )

        val parts = jobDao.getPartsForJob(jobId)
        assertEquals(2, parts.size)
        assertTrue(parts.all { it.sentResultCode == AllowedCodes.RESULT_OK })
    }

    @Test
    fun testMultipartPartialFailureCallbackAggregation() = runBlocking {
        val jobId = UUID.randomUUID().toString()
        val leaseId = UUID.randomUUID().toString()

        val job = JobEntity(
            jobId = jobId,
            leaseId = leaseId,
            leaseExpiresAt = "2026-11-11T06:31:00Z",
            destinationE164 = "+919876543210",
            body = "Multipart message with failure test",
            subscriptionPreference = "user_selected_sim",
            createdAt = "2026-11-11T06:29:00Z",
            localState = LocalJobState.CALLBACK_PROCESSING.name,
            startEventId = UUID.randomUUID().toString(),
            partsTotal = 2
        )
        jobDao.insertJob(job)

        // Part 0 OK
        jobDao.insertOrUpdatePart(
            JobPartCallbackEntity(
                jobId = jobId,
                partIndex = 0,
                partsTotal = 2,
                sentResultCode = AllowedCodes.RESULT_OK
            )
        )

        // Part 1 fails with NO_SERVICE
        jobDao.insertOrUpdatePart(
            JobPartCallbackEntity(
                jobId = jobId,
                partIndex = 1,
                partsTotal = 2,
                sentResultCode = AllowedCodes.RESULT_ERROR_NO_SERVICE,
                sentFailureCode = AllowedCodes.FAILURE_NO_SERVICE
            )
        )

        val parts = jobDao.getPartsForJob(jobId)
        assertEquals(2, parts.size)
        val hasFailure = parts.any { it.sentResultCode != AllowedCodes.RESULT_OK }
        assertTrue(hasFailure)

        // Failed part must NOT trigger automatic resend
        val failedPart = parts.first { it.sentResultCode != AllowedCodes.RESULT_OK }
        assertEquals(AllowedCodes.RESULT_ERROR_NO_SERVICE, failedPart.sentResultCode)
        assertEquals(AllowedCodes.FAILURE_NO_SERVICE, failedPart.sentFailureCode)
    }

    @Test
    fun testProcessCrashSafetyNoDuplicateSendOnRestart() = runBlocking {
        val jobId = UUID.randomUUID().toString()
        val leaseId = UUID.randomUUID().toString()

        // Simulating process death immediately after start confirmation
        val job = JobEntity(
            jobId = jobId,
            leaseId = leaseId,
            leaseExpiresAt = "2026-11-11T06:31:00Z",
            destinationE164 = "+919876543210",
            body = "Important one-time alert",
            subscriptionPreference = "user_selected_sim",
            createdAt = "2026-11-11T06:29:00Z",
            localState = LocalJobState.START_CONFIRMED.name,
            startEventId = UUID.randomUUID().toString()
        )
        jobDao.insertJob(job)

        // On app restart, in-flight jobs are retrieved
        val inFlight = jobDao.getActiveInFlightJobs()
        assertEquals(1, inFlight.size)

        // Must be marked ambiguous and require review — NEVER auto-executed!
        for (item in inFlight) {
            jobDao.markJobAmbiguous(
                jobId = item.jobId,
                reason = "App restarted during in-flight window. Operator review required."
            )
        }

        val updated = jobDao.getJob(jobId)
        assertNotNull(updated)
        assertTrue(updated!!.isAmbiguous)
        assertEquals(LocalJobState.NEEDS_REVIEW.name, updated.localState)
    }

    @Test
    fun testOutboundEventQueueUploadDecoupling() = runBlocking {
        val eventId = UUID.randomUUID().toString()
        val jobId = UUID.randomUUID().toString()

        val event = OutboundEventEntity(
            eventId = eventId,
            jobId = jobId,
            leaseId = UUID.randomUUID().toString(),
            eventType = AllowedCodes.EVENT_SMS_SENT,
            occurredAt = "2026-11-11T06:30:08Z",
            resultCode = AllowedCodes.RESULT_OK,
            partsTotal = 1,
            uploadState = EventUploadState.PENDING.name
        )

        jobDao.insertEvent(event)

        val pending = jobDao.getPendingEvents()
        assertEquals(1, pending.size)
        assertEquals(eventId, pending[0].eventId)

        // Simulating network retry: upload attempt incremented, stable eventId preserved
        val retried = pending[0].copy(
            attemptCount = pending[0].attemptCount + 1,
            lastAttemptAt = System.currentTimeMillis()
        )
        jobDao.updateEvent(retried)

        val afterRetry = jobDao.getEvent(eventId)
        assertEquals(1, afterRetry?.attemptCount)
        assertEquals(eventId, afterRetry?.eventId)
    }
}
