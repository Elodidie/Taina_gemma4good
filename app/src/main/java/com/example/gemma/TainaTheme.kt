package com.example.gemma

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ─── Brand colours ─────────────────────────────────────────────────────────────
//
//  Extracted from the Taina logo:
//    • Background  #121812  — very dark forest-night black
//    • Primary     #00C878  — vivid leaf-green (the logo mark)
//
//  Full palette is derived from those two anchors using Material3 tonal rules.
// ──────────────────────────────────────────────────────────────────────────────

// Greens — primary family
private val Green10  = Color(0xFF002112)
private val Green20  = Color(0xFF003D22)
private val Green30  = Color(0xFF005C35)
private val Green40  = Color(0xFF007A49)   // light-theme primary
private val Green80  = Color(0xFF5EDDA0)   // dark-theme primary
private val Green90  = Color(0xFFA0F0C8)   // light primary container
private val Green95  = Color(0xFFCCF8E4)
private val GreenLogo= Color(0xFF00C878)   // the exact logo green

// Greens — secondary (muted sage)
private val SageGreen20 = Color(0xFF1E3D2A)
private val SageGreen40 = Color(0xFF4A7A5E)
private val SageGreen80 = Color(0xFFA2C9B2)
private val SageGreen90 = Color(0xFFCDE5D8)

// Tertiary — warm amber/moss accent
private val Moss30  = Color(0xFF3A4A1A)
private val Moss40  = Color(0xFF556B2F)
private val Moss80  = Color(0xFFBFCF8A)
private val Moss90  = Color(0xFFDCECA8)

// Neutrals
private val DarkBg      = Color(0xFF0F1510)   // app background (dark)
private val DarkSurface = Color(0xFF161D17)   // card / bottom-bar surface (dark)
private val DarkSurfaceVariant = Color(0xFF1E2A1F)
private val NeutralVariant30 = Color(0xFF3A4A3C)
private val NeutralVariant80 = Color(0xFFB0C4B2)
private val NeutralVariant90 = Color(0xFFCCE0CE)

// On-colours
private val OnDark    = Color(0xFFE0EDE2)
private val OnDarkVar = Color(0xFFB0C4B2)

// Error
private val Red40 = Color(0xFFBA1A1A)
private val Red80 = Color(0xFFFFB4AB)
private val Red90 = Color(0xFFFFDAD6)

// ─── Dark scheme (matches the logo aesthetic) ─────────────────────────────────

private val TainaDarkColorScheme = darkColorScheme(
    primary              = GreenLogo,          // #00C878
    onPrimary            = Green10,
    primaryContainer     = Green30,
    onPrimaryContainer   = Green90,

    secondary            = SageGreen80,
    onSecondary          = SageGreen20,
    secondaryContainer   = SageGreen20,
    onSecondaryContainer = SageGreen90,

    tertiary             = Moss80,
    onTertiary           = Moss30,
    tertiaryContainer    = Moss30,
    onTertiaryContainer  = Moss90,

    background           = DarkBg,
    onBackground         = OnDark,

    surface              = DarkSurface,
    onSurface            = OnDark,
    surfaceVariant       = DarkSurfaceVariant,
    onSurfaceVariant     = OnDarkVar,

    outline              = NeutralVariant30,
    outlineVariant       = DarkSurfaceVariant,

    error                = Red80,
    onError              = Red40,
    errorContainer       = Red40,
    onErrorContainer     = Red90,

    inverseSurface       = NeutralVariant90,
    inverseOnSurface     = Green10,
    inversePrimary       = Green40,
)

// ─── Light scheme ─────────────────────────────────────────────────────────────

private val TainaLightColorScheme = lightColorScheme(
    primary              = Green40,
    onPrimary            = Color.White,
    primaryContainer     = Green90,
    onPrimaryContainer   = Green10,

    secondary            = SageGreen40,
    onSecondary          = Color.White,
    secondaryContainer   = SageGreen90,
    onSecondaryContainer = SageGreen20,

    tertiary             = Moss40,
    onTertiary           = Color.White,
    tertiaryContainer    = Moss90,
    onTertiaryContainer  = Moss30,

    background           = Color(0xFFF4FBF4),
    onBackground         = Color(0xFF0D1B0F),

    surface              = Color.White,
    onSurface            = Color(0xFF0D1B0F),
    surfaceVariant       = NeutralVariant90,
    onSurfaceVariant     = NeutralVariant30,

    outline              = NeutralVariant80,
    outlineVariant       = NeutralVariant90,

    error                = Red40,
    onError              = Color.White,
    errorContainer       = Red90,
    onErrorContainer     = Color(0xFF410002),

    inverseSurface       = Color(0xFF2D3C2F),
    inverseOnSurface     = Color(0xFFEEF5EE),
    inversePrimary       = Green80,
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
