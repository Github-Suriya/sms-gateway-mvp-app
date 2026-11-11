package com.example.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.MainViewModel
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError

@Composable
fun SettingsTroubleshootingScreen(
    viewModel: MainViewModel,
    onUnpaired: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    var serverUrlInput by remember(uiState.baseUrl) { mutableStateOf(uiState.baseUrl) }
    var urlSaveSuccess by remember { mutableStateOf(false) }
    var urlSaveError by remember { mutableStateOf<String?>(null) }
    var simDropdownExpanded by remember { mutableStateOf(false) }
    var showUnpairDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings & Diagnostics",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // 1. Configurable Server Base URL
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Configurable Server Base URL",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Requires HTTPS outside explicitly marked debug builds. TLS certificates are strictly verified; bearer tokens are never forwarded across cross-origin redirects.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = serverUrlInput,
                    onValueChange = {
                        serverUrlInput = it
                        urlSaveSuccess = false
                        urlSaveError = null
                    },
                    modifier = Modifier.fillMaxWidth().testTag("settings_server_url_input"),
                    label = { Text("Server URL") },
                    placeholder = { Text("https://gateway.techtigers.in/api/v1") },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (urlSaveSuccess) {
                        Text("Saved successfully", color = EmeraldSuccess, style = MaterialTheme.typography.bodySmall)
                    } else if (urlSaveError != null) {
                        Text(urlSaveError!!, color = RoseError, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Button(
                        onClick = {
                            val success = viewModel.updateServerUrl(serverUrlInput)
                            if (success) {
                                urlSaveSuccess = true
                                urlSaveError = null
                            } else {
                                urlSaveError = "Invalid URL: HTTPS is required in production."
                                urlSaveSuccess = false
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("save_server_url_button")
                    ) {
                        Text("Save URL")
                    }
                }
            }
        }

        // 2. Explicit SIM Selection
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SimCard,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Explicit SIM Subscription",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Android Gateway never silently switches SIMs. If the selected SIM is removed or slot changes, sending pauses automatically before start acknowledgment.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { simDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth().testTag("sim_selector_dropdown"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = uiState.selectedSimLabel.ifEmpty { "Select Active SIM" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text("Change ▼", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    DropdownMenu(
                        expanded = simDropdownExpanded,
                        onDismissRequest = { simDropdownExpanded = false }
                    ) {
                        if (uiState.availableSubscriptions.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No active SIM subscriptions detected (Grant Phone State)") },
                                onClick = { simDropdownExpanded = false }
                            )
                        } else {
                            uiState.availableSubscriptions.forEach { sub ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(sub.userLabel, fontWeight = FontWeight.Bold)
                                            Text("Subscription ID: ${sub.subscriptionId} • Slot: ${sub.slotIndex}", style = MaterialTheme.typography.labelSmall)
                                        }
                                    },
                                    onClick = {
                                        viewModel.selectSubscription(sub)
                                        simDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Execution & Foreground Policy Options
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Foreground & Recovery Architecture",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Enterprise/Internal Distribution Policy: Foreground services with dataSync provide consistent 15s polling and 30s heartbeat without relying on WorkManager. If OEM battery restrictions interfere, enable manual in-app mode.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Manual Foreground Fallback Mode",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Runs polling loop entirely within active app window without registering background system service.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.manualForegroundFallback,
                        onCheckedChange = { viewModel.toggleManualForegroundFallback(it) },
                        modifier = Modifier.testTag("manual_foreground_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }

        // 4. Zero-Loss Durable Ledger Architecture Explanation
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Safety & Non-Duplication Rules",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "1. Receive & Persist: Job is written to Room DB before acknowledging /start.\n" +
                            "2. Wait for 200: SmsManager is invoked only after server confirms start with accepted: true.\n" +
                            "3. Process Crash Guarantee: A job interrupted during start or execution is NEVER automatically resent on restart. It is marked 'Needs review' for operator inspection.\n" +
                            "4. Safe Retries: Network failures retry ONLY event upload with the same stable event UUID. SMS transmission is never re-executed for event upload errors.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 5. Device Identification & Installation UUID
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Installation & Keystore Credential Storage",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Installation ID (Non-hardware UUID):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = viewModel.uiState.value.deviceId ?: "d3d30a76-...",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 6. Safe Stop & Unpair
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Unpair & Wipe Gateway Data",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = RoseError
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Safely halts the gateway, deletes the encrypted bearer token from Android Keystore, and purges local message ledgers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { showUnpairDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseError, contentColor = MaterialTheme.colorScheme.onError),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("unpair_button")
                ) {
                    Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Unpair Device Safely", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showUnpairDialog) {
        AlertDialog(
            onDismissRequest = { showUnpairDialog = false },
            title = { Text("Confirm Device Unpair") },
            text = {
                Text("Are you sure you want to unpair this device? All local credentials and job logs will be wiped securely from Android Keystore and Room database.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUnpairDialog = false
                        viewModel.unpairDevice()
                        onUnpaired()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseError)
                ) {
                    Text("Yes, Unpair")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnpairDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
