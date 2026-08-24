package com.akaroai.chronicle.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private fun ChronicleTab.icon(): ImageVector = when (this) {
    ChronicleTab.CHAT -> Icons.Default.ChatBubbleOutline
    ChronicleTab.REVIEW -> Icons.Default.FactCheck
    ChronicleTab.CHARACTERS -> Icons.Default.Groups
    ChronicleTab.WORLD -> Icons.Default.Public
    ChronicleTab.QUESTS -> Icons.Default.Flag
    ChronicleTab.TIMELINE -> Icons.Default.Timeline
    ChronicleTab.MEMORY -> Icons.Default.AutoStories
}

@Composable
fun LivingNavigationRail(
    selected: ChronicleTab,
    proposalCount: Int,
    onSelect: (ChronicleTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.width(64.dp),
        shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
        color = ChronicleColors.Surface.copy(alpha = .96f),
        border = BorderStroke(1.dp, ChronicleColors.Lavender.copy(alpha = .35f)),
        shadowElevation = 18.dp
    ) {
        Column(
            Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            ChronicleTab.entries.forEach { item ->
                BadgedBox(
                    badge = {
                        if (item == ChronicleTab.REVIEW && proposalCount > 0) {
                            Badge { Text(if (proposalCount > 99) "99+" else proposalCount.toString()) }
                        }
                    }
                ) {
                    NavigationRailItem(
                        selected = selected == item,
                        onClick = { onSelect(item) },
                        icon = { Icon(item.icon(), item.label) },
                        alwaysShowLabel = false,
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = ChronicleColors.Ink,
                            indicatorColor = ChronicleColors.Violet
                        )
                    )
                }
            }
        }
    }
}
