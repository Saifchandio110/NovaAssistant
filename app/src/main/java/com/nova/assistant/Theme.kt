package com.nova.assistant

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object NovaColors {
    val Background = Color(0xFF0B1020)
    val BackgroundTop = Color(0xFF141B3A)
    val Card = Color(0xFF151C33)
    val CardHigh = Color(0xFF1D2745)
    val Indigo = Color(0xFF7C8CFF)
    val IndigoDeep = Color(0xFF4B3FD6)
    val Teal = Color(0xFF4FD1C5)
    val TextPrimary = Color(0xFFE8EBFA)
    val TextSecondary = Color(0xFFA9B1D6)
    val Outline = Color(0xFF2E3963)
    val Ok = Color(0xFF4ADE80)
    val Warn = Color(0xFFFBBF24)
    val Bad = Color(0xFFFF6B6B)

    val screenBrush = Brush.verticalGradient(listOf(BackgroundTop, Background))
    val accentBrush = Brush.linearGradient(listOf(IndigoDeep, Indigo, Teal))
    val listeningBrush = Brush.linearGradient(listOf(Teal, Indigo))
}

private val NovaDarkColors = darkColorScheme(
    primary = NovaColors.Indigo,
    onPrimary = Color(0xFF0B1020),
    primaryContainer = Color(0xFF2A3566),
    onPrimaryContainer = Color(0xFFE3E6FF),
    secondary = NovaColors.Teal,
    onSecondary = Color(0xFF00201D),
    background = NovaColors.Background,
    onBackground = NovaColors.TextPrimary,
    surface = NovaColors.Card,
    onSurface = NovaColors.TextPrimary,
    surfaceVariant = NovaColors.CardHigh,
    onSurfaceVariant = NovaColors.TextSecondary,
    error = NovaColors.Bad,
    onError = Color(0xFF3A0A0A),
    errorContainer = Color(0xFF4A1C24),
    onErrorContainer = Color(0xFFFFD9D9),
    outline = NovaColors.Outline
)

@Composable
fun NovaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NovaDarkColors, content = content)
}
