package com.akaroai.chronicle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akaroai.chronicle.model.*

enum class ChronicleTab(val label: String) {
    CHAT("Chat"),
    MEMORY("Memory"),
    CHARACTERS("Characters")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChronicleScreen(vm: ChronicleViewModel) {
    val campaigns by vm.campaigns.collectAsState()
    val selected by vm.selectedCampaign.collectAsState()
    val providerSettings by vm.providerSettings.collectAsState()
    val error by vm.lastError.collectAsState()

    var tab by remember { mutableStateOf(ChronicleTab.CHAT) }
    var createCampaign by remember { mutableStateOf(false) }
    var providerDialog by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Chronicle", fontWeight = FontWeight.Bold)
                        Text(
                            selected?.name ?: "No campaign selected",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { providerDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "AI settings")
                    }
                    IconButton(onClick = { createCampaign = true }) {
                        Icon(Icons.Default.Add, contentDescription = "New campaign")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                ChronicleTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = {
                            Icon(
                                when (item) {
                                    ChronicleTab.CHAT -> Icons.Default.Chat
                                    ChronicleTab.MEMORY -> Icons.Default.Book
                                    ChronicleTab.CHARACTERS -> Icons.Default.Groups
                                },
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            CampaignStrip(campaigns, selected?.id, vm::selectCampaign) {
                createCampaign = true
            }

            if (selected == null) {
                EmptyCampaignState { createCampaign = true }
            } else {
                when (tab) {
                    ChronicleTab.CHAT -> ChatTab(vm, providerSettings.enabled)
                    ChronicleTab.MEMORY -> MemoryTab(vm)
                    ChronicleTab.CHARACTERS -> CharactersTab(vm)
                }
            }
        }
    }

    if (createCampaign) {
        CreateCampaignDialog(
            onDismiss = { createCampaign = false },
            onCreate = { name, description ->
                vm.createCampaign(name, description)
                createCampaign = false
            }
        )
    }

    if (providerDialog) {
        ProviderSettingsDialog(
            current = providerSettings,
            onDismiss = { providerDialog = false },
            onSave = {
                vm.saveProviderSettings(it)
                providerDialog = false
            }
        )
    }
}

@Composable
private fun CampaignStrip(
    campaigns: List<CampaignEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onCreate: () -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(campaigns, key = { it.id }) { campaign ->
            FilterChip(
                selected = campaign.id == selectedId,
                onClick = { onSelect(campaign.id) },
                label = { Text(campaign.name) }
            )
        }
        item {
            AssistChip(
                onClick = onCreate,
                label = { Text("New campaign") },
                leadingIcon = { Icon(Icons.Default.Add, null) }
            )
        }
    }
}

@Composable
private fun EmptyCampaignState(onCreate: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.AutoStories, null, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(12.dp))
            Text("Create your first campaign", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onCreate) { Text("Create campaign") }
        }
    }
}

@Composable
private fun ChatTab(vm: ChronicleViewModel, realAi: Boolean) {
    val messages by vm.messages.collectAsState()
    val generating by vm.isGenerating.collectAsState()
    var input by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (realAi) "AI enabled • campaign-isolated context"
                else "Demo mode • tap ⚙ to connect an AI provider",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { MessageBubble(it) }
            if (generating) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }

        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Continue the story…") },
                maxLines = 5
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = {
                    val text = input
                    input = ""
                    vm.sendMessage(text)
                },
                enabled = input.isNotBlank() && !generating
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun MessageBubble(message: MessageEntity) {
    val isUser = message.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            Modifier.fillMaxWidth(0.88f)
                .background(
                    if (isUser) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            Text(
                if (isUser) "You" else "Chronicle",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(message.content)
        }
    }
}

@Composable
private fun MemoryTab(vm: ChronicleViewModel) {
    val memories by vm.memories.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Campaign Memory", style = MaterialTheme.typography.titleMedium)
                Text("Only this campaign can see these facts.", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { showAdd = true }) { Text("Add") }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(memories, key = { it.id }) { memory ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${memory.category} • ${memory.title}", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(memory.content)
                        TextButton(onClick = { vm.deleteMemory(memory) }) { Text("Remove") }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddMemoryDialog(
            onDismiss = { showAdd = false },
            onAdd = { title, content, category ->
                vm.addMemory(title, content, category)
                showAdd = false
            }
        )
    }
}

@Composable
private fun CharactersTab(vm: ChronicleViewModel) {
    val characters by vm.characters.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Characters", style = MaterialTheme.typography.titleMedium)
                Text("Campaign-specific cast and relationships.", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { showAdd = true }) { Text("Add") }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(characters, key = { it.id }) { character ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(character.name, fontWeight = FontWeight.Bold)
                        if (character.relationship.isNotBlank()) {
                            Text("Relationship: ${character.relationship}")
                        }
                        if (character.summary.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(character.summary)
                        }
                        TextButton(onClick = { vm.deleteCharacter(character) }) {
                            Text("Remove")
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddCharacterDialog(
            onDismiss = { showAdd = false },
            onAdd = { name, summary, relationship ->
                vm.addCharacter(name, summary, relationship)
                showAdd = false
            }
        )
    }
}

@Composable
private fun CreateCampaignDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New campaign") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Campaign name") })
                OutlinedTextField(description, { description = it }, label = { Text("Description") })
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(name, description) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AddMemoryDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String) -> Unit
) {
    var category by remember { mutableStateOf("Canon") }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add memory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(category, { category = it }, label = { Text("Category") })
                OutlinedTextField(title, { title = it }, label = { Text("Title") })
                OutlinedTextField(content, { content = it }, label = { Text("Memory") }, minLines = 3)
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(title, content, category) },
                enabled = title.isNotBlank() && content.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AddCharacterDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add character") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") })
                OutlinedTextField(relationship, { relationship = it }, label = { Text("Relationship") })
                OutlinedTextField(summary, { summary = it }, label = { Text("Notes") }, minLines = 3)
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(name, summary, relationship) }, enabled = name.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
