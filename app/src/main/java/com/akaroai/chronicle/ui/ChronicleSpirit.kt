package com.akaroai.chronicle.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akaroai.chronicle.R

enum class SpiritMood { ERROR, WARNING, OFFLINE, SUCCESS, INFO }

data class ChronicleSpiritVisuals(
    override val message: String,
    val mood: SpiritMood,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = true,
    override val duration: SnackbarDuration = SnackbarDuration.Long
) : SnackbarVisuals

@Composable
fun ChronicleSpiritSnackbar(data: SnackbarData) {
    val mood = (data.visuals as? ChronicleSpiritVisuals)?.mood
        ?: if (data.visuals.actionLabel == "Details") SpiritMood.ERROR else SpiritMood.INFO
    val accent = when (mood) {
        SpiritMood.ERROR -> ChronicleColors.Lavender
        SpiritMood.WARNING -> ChronicleColors.Amber
        SpiritMood.OFFLINE -> ChronicleColors.Cyan
        SpiritMood.SUCCESS -> ChronicleColors.Mint
        SpiritMood.INFO -> ChronicleColors.Cyan
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp).shadow(20.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            color = ChronicleColors.SurfaceRaised.copy(alpha = .97f),
            border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .75f))
        ) {
            Row(
                Modifier.padding(start = 84.dp, end = 14.dp, top = 16.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when (mood) {
                            SpiritMood.SUCCESS -> "Chronicle smiles"
                            SpiritMood.INFO -> "A curious spirit noticed something"
                            SpiritMood.WARNING -> "A watchful spirit needs you"
                            SpiritMood.OFFLINE -> "The engine spirit fell asleep"
                            SpiritMood.ERROR -> "A worried spirit found a snag"
                        },
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium)
                }
                data.visuals.actionLabel?.let { label ->
                    TextButton(onClick = { data.performAction() }) { Text(label) }
                }
            }
        }
        ChronicleSpirit(
            mood = mood,
            modifier = Modifier.offset(x = 12.dp).size(72.dp)
        )
    }
}

@Composable
private fun ChronicleSpirit(mood: SpiritMood, modifier: Modifier) {
    val art = when (mood) {
        SpiritMood.SUCCESS -> R.drawable.chronicle_spirit_happy
        SpiritMood.INFO -> R.drawable.chronicle_spirit_confused
        SpiritMood.OFFLINE -> R.drawable.chronicle_spirit_sleepy
        SpiritMood.WARNING -> R.drawable.chronicle_spirit_warning
        SpiritMood.ERROR -> R.drawable.chronicle_spirit_worried
    }
    Image(
        painter = painterResource(art),
        contentDescription = "Chronicle Spirit ${mood.name.lowercase()}",
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}
