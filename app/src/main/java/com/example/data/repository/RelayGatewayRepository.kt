package com.example.data.repository

import android.content.Context
import android.os.Build
import com.example.data.local.AppDatabase
import com.example.data.local.JobEntity
import com.example.data.local.OutboundEventEntity
import com.example.data.model.ApiErrorEnvelope
import com.example.data.model.EventUploadState
import com.example.data.model.HeartbeatRequest
import com.example.data.model.HeartbeatResponse
import com.example.data.model.JobEventMetadataDto
import com.example.data.model.JobEventRequest
import com.example.data.model.LocalJobState
import com.example.data.model.PairRequest
import com.example.data.model.PairResponse
import com.example.data.model.ReconcileJobResponse
import com.example.data.model.StartJobRequest
import com.example.data.remote.ApiClientProvider
import com.example.data.security.SecureKeystoreManager
import com.example.service.SmsDispatchManager
import com.example.telemetry.DeviceTelemetryHelper
import com.example.telemetry.SubscriptionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException
import java.util.UUID

class RelayGatewayRepository(
    private val context: Context,
    val keystoreManager: SecureKeystoreManager,
    private val apiClientProvider: ApiClientProvider,
    private val database: AppDatabase,
    private val smsDispatchManager: SmsDispatchManager,
    val subscriptionHelper: SubscriptionHelper,
    val telemetryHelper: DeviceTelemetryHelper
) {
    private val jobDao = database.jobDao()
    private val executionMutex = Mutex()

    private val _lastHeartbeatTime = MutableStateFlow<Long?>(null)
    val lastHeartbeatTime: StateFlow<Long?> = _lastHeartbeatTime.asStateFlow()

    private val _serverSendingEnabled = MutableStateFlow(true)
    val serverSendingEnabled: StateFlow<Boolean> = _serverSendingEnabled.asStateFlow()

    private val _serverDeviceStatus = MutableStateFlow("disconnected")
    val serverDeviceStatus: StateFlow<String> = _serverDeviceStatus.asStateFlow()

    private val _preflightError = MutableStateFlow<String?>(null)
    val preflightError: StateFlow<String?> = _preflightError.asStateFlow()

    private val _isDeviceRevoked = MutableStateFlow(false)
    val isDeviceRevoked: StateFlow<Boolean> = _isDeviceRevoked.asStateFlow()

    // Observable job flows
    val allJobs: Flow<List<JobEntity>> = jobDao.getRecentJobsFlow()
    val pendingEvents: Flow<List<OutboundEventEntity>> = jobDao.getPendingEventsFlow()
    val ambiguousJobs: Flow<List<JobEntity>> = jobDao.getAmbiguousJobsFlow()

    companion object {
        @Volatile
        private var instance: RelayGatewayRepository? = null

        fun getInstance(context: Context): RelayGatewayRepository {
            return instance ?: synchronized(this) {
                val appCtx = context.applicationContext
                val keystore = SecureKeystoreManager(appCtx)
                val apiClient = ApiClientProvider(keystore)
                val db = AppDatabase.getInstance(appCtx)
                val subHelper = SubscriptionHelper(appCtx)
                val dispatchMgr = SmsDispatchManager.getInstance(appCtx)
                val telemetry = DeviceTelemetryHelper(appCtx)

                RelayGatewayRepository(
                    context = appCtx,
                    keystoreManager = keystore,
                    apiClientProvider = apiClient,
                    database = db,
                    smsDispatchManager = dispatchMgr,
                    subscriptionHelper = subHelper,
                    telemetryHelper = telemetry
                ).also { instance = it }
            }
        }
    }

    /**
     * App restart safety hook: ensure any in-flight / acknowledged jobs are NOT re-executed
     */
    suspend fun reconcileOnAppRestart() = withContext(Dispatchers.IO) {
        val inFlight = jobDao.getActiveInFlightJobs()
        for (job in inFlight) {
            jobDao.markJobAmbiguous(
                jobId = job.jobId,
                reason = "App restarted or process died during execution window. Automatic resend strictly prohibited. Reconcile with server."
            )
        }
        // Upload any queued events
        uploadPendingEvents()
    }

    /**
     * Pair device with Laravel backend
     */
    suspend fun pairDevice(
        url: String,
        pairingCode: String,
        deviceName: String
    ): Result<PairResponse> = withContext(Dispatchers.IO) {
        try {
            val validation = keystoreManager.validateUrl(url)
            if (validation is SecureKeystoreManager.UrlValidationResult.Invalid) {
                return@withContext Result.failure(IllegalArgumentException(validation.reason))
            }

            val validUrl = (validation as SecureKeystoreManager.UrlValidationResult.Valid).normalizedUrl
            keystoreManager.baseUrl = validUrl

            val service = apiClientProvider.getApiService(validUrl)
            val telemetry = telemetryHelper.getTelemetry()

            val request = PairRequest(
                pairingCode = pairingCode.trim(),
                deviceName = deviceName.trim().ifEmpty { "Android Gateway (${Build.MODEL})" },
                installationId = keystoreManager.installationId,
                appVersion = telemetry.appVersion,
                androidVersion = telemetry.androidVersion
            )

            val response = service.pairDevice(request)
            if (response.isSuccessful && response.body() != null) {
                val pairBody = response.body()!!
                // Persist credentials in Keystore-backed storage
                keystoreManager.deviceId = pairBody.deviceId
                keystoreManager.deviceToken = pairBody.deviceToken
                keystoreManager.pollIntervalSeconds = pairBody.config.pollIntervalSeconds
                keystoreManager.heartbeatIntervalSeconds = pairBody.config.heartbeatIntervalSeconds
                _isDeviceRevoked.value = false
                _serverDeviceStatus.value = "paired"
                Result.success(pairBody)
            } else {
                val errorMsg = parseErrorMessage(response)
                Result.failure(IOException(errorMsg))
            }
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Send heartbeat to Laravel backend
     */
    suspend fun sendHeartbeat(): Result<HeartbeatResponse> = withContext(Dispatchers.IO) {
        val deviceId = keystoreManager.deviceId ?: return@withContext Result.failure(
            IllegalStateException("Device is not paired")
        )
        val telemetry = telemetryHelper.getTelemetry()
        val hasSmsPerm = hasSmsPermission()
        val simLabel = keystoreManager.selectedSimLabel.ifEmpty { "Default SIM" }

        val request = HeartbeatRequest(
            deviceId = deviceId,
            appVersion = telemetry.appVersion,
            androidVersion = telemetry.androidVersion,
            batteryPct = telemetry.batteryPct,
            smsPermissionGranted = hasSmsPerm,
            gatewayActive = keystoreManager.isGatewayActive,
            simLabel = simLabel,
            networkType = telemetry.networkType
        )

        try {
            val service = apiClientProvider.getApiService()
            val response = service.sendHeartbeat(request)

            if (response.code() == 403) {
                // DEVICE_REVOKED
                handleDeviceRevoked()
                return@withContext Result.failure(SecurityException("Device has been revoked by admin (403)"))
            }

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                _lastHeartbeatTime.value = System.currentTimeMillis()
                _serverSendingEnabled.value = body.sendingEnabled
                _serverDeviceStatus.value = body.deviceStatus
                keystoreManager.pollIntervalSeconds = body.config.pollIntervalSeconds
                keystoreManager.heartbeatIntervalSeconds = body.config.heartbeatIntervalSeconds
                Result.success(body)
            } else {
                Result.failure(IOException(parseErrorMessage(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Poll next job and execute strictly with durable Room ledger and single-execution mutex
     */
    suspend fun pollAndProcessNextJob(): Result<Boolean> = withContext(Dispatchers.IO) {
        // Enforce single execution mutex to prevent concurrent sends
        executionMutex.withLock {
            if (!keystoreManager.isPaired || !keystoreManager.isGatewayActive) {
                return@withContext Result.success(false)
            }

            // Preflight check 1: SMS permission
            if (!hasSmsPermission()) {
                _preflightError.value = "SMS Permission not granted. Cannot process jobs."
                return@withContext Result.failure(SecurityException("SEND_SMS permission is not granted"))
            }

            // Preflight check 2: SIM verification
            val selectedSubId = keystoreManager.selectedSubscriptionId
            if (selectedSubId != -1 && !subscriptionHelper.isSubscriptionActive(selectedSubId)) {
                _preflightError.value = "Selected SIM is absent or changed. Gateway paused to prevent wrong SIM dispatch."
                return@withContext Result.failure(IllegalStateException("Selected SIM is absent or changed"))
            }

            // Preflight check 3: Server sending enabled
            if (!_serverSendingEnabled.value) {
                return@withContext Result.success(false)
            }

            _preflightError.value = null

            val service = apiClientProvider.getApiService()
            val pollResponse = try {
                service.pollNextJob()
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }

            if (pollResponse.code() == 403) {
                handleDeviceRevoked()
                return@withContext Result.failure(SecurityException("Device has been revoked (403)"))
            }

            if (!pollResponse.isSuccessful || pollResponse.body() == null) {
                return@withContext Result.failure(IOException(parseErrorMessage(pollResponse)))
            }

            val pollBody = pollResponse.body()!!
            val jobDto = pollBody.job ?: return@withContext Result.success(false)

            // Step 1: Durably persist the lease and job in Room before acknowledging start!
            val startEventId = UUID.randomUUID().toString()
            val jobEntity = JobEntity(
                jobId = jobDto.id,
                leaseId = jobDto.leaseId,
                leaseExpiresAt = jobDto.leaseExpiresAt,
                destinationE164 = jobDto.destinationE164,
                body = jobDto.body,
                subscriptionPreference = jobDto.subscriptionPreference,
                createdAt = jobDto.createdAt,
                localState = LocalJobState.RECEIVED.name,
                startEventId = startEventId,
                selectedSubscriptionId = selectedSubId,
                selectedSimLabel = keystoreManager.selectedSimLabel,
                updatedAt = System.currentTimeMillis()
            )
            jobDao.insertJob(jobEntity)

            // Step 2: Send /start with persisted stable UUID. Wait for successful 200 response.
            jobDao.updateJobState(jobEntity.jobId, LocalJobState.START_REQUESTED.name)

            val startRequest = StartJobRequest(
                leaseId = jobDto.leaseId,
                eventId = startEventId,
                occurredAt = SmsDispatchManager.currentIsoUtc()
            )

            val startResponse = try {
                service.startJob(jobDto.id, startRequest)
            } catch (e: Exception) {
                // Network error during start: Retain local evidence, do NOT invoke SmsManager!
                jobDao.markJobAmbiguous(
                    jobId = jobDto.id,
                    reason = "Network failure while sending /start: ${e.message}. SMS was NOT invoked."
                )
                return@withContext Result.failure(e)
            }

            if (startResponse.code() == 409) {
                // Stale lease or invalid transition -> do not send!
                jobDao.updateJobState(jobDto.id, LocalJobState.CANCELLED_STALE.name)
                return@withContext Result.failure(IllegalStateException("Server returned 409 STALE_LEASE or INVALID_TRANSITION"))
            }

            if (!startResponse.isSuccessful || startResponse.body() == null) {
                jobDao.markJobAmbiguous(
                    jobId = jobDto.id,
                    reason = "Start rejected with HTTP ${startResponse.code()}: ${parseErrorMessage(startResponse)}"
                )
                return@withContext Result.failure(IOException("Start rejected: ${startResponse.code()}"))
            }

            // Step 3: Persist confirmed start
            val startBody = startResponse.body()!!
            if (startBody.accepted) {
                jobDao.updateJob(
                    jobEntity.copy(
                        localState = LocalJobState.START_CONFIRMED.name,
                        startConfirmedAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                )

                // Step 4: Invoke SmsManager once via SmsDispatchManager
                smsDispatchManager.executeSend(jobEntity)
                return@withContext Result.success(true)
            } else {
                jobDao.updateJobState(jobDto.id, LocalJobState.CANCELLED_STALE.name)
                return@withContext Result.success(false)
            }
        }
    }

    /**
     * Upload queued outbound events with stable event UUIDs
     */
    suspend fun uploadPendingEvents() = withContext(Dispatchers.IO) {
        val pending = jobDao.getPendingEvents()
        if (pending.isEmpty()) return@withContext

        val service = apiClientProvider.getApiService()

        for (event in pending) {
            val request = JobEventRequest(
                leaseId = event.leaseId,
                eventId = event.eventId,
                eventType = event.eventType,
                occurredAt = event.occurredAt,
                resultCode = event.resultCode,
                failureCode = event.failureCode,
                metadata = if (event.partsTotal > 0) JobEventMetadataDto(event.partsTotal) else null
            )

            jobDao.updateEvent(
                event.copy(
                    uploadState = EventUploadState.UPLOADING.name,
                    attemptCount = event.attemptCount + 1,
                    lastAttemptAt = System.currentTimeMillis()
                )
            )

            try {
                val response = service.reportEvent(event.jobId, request)
                if (response.isSuccessful && response.body()?.eventAccepted == true) {
                    jobDao.updateEvent(
                        event.copy(
                            uploadState = EventUploadState.ACKNOWLEDGED.name,
                            lastHttpCode = response.code(),
                            lastErrorMessage = null
                        )
                    )
                } else if (response.code() == 409) {
                    // Conflict / stale lease
                    jobDao.updateEvent(
                        event.copy(
                            uploadState = EventUploadState.REJECTED_CONFLICT.name,
                            lastHttpCode = 409,
                            lastErrorMessage = parseErrorMessage(response)
                        )
                    )
                } else {
                    jobDao.updateEvent(
                        event.copy(
                            uploadState = EventUploadState.PENDING.name,
                            lastHttpCode = response.code(),
                            lastErrorMessage = parseErrorMessage(response)
                        )
                    )
                }
            } catch (e: Exception) {
                jobDao.updateEvent(
                    event.copy(
                        uploadState = EventUploadState.PENDING.name,
                        lastErrorMessage = e.message
                    )
                )
            }
        }
    }

    /**
     * Reconcile job state with server
     */
    suspend fun reconcileJob(jobId: String): Result<ReconcileJobResponse> = withContext(Dispatchers.IO) {
        try {
            val service = apiClientProvider.getApiService()
            val response = service.reconcileJob(jobId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(IOException(parseErrorMessage(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun hasSmsPermission(): Boolean {
        return androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.SEND_SMS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private suspend fun handleDeviceRevoked() {
        _isDeviceRevoked.value = true
        _serverDeviceStatus.value = "revoked"
        keystoreManager.isGatewayActive = false
        keystoreManager.clearCredentials()
    }

    suspend fun unpairAndClear() = withContext(Dispatchers.IO) {
        keystoreManager.clearCredentials()
        _isDeviceRevoked.value = false
        _serverDeviceStatus.value = "disconnected"
        _lastHeartbeatTime.value = null
        jobDao.clearAllData()
    }

    private fun parseErrorMessage(response: Response<*>): String {
        return try {
            val errJson = response.errorBody()?.string()
            if (!errJson.isNullOrBlank()) {
                val parsedMsg = try {
                    val adapter = apiClientProvider.moshi().adapter(ApiErrorEnvelope::class.java)
                    val envelope = adapter.fromJson(errJson)
                    envelope?.error?.let { "${it.code}: ${it.message}" }
                } catch (e: Exception) {
                    null
                }

                parsedMsg ?: run {
                    val codeMatch = Regex("\"code\"\\s*:\\s*\"([^\"]+)\"").find(errJson)?.groupValues?.getOrNull(1)
                    val msgMatch = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(errJson)?.groupValues?.getOrNull(1)
                    if (codeMatch != null && msgMatch != null) {
                        "$codeMatch: $msgMatch"
                    } else if (msgMatch != null) {
                        msgMatch
                    } else {
                        "HTTP ${response.code()}"
                    }
                }
            } else {
                "HTTP ${response.code()} ${response.message()}"
            }
        } catch (e: Exception) {
            "HTTP ${response.code()} ${response.message()}"
        }
    }
}
