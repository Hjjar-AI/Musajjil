package com.musajjil.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Change the palette here; screens consume semantic Material colors, never these literals.
internal val LightColors = lightColorScheme(
    primary = Color(0xFF087F72), onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F1E9), onPrimaryContainer = Color(0xFF004F46),
    secondary = Color(0xFF53677B), secondaryContainer = Color(0xFFE4EDF5),
    tertiary = Color(0xFFA36C30), tertiaryContainer = Color(0xFFFFEBD0),
    background = Color(0xFFF5F7F8), onBackground = Color(0xFF18282C),
    surface = Color.White, onSurface = Color(0xFF18282C),
    surfaceVariant = Color(0xFFEDF2F2), onSurfaceVariant = Color(0xFF596B6C),
    outline = Color(0xFF758888), outlineVariant = Color(0xFFDDE6E5),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF80D5C5), onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF124E45), onPrimaryContainer = Color(0xFFB8F2E3),
    secondary = Color(0xFFB0C8DE), secondaryContainer = Color(0xFF293E50),
    tertiary = Color(0xFFF3BD7B), tertiaryContainer = Color(0xFF4D351A),
    background = Color(0xFF101B1D), onBackground = Color(0xFFE0EBE9),
    surface = Color(0xFF172426), onSurface = Color(0xFFE0EBE9),
    surfaceVariant = Color(0xFF253435), onSurfaceVariant = Color(0xFFAFBFBD),
    outline = Color(0xFF849693), outlineVariant = Color(0xFF354947),
)
