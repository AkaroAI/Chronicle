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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akaroai.chronicle.model.*

enum class ChronicleTab(val label: String) {
    CHAT("Chat"),
    REVIEW("Review"),
    CHARACTERS("Characters"),
    WORLD("World"),
    QUESTS("Quests"),
    TIMELINE("Timeline"),
    MEMORY("Lore & Memory")
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
    var campaignMenu by remember { mutableStateOf(false) }

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
            val mood = if (
                it.contains("connect", true) || it.contains("offline", true) || it.contains("timed out", true)
            ) SpiritMood.OFFLINE else SpiritMood.ERROR
            snack.showSnackbar(ChronicleSpiritVisuals(it, mood, actionLabel = "Details"))
            vm.clearError()
        }
    }
    LaunchedEffect(notice) {
        notice?.let {
            val mood = when {
                it.contains("Review", true) || it.contains("suggestion", true) -> SpiritMood.INFO
                it.contains("waiting", true) || it.contains("paused", true) -> SpiritMood.WARNING
                else -> SpiritMood.SUCCESS
            }
            snack.showSnackbar(ChronicleSpiritVisuals(it, mood))
            vm.clearNotice()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = {
            SnackbarHost(snack) { ChronicleSpiritSnackbar(it) }
        },
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        Surface(
                            modifier = Modifier.clickable { campaignMenu = true },
                            shape = RoundedCornerShape(18.dp),
                            color = ChronicleColors.Surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, ChronicleColors.Lavender.copy(alpha = .35f))
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(selected?.name ?: "Choose a campaign", fontWeight = FontWeight.Bold)
                                    Text("Chronicle", style = MaterialTheme.typography.labelSmall, color = ChronicleColors.MutedInk)
                                }
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Default.ExpandMore, "Switch campaign")
                            }
                        }
                        DropdownMenu(campaignMenu, { campaignMenu = false }) {
                            campaigns.forEach { campaign ->
                                DropdownMenuItem(
                                    text = { Text(campaign.name) },
                                    onClick = { vm.selectCampaign(campaign.id); campaignMenu = false },
                                    leadingIcon = { if (campaign.id == selected?.id) Icon(Icons.Default.Check, null) }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("New campaign") },
                                onClick = { campaignMenu = false; create = true },
                                leadingIcon = { Icon(Icons.Default.Add, null) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ChronicleColors.Void.copy(alpha = .58f)),
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
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(end = 64.dp)) {
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
                    ChronicleTab.WORLD -> WorldTab(vm)
                    ChronicleTab.QUESTS -> QuestsTab(vm)
                    ChronicleTab.TIMELINE -> TimelineTab(vm)
                    ChronicleTab.REVIEW -> ReviewTab(vm)
                }
            }
        }
        LivingNavigationRail(
            selected = tab,
            proposalCount = proposals.size,
            onSelect = { tab = it },
            modifier = Modifier.align(Alignment.CenterEnd)
        )
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
    val characters by vm.characters.collectAsState()
    val gen by vm.isGenerating.collectAsState()
    val scanning by vm.isReviewScanning.collectAsState()
    val phase by vm.turnPhase.collectAsState()
    var input by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("Story") }
    var actor by remember { mutableStateOf("Player") }
    var intent by remember { mutableStateOf("Action") }
    var target by remember { mutableStateOf("Scene") }

    Column(Modifier.fillMaxSize()) {
        Text(
            when (phase) {
                "ANALYZING_CANON" -> "First look • checking your message for canon changes…"
                "GENERATING_DRAFT" -> "Chronicle is shaping a draft…"
                "POST_STORY_SCAN" -> "Second look • checking the completed scene…"
                "AWAITING_REVIEW" -> "Paused • your changes are waiting in Review"
                "AWAITING_RESPONSE_REVIEW" -> "Draft paused • review its proposed canon before publishing"
                "REGENERATING_FROM_CANON" -> "Rewriting from your approved canon…"
                "DM_CONVERSATION" -> "Talking privately with your DM • non-canonical"
                else -> when {
                gen -> "Chronicle is writing…"
                scanning -> "Reply complete • checking Review Inbox…"
                real -> "AI enabled • campaign-isolated context"
                else -> "Demo mode • tap ⚙ to connect"
                }
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

        Surface(
            Modifier.fillMaxWidth().padding(10.dp),
            shape = RoundedCornerShape(24.dp),
            color = ChronicleColors.Surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, ChronicleColors.Lavender.copy(alpha = .38f)),
            shadowElevation = 12.dp
        ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { RouteSelector(actor, listOf("Player") + characters.map { it.name }, { actor = it }, Icons.Default.Person) }
                item { RouteSelector(intent, listOf("Talking to", "Action", "Observing", "Thinking"), { intent = it }, Icons.Default.Bolt) }
                item {
                    RouteSelector(target, listOf("Scene", "DM") + characters.map { it.name }, {
                        target = it
                        if (it == "DM") { mode = "DM"; intent = "Talking to" }
                    }, Icons.Default.NearMe)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                input,
                { input = it },
                Modifier.weight(1f),
                placeholder = { Text(if (mode == "DM") "Talk with your DM…" else "Continue the story…") },
                maxLines = 5
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = {
                    val t = input
                    input = ""
                    vm.sendMessage(t, mode, actor, intent, target)
                },
                enabled = input.isNotBlank() && !gen
            ) {
                Icon(Icons.Default.Send, "Send")
            }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Story", "DM", "Command").forEach { item ->
                    FilterChip(
                        selected = mode == item,
                        onClick = {
                            mode = item
                            if (item == "DM") { actor = "Player"; target = "DM"; intent = "Talking to" }
                            else if (target == "DM") target = "Scene"
                        },
                        label = { Text(item) }
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun RouteSelector(value: String, options: List<String>, onSelect: (String) -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(onClick = { expanded = true }, label = { Text(value) }, leadingIcon = { Icon(icon, null) })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.distinct().forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

@Composable
private fun MessageBubble(m: MessageEntity) {
    val u = m.role == "user"
    val route = m.content.lineSequence().firstOrNull()?.takeIf { it.startsWith("[") && it.endsWith("]") }
    val displayContent = ChatRouting.visibleContent(m.content)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (u) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(.9f),
            shape = RoundedCornerShape(22.dp),
            color = if (u) ChronicleColors.Violet.copy(alpha = .72f) else ChronicleColors.Surface.copy(alpha = .88f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (u) ChronicleColors.Cyan.copy(alpha = .34f) else ChronicleColors.Lavender.copy(alpha = .28f)
            ),
            shadowElevation = 10.dp
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(if (u) "You" else "Chronicle", fontWeight = FontWeight.Bold)
                route?.let { Text(it.removeSurrounding("[", "]"), color = ChronicleColors.Cyan, style = MaterialTheme.typography.labelSmall) }
                Text(displayContent)
            }
        }
    }
}

@Composable
private fun MemoryTab(vm: ChronicleViewModel) {
    val mem by vm.memories.collectAsState()
    var add by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    val categories = listOf("All") + mem.map { it.category }.distinct().sorted()
    val visible = mem.filter {
        (category == "All" || it.category == category) &&
            (query.isBlank() || it.title.contains(query, true) || it.content.contains(query, true))
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Lore & Memory", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("The campaign's approved source of truth", color = ChronicleColors.MutedInk)
            }
            FilledTonalIconButton(onClick = { add = true }) { Icon(Icons.Default.Add, "Add memory") }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("Search approved lore…") },
            shape = RoundedCornerShape(18.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
            items(categories) { item ->
                FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) })
            }
        }
        if (visible.isEmpty()) {
            ChronicleEmptyState(
                if (mem.isEmpty()) "No campaign lore established yet." else "Nothing matches this view.",
                if (mem.isEmpty()) "Approved memories and canon will appear here." else "Try another search or category."
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(visible, key = { it.id }) { m ->
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = ChronicleColors.Surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ChronicleColors.Lavender.copy(alpha = .28f)),
                    shadowElevation = 8.dp
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(m.category, color = ChronicleColors.Cyan, style = MaterialTheme.typography.labelMedium)
                        Text(m.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(5.dp))
                        Text(m.content)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { vm.deleteMemory(m) }) { Text("Remove") }
                        }
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
    var opened by remember { mutableStateOf<CharacterEntity?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Characters", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Open a name to view its canonical book", color = ChronicleColors.MutedInk)
            }
            FilledTonalIconButton(onClick = { adding = true }) { Icon(Icons.Default.Add, "Add character") }
        }
        Spacer(Modifier.height(16.dp))
        if (chars.isEmpty()) {
            ChronicleEmptyState("No characters established yet.", "Approved character proposals will appear here.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(chars, key = { it.id }) { c ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { opened = c },
                        shape = RoundedCornerShape(20.dp),
                        color = ChronicleColors.Surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ChronicleColors.Lavender.copy(alpha = .3f)),
                        shadowElevation = 8.dp
                    ) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(c.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Icon(Icons.Default.ChevronRight, "Open ${c.name}", tint = ChronicleColors.Cyan)
                        }
                    }
                }
            }
        }
    }

    opened?.let { character ->
        CharacterBookDialog(
            character = character,
            onDismiss = { opened = null },
            onEdit = { opened = null; editing = character }
        )
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
private fun CharacterBookDialog(character: CharacterEntity, onDismiss: () -> Unit, onEdit: () -> Unit) {
    val tabs = listOf("Overview", "Lore & Backstory", "Skills", "Relationships", "Status Effects", "Equipment", "Notes")
    var selectedTab by remember(character.id) { mutableIntStateOf(0) }
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            Modifier.fillMaxWidth(.98f).fillMaxHeight(.94f),
            shape = RoundedCornerShape(28.dp),
            color = ChronicleColors.DeepNavy,
            border = androidx.compose.foundation.BorderStroke(1.dp, ChronicleColors.Lavender.copy(alpha = .55f)),
            shadowElevation = 24.dp
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, "Characters") }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(character.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("Approved Canon", color = ChronicleColors.Cyan, style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit character") }
                }
                ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 8.dp, containerColor = ChronicleColors.Surface) {
                    tabs.forEachIndexed { index, title ->
                        Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                    }
                }
                Surface(
                    Modifier.fillMaxSize().padding(14.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = ChronicleColors.Surface.copy(alpha = .78f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ChronicleColors.Lavender.copy(alpha = .25f))
                ) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        when (selectedTab) {
                            0 -> {
                                BookField("Identity", listOf(character.species, character.age, character.pronouns).filter { it.isNotBlank() }.joinToString(" • "))
                                BookField("Appearance", character.appearance)
                                BookField("Personality", character.personality)
                                BookField("Goals", character.goals)
                            }
                            1 -> { BookField("Lore", ""); BookField("Backstory", character.backstory); BookField("Secrets", character.secrets) }
                            2 -> BookField("Skills & Abilities", character.abilities)
                            3 -> { BookField("Relationships", character.relationship); BookField("Affiliations", character.affiliations) }
                            4 -> { BookField("Status", character.status); BookField("Injuries & Conditions", character.injuries); BookField("Fears & Vulnerabilities", character.fears) }
                            5 -> BookField("Equipment", character.equipment)
                            else -> BookField("Notes", character.notes)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookField(title: String, value: String) {
    Column {
        Text(title, color = ChronicleColors.Lavender, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text(value.ifBlank { "Not established yet." }, color = if (value.isBlank()) ChronicleColors.MutedInk else MaterialTheme.colorScheme.onSurface)
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
    var working by remember(draft) { mutableStateOf(draft) }
    var filter by remember { mutableStateOf("All") }
    var editCampaign by remember { mutableStateOf(false) }
    var editingCharacterIndex by remember { mutableStateOf<Int?>(null) }
    var editingMemoryIndex by remember { mutableStateOf<Int?>(null) }
    var editingLocationIndex by remember { mutableStateOf<Int?>(null) }
    var editingFactionIndex by remember { mutableStateOf<Int?>(null) }
    var editingQuestIndex by remember { mutableStateOf<Int?>(null) }
    var editingTimelineIndex by remember { mutableStateOf<Int?>(null) }

    val allConfidence =
        working.characters.map { it.confidence } +
        working.memories.map { it.confidence } +
        working.locations.map { it.confidence } +
        working.factions.map { it.confidence } +
        working.quests.map { it.confidence } +
        working.timelineEvents.map { it.confidence }
    val highCount = allConfidence.count { it == "High confidence" }
    val reviewCount = allConfidence.count { it == "Needs review" }
    val ambiguousCount = allConfidence.count { it == "Ambiguous" }

    fun visible(confidence: String): Boolean = when (filter) {
        "Needs Review" -> confidence == "Needs review"
        "Ambiguous" -> confidence == "Ambiguous"
        else -> true
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            Modifier.fillMaxWidth(.96f).fillMaxHeight(.94f),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "Import Review",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Nothing here becomes canon until you approve it. Edit or remove anything Chronicle misunderstood.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "${working.characters.size} Characters • ${working.locations.size} Locations • ${working.factions.size} Factions • ${working.quests.size} Quests • ${working.timelineEvents.size} Timeline • ${working.memories.size} Memories • ${working.messages.size} Messages",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "$highCount High confidence • $reviewCount Needs review • $ambiguousCount Ambiguous",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                LazyRow(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(listOf("All", "Needs Review", "Ambiguous")) { option ->
                        FilterChip(
                            selected = filter == option,
                            onClick = { filter = option },
                            label = { Text(option) }
                        )
                    }
                }

                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        working.campaignName,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    TextButton(onClick = { editCampaign = true }) {
                                        Icon(Icons.Default.Edit, null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Edit")
                                    }
                                }
                                if (working.description.isNotBlank()) Text(working.description)
                                if (working.setting.isNotBlank()) Text("Setting: ${working.setting}")
                                if (working.genreTone.isNotBlank()) Text("Tone: ${working.genreTone}")
                                if (working.currentLocation.isNotBlank()) {
                                    Text("Current location: ${working.currentLocation}")
                                }
                                if (working.currentObjective.isNotBlank()) {
                                    Text("Objective: ${working.currentObjective}")
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            "Characters (${working.characters.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    itemsIndexed(working.characters) { index, c ->
                        if (visible(c.confidence)) {
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(c.name, fontWeight = FontWeight.Bold)
                                            Text(
                                                c.confidence,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                        IconButton(onClick = { editingCharacterIndex = index }) {
                                            Icon(Icons.Default.Edit, "Edit character")
                                        }
                                        IconButton(
                                            onClick = {
                                                working = working.copy(
                                                    characters = working.characters.filterIndexed { i, _ ->
                                                        i != index
                                                    }
                                                )
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                "Remove character",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }

                                    Text(
                                        "${c.castTier}${if (c.species.isNotBlank()) " • ${c.species}" else ""}"
                                    )
                                    if (c.relationship.isNotBlank()) {
                                        Text("Relationship: ${c.relationship}")
                                    }
                                    if (c.personality.isNotBlank()) {
                                        Text(c.personality, maxLines = 3)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            "Memories / Lore (${working.memories.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    itemsIndexed(working.memories) { index, m ->
                        if (visible(m.confidence)) {
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                "${m.category} • ${m.title}",
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                m.confidence,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                        IconButton(onClick = { editingMemoryIndex = index }) {
                                            Icon(Icons.Default.Edit, "Edit memory")
                                        }
                                        IconButton(
                                            onClick = {
                                                working = working.copy(
                                                    memories = working.memories.filterIndexed { i, _ ->
                                                        i != index
                                                    }
                                                )
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                "Remove memory",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                    Text(m.content, maxLines = 5)
                                }
                            }
                        }
                    }

                    item {
                        Text("World Locations (${working.locations.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    itemsIndexed(working.locations) { index, l ->
                        if (visible(l.confidence)) {
                            ImportWorldCard(
                                title = l.name,
                                subtitle = listOf(l.region, l.discoveryState, l.status).filter { it.isNotBlank() }.joinToString(" • "),
                                body = l.description,
                                confidence = l.confidence,
                                onEdit = { editingLocationIndex = index },
                                onDelete = { working = working.copy(locations = working.locations.filterIndexed { i, _ -> i != index }) }
                            )
                        }
                    }

                    item {
                        Text("Factions (${working.factions.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    itemsIndexed(working.factions) { index, f ->
                        if (visible(f.confidence)) {
                            ImportWorldCard(
                                title = f.name,
                                subtitle = listOf(f.relationshipToParty, f.status).filter { it.isNotBlank() }.joinToString(" • "),
                                body = f.description.ifBlank { f.goals },
                                confidence = f.confidence,
                                onEdit = { editingFactionIndex = index },
                                onDelete = { working = working.copy(factions = working.factions.filterIndexed { i, _ -> i != index }) }
                            )
                        }
                    }

                    item {
                        Text("Quests / Story Threads (${working.quests.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    itemsIndexed(working.quests) { index, q ->
                        if (visible(q.confidence)) {
                            ImportWorldCard(
                                title = q.title,
                                subtitle = listOf(q.status, q.importance).filter { it.isNotBlank() }.joinToString(" • "),
                                body = q.objective.ifBlank { q.summary },
                                confidence = q.confidence,
                                onEdit = { editingQuestIndex = index },
                                onDelete = { working = working.copy(quests = working.quests.filterIndexed { i, _ -> i != index }) }
                            )
                        }
                    }

                    item {
                        Text("Timeline Events (${working.timelineEvents.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    itemsIndexed(working.timelineEvents) { index, e ->
                        if (visible(e.confidence)) {
                            ImportWorldCard(
                                title = e.title,
                                subtitle = listOf(e.eventType, e.importance, e.location).filter { it.isNotBlank() }.joinToString(" • "),
                                body = e.summary,
                                confidence = e.confidence,
                                onEdit = { editingTimelineIndex = index },
                                onDelete = { working = working.copy(timelineEvents = working.timelineEvents.filterIndexed { i, _ -> i != index }) }
                            )
                        }
                    }

                    item {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Transcript preservation", fontWeight = FontWeight.Bold)
                                Text(
                                    if (working.messages.isNotEmpty())
                                        "${working.messages.size} speaker-prefixed chat messages will be restored into Chat."
                                    else
                                        "No reliable speaker structure was detected. The original source will be preserved as an Imported Source memory."
                                )
                            }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = { onImport(working) },
                        enabled = working.campaignName.isNotBlank()
                    ) {
                        Text("Approve & Create")
                    }
                }
            }
        }
    }

    if (editCampaign) {
        ExternalCampaignDraftEditor(
            draft = working,
            onDismiss = { editCampaign = false },
            onSave = {
                working = it
                editCampaign = false
            }
        )
    }

    editingCharacterIndex?.let { index ->
        working.characters.getOrNull(index)?.let { character ->
            ExternalCharacterDraftEditor(
                character = character,
                onDismiss = { editingCharacterIndex = null },
                onSave = { updated ->
                    working = working.copy(
                        characters = working.characters.toMutableList().also {
                            if (index in it.indices) it[index] = updated
                        }
                    )
                    editingCharacterIndex = null
                }
            )
        }
    }

    editingMemoryIndex?.let { index ->
        working.memories.getOrNull(index)?.let { memory ->
            ExternalMemoryDraftEditor(
                memory = memory,
                onDismiss = { editingMemoryIndex = null },
                onSave = { updated ->
                    working = working.copy(
                        memories = working.memories.toMutableList().also {
                            if (index in it.indices) it[index] = updated
                        }
                    )
                    editingMemoryIndex = null
                }
            )
        }
    }

    editingLocationIndex?.let { index ->
        working.locations.getOrNull(index)?.let { item ->
            ExternalLocationDraftEditor(item, { editingLocationIndex = null }) { updated ->
                working = working.copy(locations = working.locations.toMutableList().also { if (index in it.indices) it[index] = updated })
                editingLocationIndex = null
            }
        }
    }
    editingFactionIndex?.let { index ->
        working.factions.getOrNull(index)?.let { item ->
            ExternalFactionDraftEditor(item, { editingFactionIndex = null }) { updated ->
                working = working.copy(factions = working.factions.toMutableList().also { if (index in it.indices) it[index] = updated })
                editingFactionIndex = null
            }
        }
    }
    editingQuestIndex?.let { index ->
        working.quests.getOrNull(index)?.let { item ->
            ExternalQuestDraftEditor(item, { editingQuestIndex = null }) { updated ->
                working = working.copy(quests = working.quests.toMutableList().also { if (index in it.indices) it[index] = updated })
                editingQuestIndex = null
            }
        }
    }
    editingTimelineIndex?.let { index ->
        working.timelineEvents.getOrNull(index)?.let { item ->
            ExternalTimelineDraftEditor(item, { editingTimelineIndex = null }) { updated ->
                working = working.copy(timelineEvents = working.timelineEvents.toMutableList().also { if (index in it.indices) it[index] = updated })
                editingTimelineIndex = null
            }
        }
    }
}

@Composable
private fun ExternalCampaignDraftEditor(
    draft: com.akaroai.chronicle.data.ExternalImportDraft,
    onDismiss: () -> Unit,
    onSave: (com.akaroai.chronicle.data.ExternalImportDraft) -> Unit
) {
    var name by remember(draft) { mutableStateOf(draft.campaignName) }
    var description by remember(draft) { mutableStateOf(draft.description) }
    var setting by remember(draft) { mutableStateOf(draft.setting) }
    var tone by remember(draft) { mutableStateOf(draft.genreTone) }
    var location by remember(draft) { mutableStateOf(draft.currentLocation) }
    var objective by remember(draft) { mutableStateOf(draft.currentObjective) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit campaign draft") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Field("Campaign name", name) { name = it }
                Field("Description", description, 2) { description = it }
                Field("World / setting", setting, 2) { setting = it }
                Field("Genre / tone", tone, 2) { tone = it }
                Field("Current location", location) { location = it }
                Field("Current objective", objective, 2) { objective = it }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        draft.copy(
                            campaignName = name.trim(),
                            description = description.trim(),
                            setting = setting.trim(),
                            genreTone = tone.trim(),
                            currentLocation = location.trim(),
                            currentObjective = objective.trim()
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExternalCharacterDraftEditor(
    character: com.akaroai.chronicle.data.ImportedCharacterDraft,
    onDismiss: () -> Unit,
    onSave: (com.akaroai.chronicle.data.ImportedCharacterDraft) -> Unit
) {
    var c by remember(character) { mutableStateOf(character) }
    var tierMenu by remember { mutableStateOf(false) }
    var confidenceMenu by remember { mutableStateOf(false) }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            Modifier.fillMaxWidth(.96f).fillMaxHeight(.92f),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    "Edit imported character",
                    Modifier.padding(18.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Column(
                    Modifier.weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box {
                            OutlinedButton(onClick = { tierMenu = true }) {
                                Text("Cast: ${c.castTier}")
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

                        Box {
                            OutlinedButton(onClick = { confidenceMenu = true }) {
                                Text(c.confidence)
                            }
                            DropdownMenu(
                                expanded = confidenceMenu,
                                onDismissRequest = { confidenceMenu = false }
                            ) {
                                listOf("High confidence", "Needs review", "Ambiguous").forEach { value ->
                                    DropdownMenuItem(
                                        text = { Text(value) },
                                        onClick = {
                                            c = c.copy(confidence = value)
                                            confidenceMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Field("Name", c.name) { c = c.copy(name = it) }
                    Field("Species / race", c.species) { c = c.copy(species = it) }
                    Field("Age", c.age) { c = c.copy(age = it) }
                    Field("Pronouns", c.pronouns) { c = c.copy(pronouns = it) }
                    Field("Status", c.status) { c = c.copy(status = it) }
                    Field("Appearance", c.appearance, 3) { c = c.copy(appearance = it) }
                    Field("Personality", c.personality, 3) { c = c.copy(personality = it) }
                    Field("Backstory", c.backstory, 4) { c = c.copy(backstory = it) }
                    Field("Abilities / powers", c.abilities, 3) { c = c.copy(abilities = it) }
                    Field("Equipment", c.equipment, 3) { c = c.copy(equipment = it) }
                    Field("Relationship", c.relationship, 3) { c = c.copy(relationship = it) }
                    Field("Affiliations", c.affiliations, 2) { c = c.copy(affiliations = it) }
                    Field("Goals / motivations", c.goals, 3) { c = c.copy(goals = it) }
                    Field("Fears / vulnerabilities", c.fears, 3) { c = c.copy(fears = it) }
                    Field("Secrets", c.secrets, 3) { c = c.copy(secrets = it) }
                    Field("Injuries / conditions", c.injuries, 3) { c = c.copy(injuries = it) }
                    Field("Notes", c.notes, 3) { c = c.copy(notes = it) }
                }

                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = { onSave(c.copy(name = c.name.trim())) },
                        enabled = c.name.isNotBlank()
                    ) { Text("Save character") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExternalMemoryDraftEditor(
    memory: com.akaroai.chronicle.data.ImportedMemoryDraft,
    onDismiss: () -> Unit,
    onSave: (com.akaroai.chronicle.data.ImportedMemoryDraft) -> Unit
) {
    var m by remember(memory) { mutableStateOf(memory) }
    var confidenceMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit imported memory") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Field("Category", m.category) { m = m.copy(category = it) }
                Field("Title", m.title) { m = m.copy(title = it) }
                Field("Content", m.content, 5) { m = m.copy(content = it) }

                Box {
                    OutlinedButton(onClick = { confidenceMenu = true }) {
                        Text("Confidence: ${m.confidence}")
                    }
                    DropdownMenu(
                        expanded = confidenceMenu,
                        onDismissRequest = { confidenceMenu = false }
                    ) {
                        listOf("High confidence", "Needs review", "Ambiguous").forEach { value ->
                            DropdownMenuItem(
                                text = { Text(value) },
                                onClick = {
                                    m = m.copy(confidence = value)
                                    confidenceMenu = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = m.title.isNotBlank() && m.content.isNotBlank(),
                onClick = {
                    onSave(
                        m.copy(
                            category = m.category.trim().ifBlank { "Canon" },
                            title = m.title.trim(),
                            content = m.content.trim()
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}



@Composable
private fun ImportWorldCard(
    title: String,
    subtitle: String,
    body: String,
    confidence: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold)
                    if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.labelMedium)
                    Text(confidence, style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit") }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error)
                }
            }
            if (body.isNotBlank()) Text(body, maxLines = 5)
        }
    }
}

@Composable
private fun ExternalLocationDraftEditor(
    item: com.akaroai.chronicle.data.ImportedLocationDraft,
    onDismiss: () -> Unit,
    onSave: (com.akaroai.chronicle.data.ImportedLocationDraft) -> Unit
) {
    var x by remember(item) { mutableStateOf(item) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit imported location") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Name", x.name) { x = x.copy(name = it) }
                Field("Region", x.region) { x = x.copy(region = it) }
                Field("Parent location", x.parentLocation) { x = x.copy(parentLocation = it) }
                Field("Description", x.description, 3) { x = x.copy(description = it) }
                Field("Discovery state", x.discoveryState) { x = x.copy(discoveryState = it) }
                Field("Status", x.status) { x = x.copy(status = it) }
                Field("Notes", x.notes, 2) { x = x.copy(notes = it) }
            }
        },
        confirmButton = { Button(enabled = x.name.isNotBlank(), onClick = { onSave(x.copy(name = x.name.trim())) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ExternalFactionDraftEditor(
    item: com.akaroai.chronicle.data.ImportedFactionDraft,
    onDismiss: () -> Unit,
    onSave: (com.akaroai.chronicle.data.ImportedFactionDraft) -> Unit
) {
    var x by remember(item) { mutableStateOf(item) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit imported faction") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Name", x.name) { x = x.copy(name = it) }
                Field("Description", x.description, 3) { x = x.copy(description = it) }
                Field("Alignment", x.alignment) { x = x.copy(alignment = it) }
                Field("Relationship to party", x.relationshipToParty) { x = x.copy(relationshipToParty = it) }
                Field("Status", x.status) { x = x.copy(status = it) }
                Field("Goals", x.goals, 3) { x = x.copy(goals = it) }
                Field("Notes", x.notes, 2) { x = x.copy(notes = it) }
            }
        },
        confirmButton = { Button(enabled = x.name.isNotBlank(), onClick = { onSave(x.copy(name = x.name.trim())) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ExternalQuestDraftEditor(
    item: com.akaroai.chronicle.data.ImportedQuestDraft,
    onDismiss: () -> Unit,
    onSave: (com.akaroai.chronicle.data.ImportedQuestDraft) -> Unit
) {
    var x by remember(item) { mutableStateOf(item) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit imported quest") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Title", x.title) { x = x.copy(title = it) }
                Field("Summary", x.summary, 3) { x = x.copy(summary = it) }
                Field("Status", x.status) { x = x.copy(status = it) }
                Field("Objective", x.objective, 3) { x = x.copy(objective = it) }
                Field("Related location", x.relatedLocation) { x = x.copy(relatedLocation = it) }
                Field("Related faction", x.relatedFaction) { x = x.copy(relatedFaction = it) }
                Field("Importance", x.importance) { x = x.copy(importance = it) }
                Field("Notes", x.notes, 2) { x = x.copy(notes = it) }
            }
        },
        confirmButton = { Button(enabled = x.title.isNotBlank(), onClick = { onSave(x.copy(title = x.title.trim())) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ExternalTimelineDraftEditor(
    item: com.akaroai.chronicle.data.ImportedTimelineDraft,
    onDismiss: () -> Unit,
    onSave: (com.akaroai.chronicle.data.ImportedTimelineDraft) -> Unit
) {
    var x by remember(item) { mutableStateOf(item) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit imported timeline event") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Title", x.title) { x = x.copy(title = it) }
                Field("Summary", x.summary, 4) { x = x.copy(summary = it) }
                Field("Event type", x.eventType) { x = x.copy(eventType = it) }
                Field("Location", x.location) { x = x.copy(location = it) }
                Field("Involved characters", x.involvedCharacters, 2) { x = x.copy(involvedCharacters = it) }
                Field("Story arc", x.storyArc) { x = x.copy(storyArc = it) }
                Field("Importance", x.importance) { x = x.copy(importance = it) }
            }
        },
        confirmButton = {
            Button(
                enabled = x.title.isNotBlank() && x.summary.isNotBlank(),
                onClick = { onSave(x.copy(title = x.title.trim(), summary = x.summary.trim())) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
