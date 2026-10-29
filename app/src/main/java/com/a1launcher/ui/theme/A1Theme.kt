package com.a1launcher.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val FallbackDark = darkColorScheme(
    primary = Color(0xFF6C5CE7),
    secondary = Color(0xFF00D1A0),
    background = Color(0xFF0E0E12),
    surface = Color(0xFF1A1A22),
)

private val FallbackLight = lightColorScheme(
    primary = Color(0xFF6C5CE7),
    secondary = Color(0xFF00D1A0),
)

/**
 * A1 Launcher's Material 3 theme: dynamic color (Material You) from the
 * device's wallpaper on Android 12+, a fixed brand palette below that. Every
 * screen in the app wraps its content in this instead of a bare
 * `MaterialTheme { ... }`, so the launcher's own chrome follows the same
 * color the rest of the system does.
 */
@Composable
fun A1Theme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) FallbackDark else FallbackLight
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
