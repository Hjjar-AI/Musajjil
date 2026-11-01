package com.musajjil.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing is independent of screen layout so density can be customized once. */
@Immutable
data class AppDimensions(
    val tiny: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 12.dp,
    val large: Dp = 16.dp,
    val section: Dp = 24.dp,
    val page: Dp = 20.dp,
    val avatar: Dp = 42.dp,
    val brandIcon: Dp = 32.dp,
    val maxContentWidth: Dp = 840.dp,
)

val LocalAppDimensions = staticCompositionLocalOf { AppDimensions() }
