package com.example.gemma

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ─── NGO Brand colours ────────────────────────────────────────────────────────
//   Green  #30CF89   primary action colour
//   Black  #282828   dark background
//   Beige  #F2EEE4   light background / warm neutral
// ─────────────────────────────────────────────────────────────────────────────

// Primary — NGO green family
private val NgoGreen       = Color(0xFF30CF89)   // exact brand green
private val NgoGreenDark   = Color(0xFF1A7A52)   // darker container / dark-theme primary container
private val NgoGreenLight  = Color(0xFFB8F5DC)   // on-primary-container (light)
private val NgoGreenDeep   = Color(0xFF003D22)   // on-primary-container (dark)

// Neutrals — derived from black and beige anchors
private val NgoBlack       = Color(0xFF282828)   // exact brand black
private val NgoBlackLight  = Color(0xFF323232)   // surface (dark theme)
private val NgoBlackVariant= Color(0xFF3A3A3A)   // surface variant (dark theme)

private val NgoBeige       = Color(0xFFF2EEE4)   // exact brand beige
private val NgoBeigeDeep   = Color(0xFFE8E4DC)   // surface variant (light theme)
private val NgoBeigeText   = Color(0xFF484440)   // on-surface-variant (light theme)

// On-colours
private val OnDarkBg       = Color(0xFFF2EEE4)   // beige text on dark background
private val OnDarkSurface  = Color(0xFFD8D4CC)   // muted beige for secondary text

// Error
private val Red40 = Color(0xFFBA1A1A)
private val Red80 = Color(0xFFFFB4AB)
private val Red90 = Color(0xFFFFDAD6)

// ─── Dark scheme ──────────────────────────────────────────────────────────────

private val TainaDarkColorScheme = darkColorScheme(
    primary              = NgoGreen,
    onPrimary            = NgoBlack,
    primaryContainer     = NgoGreenDark,
    onPrimaryContainer   = NgoGreenLight,

    secondary            = Color(0xFF90CBA8),     // muted sage green
    onSecondary          = Color(0xFF1A3D2A),
    secondaryContainer   = Color(0xFF1E3D2A),
    onSecondaryContainer = Color(0xFFCCE8D8),

    tertiary             = Color(0xFFD4C89A),      // warm sand accent
    onTertiary           = Color(0xFF3A3020),
    tertiaryContainer    = Color(0xFF3A3020),
    onTertiaryContainer  = Color(0xFFF0E4C0),

    background           = NgoBlack,
    onBackground         = OnDarkBg,

    surface              = NgoBlackLight,
    onSurface            = OnDarkBg,
    surfaceVariant       = NgoBlackVariant,
    onSurfaceVariant     = OnDarkSurface,

    outline              = Color(0xFF585450),
    outlineVariant       = NgoBlackVariant,

    error                = Red80,
    onError              = Red40,
    errorContainer       = Red40,
    onErrorContainer     = Red90,

    inverseSurface       = NgoBeige,
    inverseOnSurface     = NgoBlack,
    inversePrimary       = NgoGreenDark,
)

// ─── Light scheme ─────────────────────────────────────────────────────────────

private val TainaLightColorScheme = lightColorScheme(
    primary              = Color(0xFF1A8C5E),      // darker green for contrast on beige
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFFB8F5DC),
    onPrimaryContainer   = NgoGreenDeep,

    secondary            = Color(0xFF3D7A58),
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFFCCE8D8),
    onSecondaryContainer = Color(0xFF1A3D2A),

    tertiary             = Color(0xFF7A6A40),
    onTertiary           = Color.White,
    tertiaryContainer    = Color(0xFFF0E4C0),
    onTertiaryContainer  = Color(0xFF3A3020),

    background           = NgoBeige,
    onBackground         = NgoBlack,

    surface              = Color.White,
    onSurface            = NgoBlack,
    surfaceVariant       = NgoBeigeDeep,
    onSurfaceVariant     = NgoBeigeText,

    outline              = Color(0xFFA0988C),
    outlineVariant       = NgoBeigeDeep,

    error                = Red40,
    onError              = Color.White,
    errorContainer       = Red90,
    onErrorContainer     = Color(0xFF410002),

    inverseSurface       = NgoBlack,
    inverseOnSurface     = NgoBeige,
    inversePrimary       = NgoGreen,
)

// ─── TainaTheme composable ────────────────────────────────────────────────────

@Composable
fun TainaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content:   @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) TainaDarkColorScheme else TainaLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content     = content
    )
}
