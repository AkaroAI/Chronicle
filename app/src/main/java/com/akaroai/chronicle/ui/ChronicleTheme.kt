package com.akaroai.chronicle.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object ChronicleColors {
    val Void = Color(0xFF060A19)
    val DeepNavy = Color(0xFF0B1025)
    val Surface = Color(0xFF121936)
    val SurfaceRaised = Color(0xFF182143)
    val Lavender = Color(0xFFA78BFA)
    val Violet = Color(0xFF7657E8)
    val Cyan = Color(0xFF67D8F3)
    val Mint = Color(0xFF72E6BE)
    val Amber = Color(0xFFF2B96B)
    val Rose = Color(0xFFF07A9B)
    val Ink = Color(0xFFF3EEFF)
    val MutedInk = Color(0xFFB8B4CE)
}

private val ChronicleDarkColors = darkColorScheme(
    primary = ChronicleColors.Lavender,
    onPrimary = ChronicleColors.Void,
    primaryContainer = ChronicleColors.Violet,
    onPrimaryContainer = ChronicleColors.Ink,
    secondary = ChronicleColors.Cyan,
    tertiary = ChronicleColors.Mint,
    background = ChronicleColors.Void,
    onBackground = ChronicleColors.Ink,
    surface = ChronicleColors.DeepNavy,
    onSurface = ChronicleColors.Ink,
    surfaceVariant = ChronicleColors.Surface,
    onSurfaceVariant = ChronicleColors.MutedInk,
    error = ChronicleColors.Rose,
    onError = ChronicleColors.Void
)

@Composable
fun ChronicleTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ChronicleDarkColors, content = content)
}
