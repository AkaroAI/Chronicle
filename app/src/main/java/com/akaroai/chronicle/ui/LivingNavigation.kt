package com.akaroai.chronicle.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
    val shape = RoundedCornerShape(topStart = 30.dp, bottomStart = 30.dp)
    Box(
        modifier = modifier.width(64.dp)
            .shadow(24.dp, shape)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        ChronicleColors.SurfaceRaised.copy(alpha = .94f),
                        ChronicleColors.DeepNavy.copy(alpha = .88f)
                    )
                )
            )
            .border(BorderStroke(1.dp, ChronicleColors.Lavender.copy(alpha = .48f)), shape)
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
