package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.JobEntity
import com.example.data.model.LocalJobState
import com.example.data.model.PairQrPayload
import com.example.data.model.ReconcileJobResponse
import com.example.data.repository.RelayGatewayRepository
import com.example.data.security.SecureKeystoreManager
import com.example.service.GatewayForegroundService
import com.example.telemetry.DeviceTelemetry
import com.example.telemetry.SimSubscriptionInfo
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GatewayUiState(
    val isPaired: Boolean = false,
    val isGatewayActive: Boolean = false,
    val deviceId: String? = null,
    val baseUrl: String = SecureKeystoreManager.DEFAULT_BASE_URL,
    val lastHeartbeatTime: Long? = null,
    val serverStatus: String = "disconnected",
    val sendingEnabled: Boolean = true,
    val preflightError: String? = null,
    val isRevoked: Boolean = false,
    val selectedSubId: Int = -1,
    val selectedSimLabel: String = "",
    val availableSubscriptions: List<SimSubscriptionInfo> = emptyList(),
    val telemetry: DeviceTelemetry? = null,
    val hasSmsPermission: Boolean = false,
    val hasPhoneStatePermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val manualForegroundFallback: Boolean = false
)

data class PairingUiState(
    val urlInput: String = SecureKeystoreManager.DEFAULT_BASE_URL,
    val pairingCodeInput: String = "",
    val deviceNameInput: String = "",
    val detectedOrigin: String? = null,
    val urlError: String? = null,
    val isPairing: Boolean = false,
    val errorMessage: String? = null,
    val ttlSecondsRemaining: Int = 300,
    val attemptCount: Int = 0
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RelayGatewayRepository.getInstance(application)
    private val moshi = Moshi.Builder().build()

    private val _uiState = MutableStateFlow(GatewayUiState())
    val uiState: StateFlow<GatewayUiState> = _uiState.asStateFlow()

    private val _pairingState = MutableStateFlow(PairingUiState())
    val pairingState: StateFlow<PairingUiState> = _pairingState.asStateFlow()

    private val _reconcileResult = MutableStateFlow<Pair<String, ReconcileJobResponse?>?>(null)
    val reconcileResult: StateFlow<Pair<String, ReconcileJobResponse?>?> = _reconcileResult.asStateFlow()

    val recentJobs: StateFlow<List<JobEntity>> = repository.allJobs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val ambiguousJobs: StateFlow<List<JobEntity>> = repository.ambiguousJobs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshState()
        viewModelScope.launch {
            repository.reconcileOnAppRestart()
        }
        viewModelScope.launch {
            repository.lastHeartbeatTime.collect { time ->
                _uiState.value = _uiState.value.copy(lastHeartbeatTime = time)
            }
        }
        viewModelScope.launch {
            repository.serverSendingEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(sendingEnabled = enabled)
            }
        }
        viewModelScope.launch {
            repository.serverDeviceStatus.collect { status ->
                _uiState.value = _uiState.value.copy(serverStatus = status)
            }
        }
        viewModelScope.launch {
            repository.preflightError.collect { err ->
                _uiState.value = _uiState.value.copy(preflightError = err)
            }
        }
        viewModelScope.launch {
            repository.isDeviceRevoked.collect { revoked ->
                _uiState.value = _uiState.value.copy(isRevoked = revoked)
                if (revoked) {
                    refreshState()
                }
            }
        }
    }

    fun refreshState() {
        val keystore = repository.keystoreManager
        val subs = repository.subscriptionHelper.getActiveSubscriptions()
        val hasSms = repository.hasSmsPermission()
        val hasPhoneState = repository.subscriptionHelper.hasPhoneStatePermission()
        val hasNotification = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                getApplication(),
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        // Auto-select first active subscription if none explicitly selected
        var chosenSubId = keystore.selectedSubscriptionId
        var chosenLabel = keystore.selectedSimLabel

        if (chosenSubId == -1 && subs.isNotEmpty()) {
            chosenSubId = subs[0].subscriptionId
            chosenLabel = subs[0].userLabel
            keystore.selectedSubscriptionId = chosenSubId
            keystore.selectedSimLabel = chosenLabel
        }

        _uiState.value = _uiState.value.copy(
            isPaired = keystore.isPaired,
            isGatewayActive = keystore.isGatewayActive,
            deviceId = keystore.deviceId,
            baseUrl = keystore.baseUrl,
            selectedSubId = chosenSubId,
            selectedSimLabel = chosenLabel,
            availableSubscriptions = subs,
            telemetry = repository.telemetryHelper.getTelemetry(),
            hasSmsPermission = hasSms,
            hasPhoneStatePermission = hasPhoneState,
            hasNotificationPermission = hasNotification,
            manualForegroundFallback = keystore.manualForegroundFallbackEnabled
        )

        _pairingState.value = _pairingState.value.copy(
            urlInput = keystore.baseUrl
        )
    }

    fun setGatewayActive(active: Boolean) {
        repository.keystoreManager.isGatewayActive = active
        _uiState.value = _uiState.value.copy(isGatewayActive = active)
        if (active) {
            if (!_uiState.value.manualForegroundFallback) {
                GatewayForegroundService.startService(getApplication())
            }
            viewModelScope.launch {
                repository.sendHeartbeat()
                repository.pollAndProcessNextJob()
            }
        } else {
            GatewayForegroundService.stopService(getApplication())
        }
    }

    fun updatePairingUrl(url: String) {
        val validation = repository.keystoreManager.validateUrl(url)
        val origin = if (validation is SecureKeystoreManager.UrlValidationResult.Valid) {
            validation.origin
        } else null
        val error = if (validation is SecureKeystoreManager.UrlValidationResult.Invalid) {
            validation.reason
        } else null

        _pairingState.value = _pairingState.value.copy(
            urlInput = url,
            detectedOrigin = origin,
            urlError = error,
            errorMessage = null
        )
    }

    fun updatePairingCode(code: String) {
        _pairingState.value = _pairingState.value.copy(
            pairingCodeInput = code.filter { it.isDigit() }.take(8),
            errorMessage = null
        )
    }

    fun updateDeviceName(name: String) {
        _pairingState.value = _pairingState.value.copy(deviceNameInput = name)
    }

    fun parseQrPayload(json: String) {
        try {
            val adapter = moshi.adapter(PairQrPayload::class.java)
            val payload = adapter.fromJson(json)
            if (payload != null) {
                updatePairingUrl(payload.baseUrl)
                updatePairingCode(payload.pairingCode)
            }
        } catch (e: Exception) {
            _pairingState.value = _pairingState.value.copy(
                errorMessage = "Invalid QR code payload format. Expected JSON with base_url and pairing_code."
            )
        }
    }

    fun submitPairing(onSuccess: () -> Unit) {
        val currentPairing = _pairingState.value
        val url = currentPairing.urlInput.trim()
        val code = currentPairing.pairingCodeInput.trim()

        if (code.length != 8) {
            _pairingState.value = currentPairing.copy(errorMessage = "Pairing code must be exactly 8 digits.")
            return
        }

        _pairingState.value = currentPairing.copy(
            isPairing = true,
            errorMessage = null,
            attemptCount = currentPairing.attemptCount + 1
        )

        viewModelScope.launch {
            val result = repository.pairDevice(
                url = url,
                pairingCode = code,
                deviceName = currentPairing.deviceNameInput
            )
            result.onSuccess {
                _pairingState.value = _pairingState.value.copy(isPairing = false)
                refreshState()
                onSuccess()
            }.onFailure { e ->
                _pairingState.value = _pairingState.value.copy(
                    isPairing = false,
                    errorMessage = e.message ?: "Pairing failed. Verify pairing code and URL."
                )
            }
        }
    }

    fun selectSubscription(sub: SimSubscriptionInfo) {
        repository.keystoreManager.selectedSubscriptionId = sub.subscriptionId
        repository.keystoreManager.selectedSimLabel = sub.userLabel
        _uiState.value = _uiState.value.copy(
            selectedSubId = sub.subscriptionId,
            selectedSimLabel = sub.userLabel
        )
    }

    fun updateServerUrl(newUrl: String): Boolean {
        val validation = repository.keystoreManager.validateUrl(newUrl)
        return if (validation is SecureKeystoreManager.UrlValidationResult.Valid) {
            repository.keystoreManager.baseUrl = validation.normalizedUrl
            _uiState.value = _uiState.value.copy(baseUrl = validation.normalizedUrl)
            true
        } else {
            false
        }
    }

    fun toggleManualForegroundFallback(enabled: Boolean) {
        repository.keystoreManager.manualForegroundFallbackEnabled = enabled
        _uiState.value = _uiState.value.copy(manualForegroundFallback = enabled)
        if (enabled && _uiState.value.isGatewayActive) {
            GatewayForegroundService.stopService(getApplication())
        } else if (!enabled && _uiState.value.isGatewayActive) {
            GatewayForegroundService.startService(getApplication())
        }
    }

    fun triggerHeartbeat() {
        viewModelScope.launch {
            repository.sendHeartbeat()
        }
    }

    fun triggerPoll() {
        viewModelScope.launch {
            repository.pollAndProcessNextJob()
        }
    }

    fun reconcileJob(jobId: String) {
        viewModelScope.launch {
            val res = repository.reconcileJob(jobId)
            res.onSuccess { response ->
                _reconcileResult.value = Pair(jobId, response)
            }.onFailure {
                _reconcileResult.value = Pair(jobId, null)
            }
        }
    }

    fun clearReconcileDialog() {
        _reconcileResult.value = null
    }

    fun unpairDevice() {
        viewModelScope.launch {
            GatewayForegroundService.stopService(getApplication())
            repository.unpairAndClear()
            refreshState()
        }
    }
}
