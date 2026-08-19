package com.akaroai.chronicle.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akaroai.chronicle.model.*

enum class ChronicleTab(val label: String) {
    CHAT("Chat"),
    MEMORY("Memory"),
    CHARACTERS("Characters"),
    REVIEW("Review")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChronicleScreen(vm: ChronicleViewModel) {
    val campaigns by vm.campaigns.collectAsState()
    val archived by vm.archivedCampaigns.collectAsState()
    val selected by vm.selectedCampaign.collectAsState()
    val ps by vm.providerSettings.collectAsState()
    val error by vm.lastError.collectAsState()
    val notice by vm.notice.collectAsState()
    val proposals by vm.pendingProposals.collectAsState()
    val externalDraft by vm.externalImportDraft.collectAsState()
    val importAnalyzing by vm.isImportAnalyzing.collectAsState()
    val context = LocalContext.current

    var tab by remember { mutableStateOf(ChronicleTab.CHAT) }
    var create by remember { mutableStateOf(false) }
    var provider by remember { mutableStateOf(false) }
    var editCampaign by remember { mutableStateOf(false) }
    var archiveDlg by remember { mutableStateOf(false) }
    var deleteDlg by remember { mutableStateOf(false) }
    var importChoice by remember { mutableStateOf<Uri?>(null) }
    var menu by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) vm.exportSelectedCampaign(context, uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importChoice = uri
    }

    val externalImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) vm.analyzeExternalCampaign(context, uri)
    }

    val snack = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        error?.let {
            snack.showSnackbar(it)
            vm.clearError()
        }
    }
    LaunchedEffect(notice) {
        notice?.let {
            snack.showSnackbar(it)
            vm.clearNotice()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
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
                    IconButton(onClick = { provider = true }) {
                        Icon(Icons.Default.Settings, "AI settings")
                    }

                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Default.MoreVert, "Campaign options")
                        }
                        DropdownMenu(
                            expanded = menu,
                            onDismissRequest = { menu = false }
                        ) {
                            if (selected != null) {
                                DropdownMenuItem(
                                    text = { Text("Export campaign") },
                                    onClick = {
                                        menu = false
                                        val safeName = selected?.name
                                            ?.replace(Regex("[^A-Za-z0-9._-]+"), "_")
                                            ?.trim('_')
                                            ?.ifBlank { "Chronicle_Campaign" }
                                            ?: "Chronicle_Campaign"
                                        exportLauncher.launch("$safeName.chronicle")
                                    },
                                    leadingIcon = { Icon(Icons.Default.FileUpload, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Import campaign backup") },
                                    onClick = {
                                        menu = false
                                        importLauncher.launch(arrayOf(
                                            "application/zip",
                                            "application/octet-stream",
                                            "*/*"
                                        ))
                                    },
                                    leadingIcon = { Icon(Icons.Default.FileDownload, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Import existing campaign") },
                                    onClick = {
                                        menu = false
                                        externalImportLauncher.launch(arrayOf(
                                            "text/plain",
                                            "text/markdown",
                                            "application/json",
                                            "*/*"
                                        ))
                                    },
                                    leadingIcon = { Icon(Icons.Default.AutoAwesome, null) }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Edit campaign") },
                                    onClick = { menu = false; editCampaign = true },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Archive / close") },
                                    onClick = {
                                        menu = false
                                        selected?.let(vm::archiveCampaign)
                                    },
                                    leadingIcon = { Icon(Icons.Default.Archive, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete campaign") },
                                    onClick = { menu = false; deleteDlg = true },
                                    leadingIcon = { Icon(Icons.Default.Delete, null) }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Import campaign backup") },
                                    onClick = {
                                        menu = false
                                        importLauncher.launch(arrayOf(
                                            "application/zip",
                                            "application/octet-stream",
                                            "*/*"
                                        ))
                                    },
                                    leadingIcon = { Icon(Icons.Default.FileDownload, null) }
                                )
                            }
                            if (archived.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Archived campaigns") },
                                    onClick = { menu = false; archiveDlg = true },
                                    leadingIcon = { Icon(Icons.Default.Inventory2, null) }
                                )
                            }
                        }
                    }

                    IconButton(onClick = { create = true }) {
                        Icon(Icons.Default.Add, "New campaign")
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
                            if (item == ChronicleTab.REVIEW) {
                                BadgedBox(
                                    badge = {
                                        if (proposals.isNotEmpty()) {
                                            Badge {
                                                Text(
                                                    if (proposals.size > 99) "99+"
                                                    else proposals.size.toString()
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Notifications, item.label)
                                }
                            } else {
                                Icon(
                                    when (item) {
                                        ChronicleTab.CHAT -> Icons.Default.Chat
                                        ChronicleTab.MEMORY -> Icons.Default.Book
                                        ChronicleTab.CHARACTERS -> Icons.Default.Groups
                                        ChronicleTab.REVIEW -> Icons.Default.Notifications
                                    },
                                    item.label
                                )
                            }
                        },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            LazyRow(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(campaigns, key = { it.id }) { c ->
                    FilterChip(
                        selected = c.id == selected?.id,
                        onClick = { vm.selectCampaign(c.id) },
                        label = { Text(c.name) }
                    )
                }
                item {
                    AssistChip(
                        onClick = { create = true },
                        label = { Text("New campaign") },
                        leadingIcon = { Icon(Icons.Default.Add, null) }
                    )
                }
            }

            if (selected == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(onClick = { create = true }) { Text("Create campaign") }
                        OutlinedButton(
                            onClick = {
                                importLauncher.launch(arrayOf(
                                    "application/zip",
                                    "application/octet-stream",
                                    "*/*"
                                ))
                            }
                        ) {
                            Icon(Icons.Default.FileDownload, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Import .chronicle")
                        }
                        OutlinedButton(
                            onClick = {
                                externalImportLauncher.launch(arrayOf(
                                    "text/plain",
                                    "text/markdown",
                                    "application/json",
                                    "*/*"
                                ))
                            },
                            enabled = !importAnalyzing
                        ) {
                            Icon(Icons.Default.AutoAwesome, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (importAnalyzing) "Analyzing…" else "Import existing campaign")
                        }
                    }
                }
            } else {
                when (tab) {
                    ChronicleTab.CHAT -> ChatTab(vm, ps.enabled)
                    ChronicleTab.MEMORY -> MemoryTab(vm)
                    ChronicleTab.CHARACTERS -> CharactersTab(vm)
                    ChronicleTab.REVIEW -> ReviewTab(vm)
                }
            }
        }
    }

    if (create) {
        CreateCampaignDialog({ create = false }) { n, d ->
            vm.createCampaign(n, d)
            create = false
        }
    }

    if (provider) {
        ProviderSettingsDialog(ps, { provider = false }) {
            vm.saveProviderSettings(it)
            provider = false
        }
    }

    importChoice?.let { uri ->
        AlertDialog(
            onDismissRequest = { importChoice = null },
            title = { Text("Import Chronicle campaign") },
            text = {
                Text(
                    if (selected != null) {
                        "Import this backup as a separate campaign, or replace the currently selected campaign '${selected?.name}'. Replace only happens after the backup is successfully read."
                    } else {
                        "Import this backup as a new campaign."
                    }
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.importCampaign(context, uri, replaceCurrent = false)
                    importChoice = null
                }) {
                    Text("Import as copy")
                }
            },
            dismissButton = {
                Row {
                    if (selected != null) {
                        TextButton(onClick = {
                            vm.importCampaign(context, uri, replaceCurrent = true)
                            importChoice = null
                        }) {
                            Text("Replace current")
                        }
                    }
                    TextButton(onClick = { importChoice = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    externalDraft?.let { draft ->
        ExternalImportReviewDialog(
            draft = draft,
            onDismiss = vm::cancelExternalImport,
            onImport = vm::commitExternalImport
        )
    }

    selected?.let { c ->
        if (editCampaign) {
            CampaignEditorDialog(c, { editCampaign = false }) {
                vm.updateCampaign(it)
                editCampaign = false
            }
        }

        if (deleteDlg) {
            AlertDialog(
                onDismissRequest = { deleteDlg = false },
                title = { Text("Delete ${c.name}?") },
                text = {
                    Text(
                        "This permanently deletes its chat, memories, characters, and review notifications."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            vm.deleteCampaign(c)
                            deleteDlg = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Delete permanently") }
                },
                dismissButton = {
                    TextButton(onClick = { deleteDlg = false }) { Text("Cancel") }
                }
            )
        }
    }

    if (archiveDlg) {
        ArchivedCampaignsDialog(
            archived,
            { archiveDlg = false },
            vm::restoreCampaign,
            vm::deleteCampaign
        )
    }
}

@Composable
private fun ChatTab(vm: ChronicleViewModel, real: Boolean) {
    val msgs by vm.messages.collectAsState()
    val gen by vm.isGenerating.collectAsState()
    val scanning by vm.isReviewScanning.collectAsState()
    var input by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Text(
            when {
                gen -> "Chronicle is writing…"
                scanning -> "Reply complete • checking Review Inbox…"
                real -> "AI enabled • campaign-isolated context"
                else -> "Demo mode • tap ⚙ to connect"
            },
            Modifier.padding(12.dp),
            style = MaterialTheme.typography.labelSmall
        )

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(msgs, key = { it.id }) { m -> MessageBubble(m) }
            if (gen) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }

        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                input,
                { input = it },
                Modifier.weight(1f),
                placeholder = { Text("Continue the story…") },
                maxLines = 5
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = {
                    val t = input
                    input = ""
                    vm.sendMessage(t)
                },
                enabled = input.isNotBlank() && !gen
            ) {
                Icon(Icons.Default.Send, "Send")
            }
        }
    }
}

@Composable
private fun MessageBubble(m: MessageEntity) {
    val u = m.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (u) Arrangement.End else Arrangement.Start
    ) {
        Column(
            Modifier.fillMaxWidth(.88f)
                .background(
                    if (u) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            Text(if (u) "You" else "Chronicle", fontWeight = FontWeight.Bold)
            Text(m.content)
        }
    }
}

@Composable
private fun MemoryTab(vm: ChronicleViewModel) {
    val mem by vm.memories.collectAsState()
    var add by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Campaign Memory", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { add = true }) { Text("Add") }
        }

        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(mem, key = { it.id }) { m ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${m.category} • ${m.title}", fontWeight = FontWeight.Bold)
                        Text(m.content)
                        TextButton(onClick = { vm.deleteMemory(m) }) { Text("Remove") }
                    }
                }
            }
        }
    }

    if (add) {
        AddMemoryDialog({ add = false }) { t, c, cat ->
            vm.addMemory(t, c, cat)
            add = false
        }
    }
}

@Composable
private fun CharactersTab(vm: ChronicleViewModel) {
    val chars by vm.characters.collectAsState()
    val campaign by vm.selectedCampaign.collectAsState()
    var editing by remember { mutableStateOf<CharacterEntity?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Characters", style = MaterialTheme.typography.titleMedium)
        Text(
            "Cast tier controls how aggressively Chronicle tracks each character.",
            style = MaterialTheme.typography.bodySmall
        )
        Button(onClick = { adding = true }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Add Character")
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(chars, key = { it.id }) { c ->
            ElevatedCard(
                Modifier.fillMaxWidth().clickable { editing = c }
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            c.name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        AssistChip(onClick = {}, label = { Text(c.castTier) })
                    }

                    if (c.species.isNotBlank() || c.age.isNotBlank()) {
                        Text(
                            listOf(c.species, c.age)
                                .filter { it.isNotBlank() }
                                .joinToString(" • ")
                        )
                    }
                    if (c.relationship.isNotBlank()) {
                        Text("Relationship: ${c.relationship}")
                    }
                    if (c.personality.isNotBlank()) {
                        Text(c.personality, maxLines = 2)
                    }
                }
            }
        }
    }

    if (adding) {
        campaign?.let {
            CharacterEditorDialog(
                CharacterEntity(campaignId = it.id, name = ""),
                true,
                { adding = false },
                {
                    vm.addCharacter(it)
                    adding = false
                },
                null
            )
        }
    }

    editing?.let { c ->
        CharacterEditorDialog(
            c,
            false,
            { editing = null },
            {
                vm.updateCharacter(it)
                editing = null
            },
            {
                vm.deleteCharacter(c)
                editing = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CharacterEditorDialog(
    initial: CharacterEntity,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (CharacterEntity) -> Unit,
    onDelete: (() -> Unit)?
) {
    var c by remember(initial) { mutableStateOf(initial) }
    var tierMenu by remember { mutableStateOf(false) }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            Modifier.fillMaxWidth(.96f).fillMaxHeight(.94f),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (isNew) "New Character" else c.name.ifBlank { "Character Sheet" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }

                Column(
                    Modifier.weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box {
                        OutlinedButton(onClick = { tierMenu = true }) {
                            Icon(Icons.Default.Star, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Cast Tier: ${c.castTier}")
                        }
                        DropdownMenu(
                            expanded = tierMenu,
                            onDismissRequest = { tierMenu = false }
                        ) {
                            listOf("Main", "Secondary", "Supporting", "Background").forEach { tier ->
                                DropdownMenuItem(
                                    text = { Text(tier) },
                                    onClick = {
                                        c = c.copy(castTier = tier)
                                        tierMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Text(
                        when (c.castTier) {
                            "Main" -> "Highest continuity priority. Chronicle closely tracks meaningful changes."
                            "Secondary" -> "Important recurring cast. Chronicle tracks durable meaningful changes."
                            "Supporting" -> "Arc-relevant cast. Chronicle filters minor details."
                            else -> "Background impact gate active. Character proposals require major impact."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )

                    Field("Name", c.name) { c = c.copy(name = it) }
                    Field("Aliases", c.aliases) { c = c.copy(aliases = it) }
                    Field("Species / race", c.species) { c = c.copy(species = it) }
                    Field("Age", c.age) { c = c.copy(age = it) }
                    Field("Pronouns", c.pronouns) { c = c.copy(pronouns = it) }
                    Field("Status", c.status) { c = c.copy(status = it) }
                    Field("Appearance", c.appearance, 3) { c = c.copy(appearance = it) }
                    Field("Personality", c.personality, 3) { c = c.copy(personality = it) }
                    Field("Backstory", c.backstory, 4) { c = c.copy(backstory = it) }
                    Field("Abilities / powers", c.abilities, 3) { c = c.copy(abilities = it) }
                    Field("Equipment / inventory", c.equipment, 3) { c = c.copy(equipment = it) }
                    Field("Relationship", c.relationship, 2) { c = c.copy(relationship = it) }
                    Field("Affiliations / factions", c.affiliations, 2) { c = c.copy(affiliations = it) }
                    Field("Goals / motivations", c.goals, 3) { c = c.copy(goals = it) }
                    Field("Fears / vulnerabilities", c.fears, 3) { c = c.copy(fears = it) }
                    Field("Secrets", c.secrets, 3) { c = c.copy(secrets = it) }
                    Field("Injuries / conditions", c.injuries, 3) { c = c.copy(injuries = it) }
                    Field("Additional notes", c.notes, 4) { c = c.copy(notes = it) }
                    Text(
                        "Internal ID: ${if (c.id == 0L) "assigned when saved" else c.id}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (!isNew && onDelete != null) {
                        TextButton(onClick = onDelete) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = { onSave(c) },
                        enabled = c.name.isNotBlank()
                    ) {
                        Text("Save character")
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    min: Int = 1,
    on: (String) -> Unit
) {
    OutlinedTextField(
        value,
        on,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        minLines = min,
        maxLines = if (min == 1) 2 else min + 3
    )
}

@Composable
private fun CampaignEditorDialog(
    campaign: CampaignEntity,
    onDismiss: () -> Unit,
    onSave: (CampaignEntity) -> Unit
) {
    var c by remember(campaign) { mutableStateOf(campaign) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit campaign") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Field("Campaign name", c.name) { c = c.copy(name = it) }
                Field("Description", c.description, 2) { c = c.copy(description = it) }
                Field("World / setting", c.setting, 2) { c = c.copy(setting = it) }
                Field("Genre / tone", c.genreTone, 2) { c = c.copy(genreTone = it) }
                Field("Current location", c.currentLocation) {
                    c = c.copy(currentLocation = it)
                }
                Field("Current objective", c.currentObjective, 2) {
                    c = c.copy(currentObjective = it)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(c) },
                enabled = c.name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ArchivedCampaignsDialog(
    campaigns: List<CampaignEntity>,
    onDismiss: () -> Unit,
    onRestore: (CampaignEntity) -> Unit,
    onDelete: (CampaignEntity) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Archived campaigns") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(campaigns, key = { it.id }) { c ->
                    ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(c.name, fontWeight = FontWeight.Bold)
                            Row {
                                TextButton(onClick = { onRestore(c) }) { Text("Restore") }
                                TextButton(onClick = { onDelete(c) }) {
                                    Text("Delete", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun CreateCampaignDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var n by remember { mutableStateOf("") }
    var d by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New campaign") },
        text = {
            Column {
                OutlinedTextField(n, { n = it }, label = { Text("Campaign name") })
                OutlinedTextField(d, { d = it }, label = { Text("Description") })
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(n, d) },
                enabled = n.isNotBlank()
            ) { Text("Create") }
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
    var cat by remember { mutableStateOf("Canon") }
    var t by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add memory") },
        text = {
            Column {
                OutlinedTextField(cat, { cat = it }, label = { Text("Category") })
                OutlinedTextField(t, { t = it }, label = { Text("Title") })
                OutlinedTextField(
                    c,
                    { c = it },
                    label = { Text("Memory") },
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(t, c, cat) },
                enabled = t.isNotBlank() && c.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExternalImportReviewDialog(
    draft: com.akaroai.chronicle.data.ExternalImportDraft,
    onDismiss: () -> Unit,
    onImport: (com.akaroai.chronicle.data.ExternalImportDraft) -> Unit
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            Modifier.fillMaxWidth(.96f).fillMaxHeight(.92f),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.padding(18.dp)) {
                    Text("Import Review", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Chronicle has not written any of this to canon yet. Review the extracted draft first.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text(draft.campaignName, fontWeight = FontWeight.Bold)
                                if (draft.description.isNotBlank()) Text(draft.description)
                                if (draft.setting.isNotBlank()) Text("Setting: ${draft.setting}")
                                if (draft.currentLocation.isNotBlank()) Text("Current location: ${draft.currentLocation}")
                                if (draft.currentObjective.isNotBlank()) Text("Objective: ${draft.currentObjective}")
                            }
                        }
                    }

                    item {
                        Text(
                            "Characters (${draft.characters.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(draft.characters) { c ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(c.name, fontWeight = FontWeight.Bold)
                                    Text(c.confidence, style = MaterialTheme.typography.labelSmall)
                                }
                                Text("${c.castTier}${if (c.species.isNotBlank()) " • ${c.species}" else ""}")
                                if (c.personality.isNotBlank()) Text(c.personality, maxLines = 3)
                            }
                        }
                    }

                    item {
                        Text(
                            "Memories / Lore (${draft.memories.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(draft.memories) { m ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${m.category} • ${m.title}", fontWeight = FontWeight.Bold)
                                    Text(m.confidence, style = MaterialTheme.typography.labelSmall)
                                }
                                Text(m.content, maxLines = 5)
                            }
                        }
                    }

                    item {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Transcript preservation", fontWeight = FontWeight.Bold)
                                Text(
                                    if (draft.messages.isNotEmpty())
                                        "${draft.messages.size} speaker-prefixed chat messages detected and ready to restore into Chat."
                                    else
                                        "No reliable User:/Assistant:/GM: transcript structure detected. The original source will be preserved as an Imported Source memory instead."
                                )
                            }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onImport(draft) }) {
                        Text("Approve & Create")
                    }
                }
            }
        }
    }
}
