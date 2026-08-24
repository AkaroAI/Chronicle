package com.akaroai.chronicle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class SpiritMood { ERROR, WARNING, OFFLINE, SUCCESS, INFO }

@Composable
fun ChronicleSpiritSnackbar(data: SnackbarData) {
    val mood = if (data.visuals.actionLabel == "Details") SpiritMood.ERROR else SpiritMood.SUCCESS
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
                        if (mood == SpiritMood.SUCCESS) "Chronicle smiles" else "A spirit found a snag",
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
            modifier = Modifier.offset(x = 12.dp).size(72.dp),
            accent = accent
        )
    }
}

@Composable
private fun ChronicleSpirit(mood: SpiritMood, modifier: Modifier, accent: Color) {
    Canvas(modifier) {
        val ghost = Path().apply {
            moveTo(size.width * .18f, size.height * .72f)
            lineTo(size.width * .18f, size.height * .43f)
            cubicTo(size.width * .18f, size.height * .12f, size.width * .82f, size.height * .12f, size.width * .82f, size.height * .43f)
            lineTo(size.width * .82f, size.height * .72f)
            cubicTo(size.width * .72f, size.height * .66f, size.width * .66f, size.height * .87f, size.width * .56f, size.height * .75f)
            cubicTo(size.width * .48f, size.height * .66f, size.width * .40f, size.height * .88f, size.width * .31f, size.height * .74f)
            cubicTo(size.width * .26f, size.height * .68f, size.width * .22f, size.height * .77f, size.width * .18f, size.height * .72f)
            close()
        }
        drawPath(ghost, Color(0xFFF4F0FF))
        drawPath(ghost, accent, style = Stroke(width = 3f))
        val eyeY = size.height * .46f
        drawOval(Color(0xFF282047), Offset(size.width * .34f, eyeY), Size(size.width * .10f, size.height * .14f))
        drawOval(Color(0xFF282047), Offset(size.width * .56f, eyeY), Size(size.width * .10f, size.height * .14f))
        drawCircle(Color.White, size.width * .018f, Offset(size.width * .375f, eyeY + size.height * .025f))
        drawCircle(Color.White, size.width * .018f, Offset(size.width * .595f, eyeY + size.height * .025f))
        val mouthY = size.height * .65f
        if (mood == SpiritMood.SUCCESS) {
            drawArc(Color(0xFF42325E), 0f, 180f, false, Offset(size.width * .43f, mouthY - 8f), Size(size.width * .14f, size.height * .09f), style = Stroke(3f))
        } else {
            drawArc(Color(0xFF42325E), 180f, 180f, false, Offset(size.width * .43f, mouthY), Size(size.width * .14f, size.height * .08f), style = Stroke(3f))
        }
        drawCircle(Color(0xFFFFB5C8).copy(alpha = .7f), size.width * .035f, Offset(size.width * .29f, size.height * .63f))
        drawCircle(Color(0xFFFFB5C8).copy(alpha = .7f), size.width * .035f, Offset(size.width * .71f, size.height * .63f))
    }
}
