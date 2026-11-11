package com.example.ui.screens.jobs

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.JobEntity
import com.example.data.model.LocalJobState
import com.example.ui.MainViewModel
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BlueInfo
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError

@Composable
fun RecentJobsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val jobs by viewModel.recentJobs.collectAsState()
    val ambiguousJobs by viewModel.ambiguousJobs.collectAsState()
    val reconcileResult by viewModel.reconcileResult.collectAsState()

    var filterAmbiguousOnly by remember { mutableStateOf(false) }

    val displayedJobs = if (filterAmbiguousOnly) {
        jobs.filter { it.isAmbiguous || it.localState == LocalJobState.NEEDS_REVIEW.name }
    } else {
        jobs
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Header & Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Durable Send Ledger",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${jobs.size} local job records • Room persisted",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (ambiguousJobs.isNotEmpty()) {
                OutlinedButton(
                    onClick = { filterAmbiguousOnly = !filterAmbiguousOnly },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (filterAmbiguousOnly) AmberWarning else MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.testTag("filter_ambiguous_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = AmberWarning,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Needs Review (${ambiguousJobs.size})")
                }
            }
        }

        // Prominent Ambiguous Alert Banner if any jobs require manual review
        if (ambiguousJobs.isNotEmpty() && !filterAmbiguousOnly) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag("ambiguous_alert_banner")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = AmberWarning,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${ambiguousJobs.size} Job(s) Require Operator Review",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Automatic resend is strictly prohibited to prevent duplicate SMS transmissions. Reconcile with server.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        if (displayedJobs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.HourglassTop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No SMS Jobs in Local Ledger",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "When the gateway claims a job from the Laravel server, it will appear here with full execution timeline and callback proof.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(displayedJobs, key = { it.jobId }) { job ->
                    JobItemCard(
                        job = job,
                        onReconcile = { viewModel.reconcileJob(job.jobId) }
                    )
                }
            }
        }
    }

    // Server Reconciliation Dialog
    reconcileResult?.let { (jobId, response) ->
        AlertDialog(
            onDismissRequest = { viewModel.clearReconcileDialog() },
            title = {
                Text(
                    text = "Server Reconcile: $jobId",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (response != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Authoritative Server State:",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("Status: ${response.status}", fontWeight = FontWeight.Bold)
                        Text("Lease ID: ${response.leaseId}")
                        Text("Last Event At: ${response.lastEventAt ?: "None"}")
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Note: If the server reports 'sending' after the 10-minute timeout, the job is treated as unknown. Blind resend is disabled by contract.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = "Unable to fetch job reconciliation from server. Check network connection or verify if device is revoked.",
                        color = RoseError
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearReconcileDialog() }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun JobItemCard(
    job: JobEntity,
    onReconcile: () -> Unit
) {
    val state = try {
        LocalJobState.valueOf(job.localState)
    } catch (e: Exception) {
        LocalJobState.NEEDS_REVIEW
    }

    val stateColor = when (state) {
        LocalJobState.SENT_TO_CARRIER -> CyanPrimary
        LocalJobState.DELIVERED -> EmeraldSuccess
        LocalJobState.SEND_FAILED, LocalJobState.DELIVERY_FAILED -> RoseError
        LocalJobState.NEEDS_REVIEW -> AmberWarning
        LocalJobState.START_CONFIRMED, LocalJobState.SEND_INVOKED -> BlueInfo
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (job.isAmbiguous) 
                AmberWarning.copy(alpha = 0.08f) 
            else 
                MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (job.isAmbiguous) 1.5.dp else 1.dp,
                color = if (job.isAmbiguous) AmberWarning else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp)
            )
            .testTag("job_card_${job.jobId}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Job UUID & State Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ID: ${job.jobId.take(8)}...",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(stateColor.copy(alpha = 0.2f))
                        .border(1.dp, stateColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = state.name.replace("_", " "),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = stateColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Destination and Part Progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "To: ${job.destinationE164}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${job.partsTotal} Part${if (job.partsTotal > 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Body Preview (truncated for privacy)
            Text(
                text = job.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )

            // Ambiguity Warning details
            if (job.isAmbiguous && job.ambiguityReason != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AmberWarning.copy(alpha = 0.15f))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = AmberWarning,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = job.ambiguityReason,
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberWarning
                    )
                }
            }

            // Results and Reconcile Button
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (job.finalResultCode != null) "Result: ${job.finalResultCode}" else "Lease: ${job.leaseId.take(8)}...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                OutlinedButton(
                    onClick = onReconcile,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp).testTag("reconcile_button_${job.jobId}")
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reconcile", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
