package com.akaroai.chronicle.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akaroai.chronicle.model.ChangeProposalEntity
import com.akaroai.chronicle.provider.ProposalParser

@Composable
fun ReviewTab(vm: ChronicleViewModel) {
    val proposals by vm.pendingProposals.collectAsState()
    val scanning by vm.isReviewScanning.collectAsState()
    val provider by vm.providerSettings.collectAsState()

    var editing by remember { mutableStateOf<ChangeProposalEntity?>(null) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                "Review proposed changes",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Nothing here changes campaign canon until you approve it.",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = vm::scanLastExchangeForProposals,
                enabled = provider.enabled && !scanning
            ) {
                Icon(Icons.Default.AutoFixHigh, null)
                Spacer(Modifier.width(8.dp))
                Text(if (scanning) "Scanning…" else "Scan last exchange")
            }

            if (provider.enabled && provider.autoReviewEnabled) {
                Text(
                    "Automatic scanning is ON. It uses one additional AI request after each storyteller reply.",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        if (scanning) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        if (proposals.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp)
            ) {
                Text(
                    if (scanning) "Chronicle is checking for continuity changes…"
                    else "No pending changes. Continue the campaign and Chronicle will place anything worth reviewing here."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(proposals, key = { it.id }) { proposal ->
                    ProposalCard(
                        proposal = proposal,
                        onApprove = { vm.approveProposal(proposal) },
                        onReject = { vm.rejectProposal(proposal) },
                        onEdit = { editing = proposal }
                    )
                }
            }
        }
    }

    editing?.let { proposal ->
        EditProposalDialog(
            proposal = proposal,
            onDismiss = { editing = null },
            onApprove = { edited ->
                vm.approveProposal(proposal, edited)
                editing = null
            }
        )
    }
}

@Composable
private fun ProposalCard(
    proposal: ChangeProposalEntity,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onEdit: () -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(
                proposal.summary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(4.dp))
            Text(targetLabel(proposal), style = MaterialTheme.typography.labelMedium)

            Spacer(Modifier.height(10.dp))
            Text("Proposed changes", fontWeight = FontWeight.Bold)
            Text(ProposalParser.prettyChanges(proposal.proposedChanges))

            if (proposal.reason.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text("Why Chronicle suggested this", fontWeight = FontWeight.Bold)
                Text(proposal.reason)
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onApprove) {
                    Icon(Icons.Default.Check, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Approve")
                }

                OutlinedButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Edit")
                }

                TextButton(onClick = onReject) {
                    Icon(Icons.Default.Close, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Reject")
                }
            }
        }
    }
}

@Composable
private fun EditProposalDialog(
    proposal: ChangeProposalEntity,
    onDismiss: () -> Unit,
    onApprove: (String) -> Unit
) {
    var changes by remember(proposal) { mutableStateOf(proposal.proposedChanges) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit before approving") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(proposal.summary)
                Text(
                    "Advanced editor: Chronicle stores the proposed fields as JSON. " +
                        "You can change the values before approving.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = changes,
                    onValueChange = { changes = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 6,
                    maxLines = 12,
                    label = { Text("Proposed change data") }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onApprove(changes) }) {
                Text("Approve edited change")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun targetLabel(proposal: ChangeProposalEntity): String =
    when (proposal.targetType) {
        "memory_new" -> "New campaign memory"
        "character_update" -> "Character sheet • ID ${proposal.targetId ?: "?"}"
        "campaign_update" -> "Campaign details"
        else -> proposal.targetType
    }
