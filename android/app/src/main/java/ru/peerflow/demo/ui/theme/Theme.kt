package ru.peerflow.demo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = IndigoSecondary,
    tertiary = AmberTertiary,
    background = SurfaceLight,
    surface = androidx.compose.ui.graphics.Color.White,
)

private val DarkColors = darkColorScheme(
    primary = TealPrimaryDark,
    secondary = IndigoSecondaryDark,
    tertiary = AmberTertiaryDark,
    background = SurfaceDark,
)

// Динамические цвета намеренно отключены: демо должно выглядеть
// одинаково на всех устройствах и эмуляторах.
@Composable
fun PeerflowTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PeerflowTypography,
        content = content,
    )
}
