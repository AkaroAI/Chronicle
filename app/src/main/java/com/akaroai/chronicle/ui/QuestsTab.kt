package com.akaroai.chronicle.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun QuestsTab(vm: ChronicleViewModel) {
    val quests by vm.quests.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Quests", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Approved objectives and unresolved story threads", color = ChronicleColors.MutedInk)
        Spacer(Modifier.height(16.dp))
        if (quests.isEmpty()) {
            ChronicleEmptyState("No quests established yet.", "Approved quest proposals will appear here.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(quests, key = { it.id }) { quest ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = ChronicleColors.Surface,
                        border = BorderStroke(1.dp, ChronicleColors.Lavender.copy(alpha = .28f)),
                        shadowElevation = 8.dp
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Row {
                                    Icon(Icons.Default.Flag, null, tint = ChronicleColors.Lavender)
                                    Spacer(Modifier.width(8.dp))
                                    Text(quest.title, fontWeight = FontWeight.Bold)
                                }
                                AssistChip(onClick = {}, label = { Text(quest.status) })
                            }
                            if (quest.objective.isNotBlank()) Text(quest.objective)
                            else Text("Objective not established yet.", color = ChronicleColors.MutedInk)
                            if (quest.relatedLocation.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text("Location • ${quest.relatedLocation}", style = MaterialTheme.typography.labelMedium, color = ChronicleColors.Cyan)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChronicleEmptyState(title: String, detail: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = ChronicleColors.Surface.copy(alpha = .72f),
        border = BorderStroke(1.dp, ChronicleColors.Lavender.copy(alpha = .24f))
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(detail, color = ChronicleColors.MutedInk)
        }
    }
}
