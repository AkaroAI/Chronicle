package com.akaroai.chronicle.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akaroai.chronicle.model.ChangeProposalEntity
import com.akaroai.chronicle.model.CharacterEntity
import com.akaroai.chronicle.provider.ProposalParser

@Composable
fun ReviewTab(vm: ChronicleViewModel) {
    val proposals by vm.pendingProposals.collectAsState()
    val characters by vm.characters.collectAsState()
    val scanning by vm.isReviewScanning.collectAsState()
    val provider by vm.providerSettings.collectAsState()

    var editing by remember { mutableStateOf<ChangeProposalEntity?>(null) }

    val critical = proposals.count { it.priority == "Critical" }
    val high = proposals.count { it.priority == "High" }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                "Review Inbox",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Chronicle organizes suggestions here. Nothing becomes canon until you approve it.",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (critical > 0) {
                    AssistChip(
                        onClick = {},
                        label = { Text("$critical Critical") },
                        leadingIcon = { Icon(Icons.Default.PriorityHigh, null) }
                    )
                }
                if (high > 0) {
                    AssistChip(
                        onClick = {},
                        label = { Text("$high High") }
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text("${proposals.size} Pending") }
                )
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = vm::scanLastExchangeForProposals,
                enabled = provider.enabled && !scanning
            ) {
                Icon(Icons.Default.AutoFixHigh, null)
                Spacer(Modifier.width(8.dp))
                Text(if (scanning) "Scanning…" else "Scan last exchange")
            }
        }

        if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())

        if (proposals.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp)) {
                Text(
                    if (scanning) "Checking for continuity changes…"
                    else "Inbox clear. Keep playing — Chronicle will organize meaningful changes here."
                )
            }
        } else {
            val grouped = proposals.groupBy { it.groupType }
            val order = listOf("Characters", "World", "Events", "Relationships", "Lore", "Quests", "Other")

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                order.forEach { type ->
                    val items = grouped[type].orEmpty()
                    if (items.isNotEmpty()) {
                        item(key = "header-$type") {
                            ProposalGroup(
                                title = type,
                                proposals = items,
                                characters = characters,
                                vm = vm,
                                onEdit = { editing = it }
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { proposal ->
        EditProposalDialog(
            proposal = proposal,
            onDismiss = { editing = null },
            onApprove = {
                vm.approveProposal(proposal, it)
                editing = null
            }
        )
    }
}

@Composable
private fun ProposalGroup(
    title: String,
    proposals: List<ChangeProposalEntity>,
    characters: List<CharacterEntity>,
    vm: ChronicleViewModel,
    onEdit: (ChangeProposalEntity) -> Unit
) {
    var expanded by remember(title, proposals.size) { mutableStateOf(true) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(groupIcon(title), null)
                    Spacer(Modifier.width(8.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Badge { Text(proposals.size.toString()) }
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null
                )
            }

            if (expanded) {
                if (title == "Characters") {
                    val byCharacter = proposals.groupBy { p ->
                        characters.firstOrNull { it.id == p.targetId }?.name
                            ?: p.groupLabel.ifBlank { "Character changes" }
                    }

                    byCharacter.forEach { (name, items) ->
                        CharacterProposalFolder(
                            name = name,
                            proposals = items,
                            vm = vm,
                            onEdit = onEdit
                        )
                    }
                } else {
                    val byLabel = proposals.groupBy {
                        it.groupLabel.ifBlank { title }
                    }

                    byLabel.forEach { (label, items) ->
                        SubFolder(
                            label = label,
                            proposals = items,
                            vm = vm,
                            onEdit = onEdit
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterProposalFolder(
    name: String,
    proposals: List<ChangeProposalEntity>,
    vm: ChronicleViewModel,
    onEdit: (ChangeProposalEntity) -> Unit
) {
    SubFolder(
        label = name,
        proposals = proposals,
        vm = vm,
        onEdit = onEdit
    )
}

@Composable
private fun SubFolder(
    label: String,
    proposals: List<ChangeProposalEntity>,
    vm: ChronicleViewModel,
    onEdit: (ChangeProposalEntity) -> Unit
) {
    var expanded by remember(label, proposals.size) { mutableStateOf(true) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Badge { Text(proposals.size.toString()) }
            }
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                null
            )
        }

        if (expanded) {
            proposals.forEach { proposal ->
                ProposalCard(
                    proposal = proposal,
                    onApprove = { vm.approveProposal(proposal) },
                    onReject = { vm.rejectProposal(proposal) },
                    onEdit = { onEdit(proposal) }
                )
                Spacer(Modifier.height(8.dp))
            }

            if (proposals.size > 1) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = { vm.rejectAll(proposals) }) {
                        Text("Reject all")
                    }
                    OutlinedButton(onClick = { vm.approveAll(proposals) }) {
                        Text("Approve all")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProposalCard(
    proposal: ChangeProposalEntity,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onEdit: () -> Unit
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    proposal.summary,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                PriorityBadge(proposal.priority)
            }

            Spacer(Modifier.height(6.dp))
            Text(targetLabel(proposal), style = MaterialTheme.typography.labelMedium)

            Spacer(Modifier.height(8.dp))
            Text(
                ProposalParser.prettyChanges(proposal.proposedChanges),
                style = MaterialTheme.typography.bodyMedium
            )

            if (proposal.reason.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Why", fontWeight = FontWeight.SemiBold)
                Text(proposal.reason, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = onApprove) { Text("Approve") }
                OutlinedButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onReject) { Text("Reject") }
            }
        }
    }
}

@Composable
private fun PriorityBadge(priority: String) {
    AssistChip(
        onClick = {},
        label = { Text(priority) },
        leadingIcon = {
            if (priority == "Critical") {
                Icon(Icons.Default.PriorityHigh, null)
            }
        }
    )
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
                Text("Approve edited")
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
        "cast_tier_update" -> "Cast importance • ID ${proposal.targetId ?: "?"}"
        "campaign_update" -> "Campaign details"
        else -> proposal.targetType
    }

private fun groupIcon(type: String) =
    when (type) {
        "Characters" -> Icons.Default.Groups
        "World" -> Icons.Default.Public
        "Events" -> Icons.Default.Event
        "Relationships" -> Icons.Default.Favorite
        "Lore" -> Icons.Default.MenuBook
        "Quests" -> Icons.Default.Flag
        else -> Icons.Default.Inbox
    }
