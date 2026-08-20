package com.akaroai.chronicle.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.akaroai.chronicle.model.*
import kotlin.math.cos
import kotlin.math.sin

private enum class WorldMode { MAP, LOCATIONS, FACTIONS, QUESTS }

private data class NodePos(val x: Float, val y: Float)

@Composable
fun WorldTab(vm: ChronicleViewModel) {
    val campaign by vm.selectedCampaign.collectAsState()
    val locations by vm.locations.collectAsState()
    val factions by vm.factions.collectAsState()
    val quests by vm.quests.collectAsState()
    val timeline by vm.timelineEvents.collectAsState()
    val characters by vm.characters.collectAsState()

    var mode by remember { mutableStateOf(WorldMode.MAP) }
    var search by remember { mutableStateOf("") }
    var discovery by remember { mutableStateOf("All") }
    var selectedLocation by remember { mutableStateOf<LocationEntity?>(null) }

    val filteredLocations = locations.filter { l ->
        val searchOk = search.isBlank() ||
            l.name.contains(search, true) ||
            l.region.contains(search, true) ||
            l.description.contains(search, true)
        val discoveryOk = discovery == "All" ||
            l.discoveryState.equals(discovery, true)
        searchOk && discoveryOk && !l.discoveryState.equals("Unknown", true)
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("World Explorer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        campaign?.currentLocation?.takeIf { it.isNotBlank() }?.let { "Current location • $it" }
                            ?: "Explore approved campaign canon",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (campaign?.currentLocation?.isNotBlank() == true) {
                    Icon(Icons.Default.Place, "Current location")
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WorldMode.entries.forEach { item ->
                    FilterChip(
                        selected = mode == item,
                        onClick = { mode = item },
                        label = {
                            Text(
                                when (item) {
                                    WorldMode.MAP -> "Map"
                                    WorldMode.LOCATIONS -> "Locations (${locations.size})"
                                    WorldMode.FACTIONS -> "Factions (${factions.size})"
                                    WorldMode.QUESTS -> "Quests (${quests.count { it.status == "Active" }})"
                                }
                            )
                        },
                        leadingIcon = {
                            Icon(
                                when(item) {
                                    WorldMode.MAP -> Icons.Default.Public
                                    WorldMode.LOCATIONS -> Icons.Default.Place
                                    WorldMode.FACTIONS -> Icons.Default.Groups
                                    WorldMode.QUESTS -> Icons.Default.Flag
                                },
                                null
                            )
                        }
                    )
                }
            }
        }

        when (mode) {
            WorldMode.MAP -> {
                Column(Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        placeholder = { Text("Find a place…") }
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("All","Visited","Discovered","Heard About").forEach { state ->
                            FilterChip(
                                selected = discovery == state,
                                onClick = { discovery = state },
                                label = { Text(state) }
                            )
                        }
                    }
                    WorldMapCanvas(
                        campaignId = campaign?.id ?: 0,
                        currentLocation = campaign?.currentLocation.orEmpty(),
                        locations = filteredLocations,
                        characters = characters,
                        onSelect = { selectedLocation = it }
                    )
                }
            }
            WorldMode.LOCATIONS -> LocationList(locations) { selectedLocation = it }
            WorldMode.FACTIONS -> FactionList(factions)
            WorldMode.QUESTS -> QuestList(quests)
        }
    }

    selectedLocation?.let { location ->
        LocationDetailSheet(
            location = location,
            currentLocation = campaign?.currentLocation.orEmpty(),
            factions = factions,
            quests = quests,
            timeline = timeline,
            characters = characters,
            onDismiss = { selectedLocation = null }
        )
    }
}

@Composable
private fun WorldMapCanvas(
    campaignId: Long,
    currentLocation: String,
    locations: List<LocationEntity>,
    characters: List<CharacterEntity>,
    onSelect: (LocationEntity) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(campaignId) {
        context.getSharedPreferences("chronicle_world_map_$campaignId", Context.MODE_PRIVATE)
    }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val positions = remember(campaignId, locations.map { it.name }) {
        mutableStateMapOf<String, NodePos>().apply {
            locations.forEachIndexed { index, l ->
                val savedX = prefs.getFloat("${l.name}|x", -1f)
                val savedY = prefs.getFloat("${l.name}|y", -1f)
                if (savedX >= 0f && savedY >= 0f) {
                    put(l.name, NodePos(savedX, savedY))
                } else {
                    val count = locations.size.coerceAtLeast(1)
                    val angle = (index.toFloat() / count.toFloat()) * (Math.PI * 2.0)
                    val ring = if (index == 0) 0f else 0.32f
                    put(
                        l.name,
                        NodePos(
                            (0.5f + cos(angle).toFloat() * ring).coerceIn(.12f,.88f),
                            (0.5f + sin(angle).toFloat() * ring).coerceIn(.14f,.86f)
                        )
                    )
                }
            }
        }
    }

    if (locations.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Public, null)
                Spacer(Modifier.height(8.dp))
                Text("No discovered locations yet.", fontWeight = FontWeight.Bold)
                Text(
                    "Approved locations will reveal themselves here as the story grows.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        return
    }

    Box(
        Modifier.fillMaxSize()
            .padding(10.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f), RoundedCornerShape(22.dp))
            .onSizeChanged { canvasSize = it }
    ) {
        Canvas(Modifier.matchParentSize()) {
            val byName = locations.associateBy { it.name.lowercase() }
            locations.forEach { child ->
                val parentName = child.parentLocation.trim()
                if (parentName.isBlank()) return@forEach
                val parent = byName[parentName.lowercase()] ?: return@forEach
                val a = positions[parent.name] ?: return@forEach
                val b = positions[child.name] ?: return@forEach
                drawLine(
                    color = androidx.compose.ui.graphics.Color.Gray,
                    start = Offset(a.x * size.width, a.y * size.height),
                    end = Offset(b.x * size.width, b.y * size.height),
                    strokeWidth = 3f
                )
            }
        }

        locations.forEach { l ->
            val charactersHere = characters.filter { c ->
                latestCharacterLocation(c).equals(l.name, ignoreCase = true)
            }
            val pos = positions[l.name] ?: NodePos(.5f,.5f)
            val current = currentLocation.equals(l.name, true)
            val heard = l.discoveryState.equals("Heard About", true)
            val nodeWidth = 126.dp

            Column(
                modifier = Modifier
                    .offset(
                        x = ((canvasSize.width * pos.x).toInt().dp / context.resources.displayMetrics.density) - nodeWidth/2,
                        y = ((canvasSize.height * pos.y).toInt().dp / context.resources.displayMetrics.density) - 34.dp
                    )
                    .width(nodeWidth)
                    .alpha(if (heard) .62f else 1f)
                    .pointerInput(l.name, canvasSize) {
                        detectDragGestures(
                            onDragEnd = {
                                positions[l.name]?.let { p ->
                                    prefs.edit()
                                        .putFloat("${l.name}|x", p.x)
                                        .putFloat("${l.name}|y", p.y)
                                        .apply()
                                }
                            }
                        ) { change, drag ->
                            if (canvasSize.width > 0 && canvasSize.height > 0) {
                                val old = positions[l.name] ?: pos
                                positions[l.name] = NodePos(
                                    (old.x + drag.x / canvasSize.width).coerceIn(.08f,.92f),
                                    (old.y + drag.y / canvasSize.height).coerceIn(.08f,.92f)
                                )
                            }
                        }
                    }
                    .clickable { onSelect(l) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    tonalElevation = if (current) 8.dp else 2.dp,
                    color = if (current) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                ) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            when {
                                current -> Icons.Default.Place
                                l.discoveryState.equals("Visited", true) -> Icons.Default.Home
                                heard -> Icons.Default.Help
                                else -> Icons.Default.Place
                            },
                            null
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    l.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (l.region.isNotBlank()) {
                    Text(l.region, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                if (charactersHere.isNotEmpty()) {
                    Text(
                        "👥 " + charactersHere.take(3).joinToString(" • ") { it.name } +
                            if (charactersHere.size > 3) " +${charactersHere.size - 3}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 3.dp
        ) {
            Text(
                "Drag places to arrange • Tap for details",
                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationDetailSheet(
    location: LocationEntity,
    currentLocation: String,
    factions: List<FactionEntity>,
    quests: List<QuestEntity>,
    timeline: List<TimelineEventEntity>,
    characters: List<CharacterEntity>,
    onDismiss: () -> Unit
) {
    val relatedQuests = quests.filter {
        it.relatedLocation.equals(location.name, true) ||
            it.summary.contains(location.name, true) ||
            it.objective.contains(location.name, true)
    }
    val relatedFactions = factions.filter {
        it.description.contains(location.name, true) ||
            it.notes.contains(location.name, true) ||
            relatedQuests.any { q -> q.relatedFaction.equals(it.name, true) }
    }
    val events = timeline.filter { it.location.equals(location.name, true) }
    val charactersHere = characters.filter { c ->
        latestCharacterLocation(c).equals(location.name, ignoreCase = true)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (currentLocation.equals(location.name,true)) Icons.Default.Place else Icons.Default.Place,
                        null
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(location.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            listOf(location.region, location.discoveryState, location.status)
                                .filter { it.isNotBlank() }.joinToString(" • "),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
            if (location.description.isNotBlank()) item { Text(location.description) }
            if (location.parentLocation.isNotBlank()) item {
                Text("Within • ${location.parentLocation}", style = MaterialTheme.typography.bodySmall)
            }
            if (charactersHere.isNotEmpty()) {
                item { SectionTitle("Characters here") }
                items(charactersHere) { c ->
                    MiniWorldCard(c.name, "${c.castTier} • ${c.status}", c.relationship.ifBlank { c.notes })
                }
            }
            if (relatedQuests.isNotEmpty()) {
                item { SectionTitle("Quests") }
                items(relatedQuests) { q ->
                    MiniWorldCard(q.title, "${q.status} • ${q.importance}", q.objective.ifBlank { q.summary })
                }
            }
            if (relatedFactions.isNotEmpty()) {
                item { SectionTitle("Factions") }
                items(relatedFactions) { f ->
                    MiniWorldCard(f.name, "${f.relationshipToParty} • ${f.status}", f.goals.ifBlank { f.description })
                }
            }
            if (events.isNotEmpty()) {
                item { SectionTitle("History here") }
                items(events) { e ->
                    MiniWorldCard(e.title, "${e.eventType} • ${e.importance}", e.summary)
                }
            }
            if (location.notes.isNotBlank()) {
                item { SectionTitle("Notes") }
                item { Text(location.notes) }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun MiniWorldCard(title: String, subtitle: String, body: String) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.labelSmall)
            if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LocationList(locations: List<LocationEntity>, onSelect: (LocationEntity)->Unit) {
    if (locations.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No approved locations yet.") }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(locations, key={it.id}) { l ->
            ElevatedCard(Modifier.fillMaxWidth().clickable { onSelect(l) }) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(l.name, fontWeight = FontWeight.Bold)
                        AssistChip(onClick={onSelect(l)}, label={Text(l.discoveryState)})
                    }
                    if (l.region.isNotBlank()) Text("Region • ${l.region}", style = MaterialTheme.typography.labelMedium)
                    if (l.description.isNotBlank()) Text(l.description)
                    Text("Status • ${l.status}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun FactionList(factions: List<FactionEntity>) {
    if (factions.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No approved factions yet.") }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(factions, key={it.id}) { f ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(f.name, fontWeight = FontWeight.Bold)
                        AssistChip(onClick={}, label={Text(f.relationshipToParty)})
                    }
                    if (f.description.isNotBlank()) Text(f.description)
                    if (f.goals.isNotBlank()) Text("Goals • ${f.goals}")
                    Text("Status • ${f.status}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun QuestList(quests: List<QuestEntity>) {
    var filter by remember { mutableStateOf("Active") }
    val visible = if (filter == "All") quests else quests.filter { it.status == filter }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Active","Paused","Completed","Failed","All").forEach {
                FilterChip(selected = filter == it, onClick={filter=it}, label={Text(it)})
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (visible.isEmpty()) item { Text("No $filter quests.") }
            items(visible, key={it.id}) { q ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(q.title, fontWeight = FontWeight.Bold, modifier=Modifier.weight(1f))
                            AssistChip(onClick={}, label={Text(q.status)})
                        }
                        if (q.summary.isNotBlank()) Text(q.summary)
                        if (q.objective.isNotBlank()) Text("Objective • ${q.objective}")
                        val links=listOf(q.relatedLocation,q.relatedFaction).filter{it.isNotBlank()}
                        if(links.isNotEmpty()) Text(links.joinToString(" • "),style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}


private fun latestCharacterLocation(character: CharacterEntity): String {
    val explicit = Regex("""(?i)Currently at\s+([^.\n]+)\.""")
        .findAll(character.notes)
        .lastOrNull()
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        .orEmpty()
    if (explicit.isNotBlank()) return explicit

    // Backward-compatible fallback for older imported/manual character notes.
    val legacyText = listOf(character.notes, character.relationship, character.affiliations)
        .joinToString("\n")
    return Regex("""(?i)(?:staying at|staying in|located at|located in|remains at|remains in)\s+([^.\n]+)""")
        .findAll(legacyText)
        .lastOrNull()
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        .orEmpty()
}
