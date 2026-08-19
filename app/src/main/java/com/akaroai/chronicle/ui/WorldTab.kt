package com.akaroai.chronicle.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun WorldTab(vm: ChronicleViewModel) {
    val locations by vm.locations.collectAsState()
    val factions by vm.factions.collectAsState()
    val quests by vm.quests.collectAsState()

    var section by remember { mutableStateOf("Locations") }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(12.dp)) {
            Text("World State", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Structured canon approved through Review. This becomes the foundation for Chronicle's future map.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("Locations","Factions","Quests")) { name ->
                    FilterChip(
                        selected = section == name,
                        onClick = { section = name },
                        label = {
                            val count = when(name) {
                                "Locations" -> locations.size
                                "Factions" -> factions.size
                                else -> quests.size
                            }
                            Text("$name ($count)")
                        }
                    )
                }
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when(section) {
                "Locations" -> {
                    if (locations.isEmpty()) item { EmptyWorld("No approved locations yet.") }
                    items(locations, key={it.id}) { l ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(l.name, fontWeight = FontWeight.Bold)
                                    AssistChip(onClick={}, label={Text(l.discoveryState)})
                                }
                                if (l.region.isNotBlank()) Text("Region: ${l.region}")
                                if (l.parentLocation.isNotBlank()) Text("Within: ${l.parentLocation}")
                                if (l.description.isNotBlank()) Text(l.description)
                                Text("Status: ${l.status}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                "Factions" -> {
                    if (factions.isEmpty()) item { EmptyWorld("No approved factions yet.") }
                    items(factions, key={it.id}) { f ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(f.name, fontWeight = FontWeight.Bold)
                                    AssistChip(onClick={}, label={Text(f.relationshipToParty)})
                                }
                                if (f.description.isNotBlank()) Text(f.description)
                                if (f.goals.isNotBlank()) Text("Goals: ${f.goals}")
                                Text("Status: ${f.status}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                else -> {
                    if (quests.isEmpty()) item { EmptyWorld("No approved quests or story threads yet.") }
                    items(quests, key={it.id}) { q ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(q.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    AssistChip(onClick={}, label={Text(q.status)})
                                }
                                if (q.summary.isNotBlank()) Text(q.summary)
                                if (q.objective.isNotBlank()) Text("Objective: ${q.objective}")
                                val links = listOf(q.relatedLocation, q.relatedFaction).filter { it.isNotBlank() }
                                if (links.isNotEmpty()) Text(links.joinToString(" • "), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyWorld(text: String) {
    Column(Modifier.fillMaxWidth().padding(24.dp)) {
        Icon(Icons.Default.Public, null)
        Spacer(Modifier.height(8.dp))
        Text(text)
        Text("Continue the story and approve meaningful world proposals in Review.", style = MaterialTheme.typography.bodySmall)
    }
}
