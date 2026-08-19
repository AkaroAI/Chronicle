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
fun TimelineTab(vm: ChronicleViewModel) {
    val events by vm.timelineEvents.collectAsState()
    var filter by remember { mutableStateOf("All") }
    val visible = if (filter == "All") events else events.filter { it.importance == filter }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(12.dp)) {
            Text("Story Timeline", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Approved milestones only — major discoveries, battles, decisions, relationships, quests, and world changes.",
                style = MaterialTheme.typography.bodySmall
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("All","Critical","High","Normal")) { x ->
                    FilterChip(selected = filter == x, onClick = { filter = x }, label = { Text(x) })
                }
            }
        }

        if (visible.isEmpty()) {
            Column(Modifier.padding(24.dp)) {
                Icon(Icons.Default.History, null)
                Spacer(Modifier.height(8.dp))
                Text("No approved timeline events yet.")
                Text("Meaningful story beats will appear here after Review approval.", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(visible, key={_, e -> e.id}) { index, e ->
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.width(34.dp)) {
                            Icon(
                                when(e.eventType) {
                                    "Battle" -> Icons.Default.LocalFireDepartment
                                    "Discovery" -> Icons.Default.Explore
                                    "Relationship" -> Icons.Default.Favorite
                                    "Quest" -> Icons.Default.Flag
                                    "World Change" -> Icons.Default.Public
                                    else -> Icons.Default.AutoStories
                                },
                                null
                            )
                        }
                        ElevatedCard(Modifier.weight(1f)) {
                            Column(Modifier.padding(14.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(e.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    AssistChip(onClick={}, label={Text(e.importance)})
                                }
                                Text(e.summary)
                                val meta = listOf(e.eventType, e.storyArc, e.location).filter { it.isNotBlank() }
                                if (meta.isNotEmpty()) Text(meta.joinToString(" • "), style = MaterialTheme.typography.labelSmall)
                                if (e.involvedCharacters.isNotBlank()) {
                                    Text("Characters: ${e.involvedCharacters}", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
