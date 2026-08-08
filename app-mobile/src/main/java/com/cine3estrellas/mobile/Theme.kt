package com.cine3estrellas.mobile

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Gold = Color(0xFFFFD700)
val DarkGrey = Color(0xFF1A1A1A)
val Black = Color(0xFF000000)

private val DarkColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = Black,
    background = Black,
    onBackground = Color.White,
    surface = DarkGrey,
    onSurface = Color.White
)

@Composable
fun Cine3EstrellasMobileTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
