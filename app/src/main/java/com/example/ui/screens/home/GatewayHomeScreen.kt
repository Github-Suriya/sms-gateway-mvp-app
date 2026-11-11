package com.example.ui.screens.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.MainViewModel
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BlueInfo
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import androidx.compose.foundation.BorderStroke

@Composable
fun GatewayHomeScreen(
    viewModel: MainViewModel,
    onNavigateToPair: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    // Permission launchers
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshState() }

    val phoneStatePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshState() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshState() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Master Gateway Active Control Banner
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (uiState.isGatewayActive) 
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) 
                else 
                    MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 2.dp,
                    color = if (uiState.isGatewayActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(20.dp)
                )
                .testTag("gateway_master_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (uiState.isGatewayActive) "Gateway Active" else "Gateway Idle / Paused",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.isGatewayActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (uiState.isGatewayActive)
                            "Polling every 15s • Heartbeat 30s • Foreground active"
                        else
                            "Explicit user control stopped. No jobs are claimed or sent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = uiState.isGatewayActive,
                    onCheckedChange = { viewModel.setGatewayActive(it) },
                    modifier = Modifier.testTag("gateway_active_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        // Preflight Warning Banner
        AnimatedVisibility(visible = uiState.preflightError != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("preflight_error_banner")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = RoseError,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = uiState.preflightError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Two-Column Status Distinction: Server Connection vs SMS Capability
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Server Connection Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (uiState.serverStatus == "active") Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (uiState.serverStatus == "active") EmeraldSuccess else AmberWarning,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Server",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.serverStatus.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.serverStatus == "active") EmeraldSuccess else AmberWarning
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val hbText = if (uiState.lastHeartbeatTime != null) {
                        val sec = ((System.currentTimeMillis() - uiState.lastHeartbeatTime!!) / 1000).coerceAtLeast(0)
                        "${sec}s ago"
                    } else "Never"
                    Text(
                        text = "Heartbeat: $hbText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (uiState.sendingEnabled) "Sending: Enabled" else "Sending: Server Disabled",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.sendingEnabled) EmeraldSuccess else RoseError
                    )
                }
            }

            // SMS Capability Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SimCard,
                            contentDescription = null,
                            tint = if (uiState.hasSmsPermission) CyanPrimary else RoseError,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SMS Capability",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (uiState.hasSmsPermission) "Ready" else "No Permission",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.hasSmsPermission) CyanPrimary else RoseError
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.selectedSimLabel.ifEmpty { "No SIM Chosen" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "SubId: ${if (uiState.selectedSubId >= 0) uiState.selectedSubId else "Default"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Permissions & Readiness Section
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Android System Permissions",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                PermissionRow(
                    title = "SEND_SMS (Runtime SMS Dispatch)",
                    granted = uiState.hasSmsPermission,
                    onRequest = { smsPermissionLauncher.launch(Manifest.permission.SEND_SMS) },
                    description = "Required to transmit SMS via native SmsManager without broad read/receive permissions."
                )

                Spacer(modifier = Modifier.height(8.dp))

                PermissionRow(
                    title = "READ_PHONE_STATE (SIM Enumeration)",
                    granted = uiState.hasPhoneStatePermission,
                    onRequest = { phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE) },
                    description = "Needed only to list SIM card slots and carrier names for explicit selection."
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Spacer(modifier = Modifier.height(8.dp))
                    PermissionRow(
                        title = "POST_NOTIFICATIONS (Foreground Status)",
                        granted = uiState.hasNotificationPermission,
                        onRequest = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        description = "Required for persistent foreground service notification and stop control."
                    )
                }
            }
        }

        // Device Telemetry Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tel = uiState.telemetry
                val batteryIcon = if (tel?.isCharging == true) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull

                TelemetryItem(
                    icon = batteryIcon,
                    label = "Battery",
                    value = "${tel?.batteryPct ?: 85}%${if (tel?.isCharging == true) " (Charging)" else ""}"
                )

                TelemetryItem(
                    icon = Icons.Default.CloudDone,
                    label = "Network",
                    value = tel?.networkType?.replaceFirstChar { it.uppercase() } ?: "WiFi"
                )

                TelemetryItem(
                    icon = Icons.Default.Notifications,
                    label = "Service",
                    value = if (uiState.manualForegroundFallback) "Manual Mode" else "Foreground"
                )
            }
        }

        // Quick Operator Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                onClick = { viewModel.triggerHeartbeat() },
                modifier = Modifier
                    .weight(1f)
                    .testTag("send_heartbeat_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Heartbeat")
            }

            FilledTonalButton(
                onClick = { viewModel.triggerPoll() },
                modifier = Modifier
                    .weight(1f)
                    .testTag("poll_queue_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                @Suppress("DEPRECATION")
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Poll Queue")
            }
        }

        // Not paired banner
        if (!uiState.isPaired) {
            OutlinedCard(
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Device Not Paired",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AmberWarning
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "This device does not have an active authentication token. Pair with your Laravel backend to begin processing SMS jobs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onNavigateToPair,
                        modifier = Modifier.fillMaxWidth().testTag("go_to_pair_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Pair Device Now", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionRow(
    title: String,
    granted: Boolean,
    onRequest: () -> Unit,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (granted) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (granted) EmeraldSuccess else RoseError,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!granted) {
            Button(
                onClick = onRequest,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text("Grant", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun TelemetryItem(
    icon: ImageVector,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = null, tint = BlueInfo, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
