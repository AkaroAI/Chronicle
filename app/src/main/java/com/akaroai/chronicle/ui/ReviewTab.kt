package com.akaroai.chronicle.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akaroai.chronicle.data.IntegrityPolicy
import com.akaroai.chronicle.model.ChangeProposalEntity
import com.akaroai.chronicle.model.CharacterEntity
import com.akaroai.chronicle.provider.ProposalParser
import org.json.JSONObject

@Composable
fun ReviewTab(vm: ChronicleViewModel) {
    val proposals by vm.pendingProposals.collectAsState()
    val characters by vm.characters.collectAsState()
    val scanning by vm.isReviewScanning.collectAsState()
    val provider by vm.providerSettings.collectAsState()

    var editing by remember { mutableStateOf<ChangeProposalEntity?>(null) }
    var integrityCharacter by remember { mutableStateOf<CharacterEntity?>(null) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text("Review Inbox", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Nothing becomes canon until you approve it. Integrity Guard blocks accidental core-character rewrites.",
                style = MaterialTheme.typography.bodySmall
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val guarded = proposals.count { it.integrityWarning.isNotBlank() }
                if (guarded > 0) {
                    AssistChip(
                        onClick = {},
                        label = { Text("$guarded Guarded") },
                        leadingIcon = { Icon(Icons.Default.Shield, null) }
                    )
                }
                AssistChip(onClick = {}, label = { Text("${proposals.size} Pending") })
            }

            OutlinedButton(
                onClick = vm::scanLastExchangeForProposals,
                enabled = provider.enabled && !scanning
            ) {
                Icon(Icons.Default.AutoFixHigh, null)
                Spacer(Modifier.width(8.dp))
                Text(if (scanning) "Scanning…" else "Scan last exchange")
            }

            if (characters.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Character Integrity", fontWeight = FontWeight.Bold)
                Text(
                    "Tap a character to set Strict, Balanced, or Flexible protection.",
                    style = MaterialTheme.typography.bodySmall
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(characters, key = { it.id }) { c ->
                        AssistChip(
                            onClick = { integrityCharacter = c },
                            label = { Text("${c.name} • ${c.integrityMode}") },
                            leadingIcon = { Icon(Icons.Default.Shield, null) }
                        )
                    }
                }
            }
        }

        if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())

        if (proposals.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp)) {
                Text(if (scanning) "Checking for continuity changes…" else "Inbox clear.")
            }
        } else {
            val grouped = proposals.groupBy { it.groupType }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf("Characters","World","Events","Relationships","Lore","Quests","Other").forEach { group ->
                    val groupItems = grouped[group].orEmpty()
                    if (groupItems.isNotEmpty()) {
                        item(key = "header-$group") {
                            Text(group, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        items(groupItems, key = { it.id }) { proposal ->
                            ProposalCard(
                                proposal = proposal,
                                onApprove = { vm.approveProposal(proposal) },
                                onReject = { vm.rejectProposal(proposal) },
                                onEdit = { editing = proposal }
                            )
                        }
                        if (groupItems.size > 1) {
                            item(key = "bulk-$group") {
                                val safe = groupItems.filter { it.integrityWarning.isBlank() }
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { vm.rejectAll(groupItems) }) { Text("Reject all") }
                                    if (safe.isNotEmpty()) {
                                        OutlinedButton(onClick = { vm.approveAll(safe) }) {
                                            Text(if (safe.size == groupItems.size) "Approve all" else "Approve safe (${safe.size})")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { proposal ->
        SafeProposalDialog(
            proposal = proposal,
            target = characters.firstOrNull { it.id == proposal.targetId },
            onDismiss = { editing = null },
            onApprove = {
                vm.approveProposal(proposal, it)
                editing = null
            }
        )
    }

    integrityCharacter?.let { character ->
        IntegritySettingsDialog(
            character = character,
            onDismiss = { integrityCharacter = null },
            onSave = {
                vm.updateCharacter(it)
                integrityCharacter = null
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
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(proposal.summary, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                AssistChip(onClick = {}, label = { Text(proposal.priority) })
            }

            Text(targetLabel(proposal), style = MaterialTheme.typography.labelMedium)
            Text(
                "Mode: ${proposal.changeMode} • Evidence: ${proposal.evidenceType}",
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(8.dp))
            Text(ProposalParser.prettyChanges(proposal.proposedChanges))

            if (proposal.integrityWarning.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "🛡 ${proposal.integrityWarning}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (proposal.reason.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Why", fontWeight = FontWeight.SemiBold)
                Text(proposal.reason, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (proposal.integrityWarning.isBlank()) {
                    Button(onClick = onApprove) { Text("Approve") }
                } else {
                    Button(onClick = onEdit) { Text("Review override") }
                }
                OutlinedButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onReject) { Text("Reject") }
            }
        }
    }
}

@Composable
private fun SafeProposalDialog(
    proposal: ChangeProposalEntity,
    target: CharacterEntity?,
    onDismiss: () -> Unit,
    onApprove: (String) -> Unit
) {
    var changes by remember(proposal) { mutableStateOf(proposal.proposedChanges) }
    var mode by remember(proposal) { mutableStateOf(proposal.changeMode) }
    var evidence by remember(proposal) { mutableStateOf(proposal.evidenceType) }
    var overrideLocked by remember { mutableStateOf(false) }
    var modeMenu by remember { mutableStateOf(false) }
    var evidenceMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review change safely") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (proposal.integrityWarning.isNotBlank()) {
                    Text(
                        proposal.integrityWarning,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box {
                    OutlinedButton(onClick = { modeMenu = true }) { Text("Change mode: $mode") }
                    DropdownMenu(modeMenu, { modeMenu = false }) {
                        listOf("Append","Replace","Clear").forEach {
                            DropdownMenuItem(
                                text = { Text(it) },
                                onClick = { mode = it; modeMenu = false }
                            )
                        }
                    }
                }
                Text(
                    when (mode) {
                        "Append" -> "Adds development without erasing what is already established."
                        "Clear" -> "Removes the targeted value."
                        else -> "Replaces the existing value. Best for genuine transformations or corrections."
                    },
                    style = MaterialTheme.typography.bodySmall
                )

                Box {
                    OutlinedButton(onClick = { evidenceMenu = true }) { Text("Evidence: $evidence") }
                    DropdownMenu(evidenceMenu, { evidenceMenu = false }) {
                        listOf("Player Confirmed","Story Event","Assistant Only","Unverified").forEach {
                            DropdownMenuItem(
                                text = { Text(it) },
                                onClick = { evidence = it; evidenceMenu = false }
                            )
                        }
                    }
                }
                Text(
                    "Assistant Only should be used when the AI introduced a fact without you or an established story event confirming it.",
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

                if (proposal.integrityWarning.contains("Protected core field")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(overrideLocked, { overrideLocked = it })
                        Text("I explicitly approve changing protected core fields.")
                    }
                }

                target?.let {
                    Text("${it.name}: ${it.integrityMode} integrity", style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !proposal.integrityWarning.contains("Protected core field") || overrideLocked,
                onClick = {
                    try {
                        val obj = JSONObject(changes)
                        obj.put("__changeMode", mode)
                        obj.put("__evidenceType", evidence)
                        if (overrideLocked) obj.put("__integrityOverride", true)
                        onApprove(obj.toString())
                    } catch (_: Throwable) {
                        onApprove(changes)
                    }
                }
            ) { Text("Approve reviewed") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun IntegritySettingsDialog(
    character: CharacterEntity,
    onDismiss: () -> Unit,
    onSave: (CharacterEntity) -> Unit
) {
    var mode by remember(character) { mutableStateOf(character.integrityMode) }
    var extra by remember(character) {
        mutableStateOf(
            character.protectedFields.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        )
    }
    var menu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${character.name} • Integrity Guard") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box {
                    OutlinedButton(onClick = { menu = true }) { Text("Integrity mode: $mode") }
                    DropdownMenu(menu, { menu = false }) {
                        listOf("Strict","Balanced","Flexible").forEach {
                            DropdownMenuItem(
                                text = { Text(it) },
                                onClick = { mode = it; menu = false }
                            )
                        }
                    }
                }

                Text(
                    when (mode) {
                        "Strict" -> "Recommended for Main cast. Strongly protects identity, history, personality, abilities, and secrets."
                        "Flexible" -> "Minimal automatic protection. Useful for minor or rapidly changing characters."
                        else -> "Protects foundational identity while allowing normal development."
                    },
                    style = MaterialTheme.typography.bodySmall
                )

                Text("Additional protected fields", fontWeight = FontWeight.Bold)

                IntegrityPolicy.allCharacterFields.forEach { field ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = field in extra,
                            onCheckedChange = { checked ->
                                extra = if (checked) extra + field else extra - field
                            }
                        )
                        Text(field)
                    }
                }

                val effective = IntegrityPolicy.protectedFields(
                    character.copy(integrityMode = mode, protectedFields = extra.joinToString(","))
                )
                Text(
                    "Effective protection: ${effective.joinToString(", ").ifBlank { "none" }}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        character.copy(
                            integrityMode = mode,
                            protectedFields = extra.sorted().joinToString(",")
                        )
                    )
                }
            ) { Text("Save protection") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
