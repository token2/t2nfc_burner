package com.token2.burner3.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Design direction: "calm instrument".
 *
 * This is a security-hardware tool. It should feel like a trustworthy piece of
 * lab equipment — quiet, precise, unhurried — not a consumer growth app. The
 * palette is a deep desaturated slate/ink base with a single confident signal
 * colour (a warm amber) reserved for the one thing that matters on each screen:
 * the live action. Success uses a restrained teal-green; problems use a muted
 * clay-red that reads as "attention", never "alarm/panic", because most errors
 * here are honest mistakes (wrong QR), not disasters.
 *
 * Type: a heavier grotesque weight for headings paired with a comfortable body,
 * and a monospaced face for the one place precision is literal — serials, codes,
 * and the secret preview.
 */

// Palette — Token2 house style: brand red on a dark ink base.
private val Ink        = Color(0xFF15171C)  // near-black base
private val Slate      = Color(0xFF1E2128)  // raised surface
private val SlateHi    = Color(0xFF292D36)  // cards / inputs
private val Mist       = Color(0xFFBAC0CC)  // secondary text
private val Paper      = Color(0xFFF3F5F9)  // primary text on dark
private val BrandRed    = Color(0xFFF80041)  // Token2 signal red
private val BrandRedInk  = Color(0xFFFFFFFF) // text on red
private val Teal        = Color(0xFF3FBFA0)  // success
private val Clay        = Color(0xFFE8836A)  // attention / gentle error
private val ClayInk     = Color(0xFF2A0F09)

// Light equivalents (kept close so brand reads the same in both modes)
private val LPaper     = Color(0xFFF7F8FB)
private val LSurface   = Color(0xFFFFFFFF)
private val LSurfaceHi = Color(0xFFEFF1F6)
private val LInk       = Color(0xFF15171C)
private val LMist      = Color(0xFF5A6270)

private val DarkColors = darkColorScheme(
    primary = BrandRed, onPrimary = BrandRedInk,
    secondary = Teal, onSecondary = Ink,
    background = Ink, onBackground = Paper,
    surface = Slate, onSurface = Paper,
    surfaceVariant = SlateHi, onSurfaceVariant = Mist,
    error = Clay, onError = ClayInk,
    outline = Color(0xFF3B424D),
)

private val LightColors = lightColorScheme(
    primary = BrandRed, onPrimary = BrandRedInk,
    secondary = Teal, onSecondary = LPaper,
    background = LPaper, onBackground = LInk,
    surface = LSurface, onSurface = LInk,
    surfaceVariant = LSurfaceHi, onSurfaceVariant = LMist,
    error = Clay, onError = ClayInk,
    outline = Color(0xFFCDD3DC),
)

// A restrained, deliberate type scale. System sans keeps the app light to build;
// weights and tracking carry the personality.
private val AppType = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp, lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 1.sp,
    ),
)

@Composable
fun Token2Theme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = AppType, content = content)
}
