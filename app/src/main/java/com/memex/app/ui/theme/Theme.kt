package com.memex.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

// ── Color scheme — dark only ─────────────────────────────────────────────────
private val MemexDarkColorScheme = darkColorScheme(
    // Brand
    primary             = MemexPurple,
    onPrimary           = MemexWhite,
    primaryContainer    = MemexPurpleDim,
    onPrimaryContainer  = MemexPurpleLight,

    // Secondary
    secondary           = MemexTeal,
    onSecondary         = MemexBlack,
    secondaryContainer  = MemexTealDim,
    onSecondaryContainer= MemexTeal,

    // Backgrounds / surfaces
    background          = MemexBlack,
    onBackground        = MemexWhite,
    surface             = MemexDeepNavy,
    onSurface           = MemexWhite,
    surfaceVariant      = MemexCard,
    onSurfaceVariant    = MemexGray,

    // Outlines
    outline             = MemexCardBorder,
    outlineVariant      = MemexGrayDim,

    // Semantic
    error               = MemexRed,
    onError             = MemexWhite,
    errorContainer      = MemexRed.copy(alpha = 0.15f),
    onErrorContainer    = MemexRed,

    // Inverse (used by snackbars etc.)
    inverseSurface      = MemexWhite,
    inverseOnSurface    = MemexBlack,
    inversePrimary      = MemexPurpleDim
)

// ── Shapes — all generously rounded ─────────────────────────────────────────
private val MemexShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(16.dp),   // default card / dialog
    large      = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

// ── Theme composable ─────────────────────────────────────────────────────────
/**
 * MEMEX app theme — always dark, never light.
 * Wrap your entire Compose UI in this.
 */
@Composable
fun MemexTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MemexDarkColorScheme,
        typography  = MemexTypography,
        shapes      = MemexShapes,
        content     = content
    )
}
