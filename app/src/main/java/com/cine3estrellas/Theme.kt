package com.cine3estrellas

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.*

val Gold = Color(0xFFFFD700)
val DarkGrey = Color(0xFF1A1A1A)
val Black = Color(0xFF000000)

private val DarkColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = Black,
    background = Black,
    onBackground = Color.White,
    surface = DarkGrey,
    onSurface = Color.White,
    border = Gold
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun Cine3EstrellasTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
