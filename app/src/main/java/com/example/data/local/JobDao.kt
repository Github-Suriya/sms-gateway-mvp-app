package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: JobEntity)

    @Update
    suspend fun updateJob(job: JobEntity)

    @Query("SELECT * FROM sms_jobs WHERE jobId = :jobId")
    suspend fun getJob(jobId: String): JobEntity?

    @Query("SELECT * FROM sms_jobs WHERE jobId = :jobId")
    fun getJobFlow(jobId: String): Flow<JobEntity?>

    @Query("SELECT * FROM sms_jobs ORDER BY updatedAt DESC")
    fun getAllJobsFlow(): Flow<List<JobEntity>>

    @Query("SELECT * FROM sms_jobs ORDER BY updatedAt DESC LIMIT :limit")
    fun getRecentJobsFlow(limit: Int = 100): Flow<List<JobEntity>>

    @Query("SELECT * FROM sms_jobs WHERE localState IN ('RECEIVED', 'START_REQUESTED', 'START_CONFIRMED', 'SEND_INTENT_PERSISTED', 'SEND_INVOKED', 'CALLBACK_PROCESSING')")
    suspend fun getActiveInFlightJobs(): List<JobEntity>

    @Query("SELECT * FROM sms_jobs WHERE isAmbiguous = 1")
    suspend fun getAmbiguousJobs(): List<JobEntity>

    @Query("SELECT * FROM sms_jobs WHERE isAmbiguous = 1")
    fun getAmbiguousJobsFlow(): Flow<List<JobEntity>>

    @Query("UPDATE sms_jobs SET isAmbiguous = 1, localState = 'NEEDS_REVIEW', ambiguityReason = :reason, updatedAt = :updatedAt WHERE jobId = :jobId")
    suspend fun markJobAmbiguous(jobId: String, reason: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE sms_jobs SET localState = :newState, updatedAt = :updatedAt WHERE jobId = :jobId")
    suspend fun updateJobState(jobId: String, newState: String, updatedAt: Long = System.currentTimeMillis())

    // Parts
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParts(parts: List<JobPartCallbackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePart(part: JobPartCallbackEntity)

    @Query("SELECT * FROM job_part_callbacks WHERE jobId = :jobId ORDER BY partIndex ASC")
    suspend fun getPartsForJob(jobId: String): List<JobPartCallbackEntity>

    @Query("SELECT * FROM job_part_callbacks WHERE jobId = :jobId ORDER BY partIndex ASC")
    fun getPartsForJobFlow(jobId: String): Flow<List<JobPartCallbackEntity>>

    @Query("SELECT * FROM job_part_callbacks WHERE jobId = :jobId AND partIndex = :partIndex")
    suspend fun getPart(jobId: String, partIndex: Int): JobPartCallbackEntity?

    // Outbound events
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: OutboundEventEntity)

    @Update
    suspend fun updateEvent(event: OutboundEventEntity)

    @Query("SELECT * FROM outbound_events WHERE uploadState = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingEvents(): List<OutboundEventEntity>

    @Query("SELECT * FROM outbound_events WHERE uploadState = 'PENDING' ORDER BY createdAt ASC")
    fun getPendingEventsFlow(): Flow<List<OutboundEventEntity>>

    @Query("SELECT * FROM outbound_events WHERE eventId = :eventId")
    suspend fun getEvent(eventId: String): OutboundEventEntity?

    @Query("SELECT * FROM outbound_events WHERE jobId = :jobId ORDER BY createdAt ASC")
    suspend fun getEventsForJob(jobId: String): List<OutboundEventEntity>

    @Query("SELECT * FROM outbound_events WHERE jobId = :jobId ORDER BY createdAt ASC")
    fun getEventsForJobFlow(jobId: String): Flow<List<OutboundEventEntity>>

    // Maintenance / cleanup on unpair
    @Query("DELETE FROM sms_jobs")
    suspend fun deleteAllJobs()

    @Query("DELETE FROM job_part_callbacks")
    suspend fun deleteAllParts()

    @Query("DELETE FROM outbound_events")
    suspend fun deleteAllEvents()

    @Transaction
    suspend fun clearAllData() {
        deleteAllParts()
        deleteAllEvents()
        deleteAllJobs()
    }
}
