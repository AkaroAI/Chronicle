package com.akaroai.chronicle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

private data class ChronicleStar(val x: Float, val y: Float, val radius: Float, val alpha: Float)

@Composable
fun LivingChronicleBackdrop(modifier: Modifier = Modifier) {
    val stars = remember {
        val random = Random(789)
        List(115) {
            ChronicleStar(random.nextFloat(), random.nextFloat(), .7f + random.nextFloat() * 2.1f, .18f + random.nextFloat() * .7f)
        }
    }
    Canvas(modifier.fillMaxSize()) {
        drawRect(
            Brush.verticalGradient(
                listOf(Color(0xFF050817), Color(0xFF090E25), Color(0xFF050817))
            )
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF6E4CCF).copy(alpha = .24f), Color.Transparent),
                center = Offset(size.width * .76f, size.height * .18f),
                radius = size.width * .72f
            ),
            radius = size.width * .72f,
            center = Offset(size.width * .76f, size.height * .18f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF176C8B).copy(alpha = .14f), Color.Transparent),
                center = Offset(size.width * .16f, size.height * .72f),
                radius = size.width * .62f
            ),
            radius = size.width * .62f,
            center = Offset(size.width * .16f, size.height * .72f)
        )
        stars.forEach { star ->
            drawCircle(
                color = if (star.radius > 2f) ChronicleColors.Lavender.copy(alpha = star.alpha) else Color.White.copy(alpha = star.alpha),
                radius = star.radius,
                center = Offset(size.width * star.x, size.height * star.y)
            )
        }
    }
}
