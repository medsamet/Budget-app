package com.medsamet.budgetapp.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6F5C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB9E4D6),
    onPrimaryContainer = Color(0xFF06251C),
    secondary = Color(0xFF4C6359),
    tertiary = Color(0xFF8A6552),
    background = Color(0xFFFBFBF9),
    onBackground = Color(0xFF1A1C1B),
    surface = Color(0xFFFBFBF9),
    onSurface = Color(0xFF1A1C1B),
    surfaceVariant = Color(0xFFDBE5DF),
    onSurfaceVariant = Color(0xFF3F4945),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DD3C2),
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF005141),
    onPrimaryContainer = Color(0xFFB9E4D6),
    secondary = Color(0xFFB3CCC0),
    tertiary = Color(0xFFF0BB9E),
    background = Color(0xFF111413),
    onBackground = Color(0xFFE1E3E1),
    surface = Color(0xFF111413),
    onSurface = Color(0xFFE1E3E1),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBFC9C4),
    error = Color(0xFFF2B8B5)
)

@Composable
fun BudgetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}

/** Convertit une couleur "#RRGGBB" en [Color], avec repli gris si la valeur est invalide. */
fun parseHexColor(hex: String): Color {
    val cleaned = hex.trim().removePrefix("#")
    if (cleaned.length != 6) return Color(0xFF7A7A7A)
    return try {
        val value = cleaned.toLong(16)
        Color(0xFF000000L or value)
    } catch (_: Exception) {
        Color(0xFF7A7A7A)
    }
}
