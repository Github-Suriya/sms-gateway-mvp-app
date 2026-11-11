package com.example.service

import com.example.data.repository.RelayGatewayRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Random

class GatewayLoopCoordinator(
    private val repository: RelayGatewayRepository,
    private val scope: CoroutineScope
) {
    private var heartbeatJob: Job? = null
    private var pollJob: Job? = null
    private val random = Random()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var retryAfterDelayMs: Long = 0
    private var consecutiveErrors = 0

    fun start() {
        if (_isRunning.value) return
        _isRunning.value = true
        consecutiveErrors = 0
        _lastError.value = null

        heartbeatJob = scope.launch {
            runHeartbeatLoop()
        }

        pollJob = scope.launch {
            runPollLoop()
        }
    }

    fun stop() {
        _isRunning.value = false
        heartbeatJob?.cancel()
        pollJob?.cancel()
        heartbeatJob = null
        pollJob = null
    }

    private suspend fun runHeartbeatLoop() {
        while (scope.isActive && _isRunning.value) {
            if (repository.keystoreManager.isPaired) {
                val result = repository.sendHeartbeat()
                result.onSuccess {
                    consecutiveErrors = 0
                    _lastError.value = null
                }.onFailure { e ->
                    if (e is SecurityException && repository.isDeviceRevoked.value) {
                        stop()
                        return
                    }
                    _lastError.value = "Heartbeat error: ${e.message}"
                }
            }

            val intervalSec = repository.keystoreManager.heartbeatIntervalSeconds.coerceAtLeast(10L)
            delay(intervalSec * 1000L)
        }
    }

    private suspend fun runPollLoop() {
        while (scope.isActive && _isRunning.value) {
            if (!repository.keystoreManager.isGatewayActive) {
                delay(1000L)
                continue
            }

            if (retryAfterDelayMs > 0) {
                val wait = retryAfterDelayMs
                retryAfterDelayMs = 0
                delay(wait)
            }

            val pollResult = repository.pollAndProcessNextJob()
            pollResult.onSuccess { hadJob ->
                consecutiveErrors = 0
                _lastError.value = null
                // Trigger upload for any callbacks or events
                repository.uploadPendingEvents()
            }.onFailure { e ->
                if (e is CancellationException) throw e
                consecutiveErrors++
                _lastError.value = "Poll error: ${e.message}"

                // Check for 429 Retry-After or apply exponential backoff with jitter
                val backoffBase = (5L * (1 shl (consecutiveErrors - 1).coerceAtMost(4))) * 1000L
                val jitter = random.nextInt(2000).toLong()
                delay(backoffBase + jitter)
                return@onFailure
            }

            // Normal poll delay
            val pollSec = repository.keystoreManager.pollIntervalSeconds.coerceAtLeast(5L)
            delay(pollSec * 1000L)
        }
    }

    fun applyRetryAfter(seconds: Long) {
        retryAfterDelayMs = seconds.coerceAtLeast(1L) * 1000L
    }
}
