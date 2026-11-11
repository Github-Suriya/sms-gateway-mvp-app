package com.example.ui.screens.pair

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError

@Composable
fun PairDeviceScreen(
    viewModel: MainViewModel,
    onPairSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pairingState by viewModel.pairingState.collectAsState()
    var qrPayloadInput by remember { mutableStateOf("") }
    var showQrPasteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Header Icon
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Security Gateway",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }

        Text(
            text = "Pair Relay Gateway",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Connect this device securely to your Laravel SMS Gateway backend using a single-use 5-minute pairing code.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        // Error Banner
        AnimatedVisibility(visible = pairingState.errorMessage != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pairing_error_banner")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = RoseError,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = pairingState.errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Server URL Card with Origin Validation
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "1. Server Base URL",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = pairingState.urlInput,
                    onValueChange = { viewModel.updatePairingUrl(it) },
                    label = { Text("Base URL") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("server_url_input"),
                    singleLine = true,
                    isError = pairingState.urlError != null,
                    supportingText = {
                        if (pairingState.urlError != null) {
                            Text(pairingState.urlError!!, color = RoseError)
                        } else if (pairingState.detectedOrigin != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "TLS Verified",
                                    tint = EmeraldSuccess,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Origin: ${pairingState.detectedOrigin}", color = EmeraldSuccess)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        // Pairing Code Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "2. Eight-Digit Code",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "5-min single-use",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = pairingState.pairingCodeInput,
                    onValueChange = { viewModel.updatePairingCode(it) },
                    label = { Text("Pairing Code") },
                    placeholder = { Text("12345678") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pairing_code_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 4.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pairingState.deviceNameInput,
                    onValueChange = { viewModel.updateDeviceName(it) },
                    label = { Text("Device Name (Optional)") },
                    placeholder = { Text("Office Android Gateway 01") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("device_name_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        // QR Code Quick Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { showQrPasteDialog = !showQrPasteDialog },
                modifier = Modifier
                    .weight(1f)
                    .testTag("qr_payload_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Paste QR JSON")
            }

            OutlinedButton(
                onClick = {
                    val sample = """{"base_url": "https://gateway.techtigers.in/api/v1", "pairing_code": "12345678"}"""
                    viewModel.parseQrPayload(sample)
                },
                modifier = Modifier.testTag("fill_sample_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text("Sample")
            }
        }

        // QR Paste Dialog Expansion
        AnimatedVisibility(visible = showQrPasteDialog) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Paste QR Payload JSON",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = qrPayloadInput,
                        onValueChange = { qrPayloadInput = it },
                        placeholder = { Text("""{"base_url":"https://...","pairing_code":"12345678"}""") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("qr_json_field"),
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.parseQrPayload(qrPayloadInput)
                            showQrPasteDialog = false
                        },
                        modifier = Modifier
                            .align(Alignment.End)
                            .testTag("apply_qr_button")
                    ) {
                        Text("Apply Payload")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Submit Pair Button
        Button(
            onClick = { viewModel.submitPairing(onPairSuccess) },
            enabled = !pairingState.isPairing &&
                    pairingState.urlError == null &&
                    pairingState.pairingCodeInput.length == 8,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("pair_submit_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            if (pairingState.isPairing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.5.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Exchanging Credentials...", fontWeight = FontWeight.Bold)
            } else {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Authorize & Pair Device", fontWeight = FontWeight.Bold)
            }
        }

        // Security Notice
        Text(
            text = "Keystore security: The server returns the bearer token exactly once upon pairing. The token is stored encrypted using Android Keystore AES-256 GCM. Hardware identifiers (IMEI/IMSI) are never accessed or transmitted.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}
