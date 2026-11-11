package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.repository.RelayGatewayRepository
import com.example.telemetry.DeviceTelemetryHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class GatewayForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var repository: RelayGatewayRepository
    private lateinit var loopCoordinator: GatewayLoopCoordinator
    private lateinit var telemetryHelper: DeviceTelemetryHelper

    override fun onCreate() {
        super.onCreate()
        repository = RelayGatewayRepository.getInstance(this)
        telemetryHelper = DeviceTelemetryHelper(this)
        loopCoordinator = GatewayLoopCoordinator(repository, serviceScope)

        createNotificationChannel()
        startInForeground()

        // Observe repository state to update notification
        serviceScope.launch {
            combine(
                repository.serverDeviceStatus,
                repository.serverSendingEnabled,
                repository.lastHeartbeatTime,
                repository.preflightError
            ) { status, sendingEnabled, lastHeartbeat, preflightErr ->
                buildNotification(status, sendingEnabled, lastHeartbeat, preflightErr)
            }.collect { notification ->
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopGateway()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                repository.keystoreManager.isGatewayActive = true
                loopCoordinator.start()
            }
            else -> {
                if (repository.keystoreManager.isGatewayActive) {
                    loopCoordinator.start()
                }
            }
        }
        return START_STICKY
    }

    private fun startInForeground() {
        try {
            val notification = buildNotification(
                status = repository.serverDeviceStatus.value,
                sendingEnabled = repository.serverSendingEnabled.value,
                lastHeartbeat = repository.lastHeartbeatTime.value,
                preflightErr = repository.preflightError.value
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Foreground service start failed (e.g. OEM restrictions or missing notification perm)
        }
    }

    private fun stopGateway() {
        repository.keystoreManager.isGatewayActive = false
        loopCoordinator.stop()
    }

    private fun buildNotification(
        status: String,
        sendingEnabled: Boolean,
        lastHeartbeat: Long?,
        preflightErr: String?
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, GatewayForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (preflightErr != null) {
            "Relay Gateway: Warning"
        } else if (repository.keystoreManager.isGatewayActive) {
            "Relay Gateway: Active"
        } else {
            "Relay Gateway: Paused"
        }

        val heartbeatText = if (lastHeartbeat != null) {
            val secAgo = ((System.currentTimeMillis() - lastHeartbeat) / 1000).coerceAtLeast(0)
            "${secAgo}s ago"
        } else {
            "Never"
        }

        val simLabel = repository.keystoreManager.selectedSimLabel.ifEmpty { "Default SIM" }
        val batteryPct = telemetryHelper.getTelemetry().batteryPct

        val content = if (preflightErr != null) {
            preflightErr
        } else {
            "Server: $status | Heartbeat: $heartbeatText | SIM: $simLabel | Bat: $batteryPct%"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_stat_relay)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(0, "Stop Gateway", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Relay Gateway Foreground Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time gateway transmission status and connectivity"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        loopCoordinator.stop()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "relay_gateway_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.relaygateway.ACTION_START"
        const val ACTION_STOP = "com.example.relaygateway.ACTION_STOP"

        fun startService(context: Context) {
            try {
                val intent = Intent(context, GatewayForegroundService::class.java).apply {
                    action = ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Ignore if foreground service cannot be started due to system restrictions
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, GatewayForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
