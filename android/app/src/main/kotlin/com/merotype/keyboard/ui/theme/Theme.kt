package com.merotype.keyboard.ui.theme

import androidx.compose.foundation.isSystemInDarkMode
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF6200EE),
    secondary = androidx.compose.ui.graphics.Color(0xFF03DAC6),
    tertiary = androidx.compose.ui.graphics.Color(0xFF018786)
)

private val DarkColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFBB86FC),
    secondary = androidx.compose.ui.graphics.Color(0xFF03DAC6),
    tertiary = androidx.compose.ui.graphics.Color(0xFF03DAC6)
)

@Composable
fun MeroTypeTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkMode()) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
