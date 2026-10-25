package com.vknxworks.textctrl.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vknxworks.textctrl.R

/**
 * Ctrl / Signal colours, shared by every Ctrl app. Dark only.
 * [Accent] is the one place to change the app's accent colour.
 */
object CtrlColors {
    val Accent = Color(0xFFB48CFF)
    /** Text and icons on the accent colour. */
    val OnAccent = Color(0xFF08080A)

    val Background = Color(0xFF08080A)
    /** Cards, sheets, dialogs and incoming bubbles. */
    val Surface = Color(0xFF141417)
    val Raised = Color(0xFF1C1C21)
    /** Card borders. */
    val Outline = Color(0xFF232328)
    /** Borders on controls (buttons, fields, the profile button). */
    val ControlOutline = Color(0xFF2A2A2F)
    /** Lines between list rows. */
    val Divider = Color(0xFF1E1E23)

    val Text = Color(0xFFF4F4F2)
    val Muted = Color(0xFFA3A3AB)
    /** Disabled and "off" states. */
    val Inactive = Color(0xFF8C8C94)
    val Error = Color(0xFFFF6B6B)

    val Track = Color(0xFF26262C)
    val SwitchOff = Color(0xFF2A2A30)
    val PinEmpty = Color(0xFF3A3A41)
}

private val CtrlScheme = darkColorScheme(
    primary = CtrlColors.Accent,
    onPrimary = CtrlColors.OnAccent,
    primaryContainer = CtrlColors.Accent,
    onPrimaryContainer = CtrlColors.OnAccent,
    inversePrimary = CtrlColors.Accent,
    secondary = CtrlColors.Text,
    onSecondary = CtrlColors.OnAccent,
    secondaryContainer = CtrlColors.Raised,
    onSecondaryContainer = CtrlColors.Text,
    tertiary = CtrlColors.Accent,
    onTertiary = CtrlColors.OnAccent,
    tertiaryContainer = CtrlColors.Raised,
    onTertiaryContainer = CtrlColors.Text,
    background = CtrlColors.Background,
    onBackground = CtrlColors.Text,
    surface = CtrlColors.Surface,
    onSurface = CtrlColors.Text,
    surfaceVariant = CtrlColors.Raised,
    onSurfaceVariant = CtrlColors.Muted,
    surfaceTint = CtrlColors.Surface,
    inverseSurface = CtrlColors.Text,
    inverseOnSurface = CtrlColors.Background,
    error = CtrlColors.Error,
    onError = CtrlColors.OnAccent,
    errorContainer = Color(0xFF3A1A1C),
    onErrorContainer = Color(0xFFFFD9D9),
    outline = CtrlColors.ControlOutline,
    outlineVariant = CtrlColors.Outline,
    scrim = Color.Black,
    surfaceBright = CtrlColors.Raised,
    surfaceDim = CtrlColors.Background,
    surfaceContainerLowest = CtrlColors.Background,
    surfaceContainerLow = Color(0xFF0E0E11),
    surfaceContainer = CtrlColors.Surface,
    surfaceContainerHigh = CtrlColors.Raised,
    surfaceContainerHighest = CtrlColors.Outline,
)

// Space Grotesk is one variable font (weights 300-700); JetBrains Mono comes as three static weights.
private fun grotesk(weight: Int) = Font(
    R.font.space_grotesk,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** All UI text and headings. */
val SpaceGrotesk = FontFamily(grotesk(400), grotesk(500), grotesk(600), grotesk(700))

/** Numbers, data readouts, section labels and the wordmark. */
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
)

private fun style(size: Int, line: Int, weight: Int, tracking: Double = 0.0, family: FontFamily = SpaceGrotesk) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.em,
)

private val CtrlTypography = Typography(
    // Big numeric readouts.
    displayLarge = style(64, 60, 700, -0.05),
    // Display / hero.
    displayMedium = style(56, 54, 700, -0.04),
    displaySmall = style(44, 46, 700, -0.035),
    // Headline (main tab titles).
    headlineLarge = style(40, 42, 700, -0.035),
    headlineMedium = style(32, 36, 700, -0.03),
    headlineSmall = style(24, 30, 700, -0.02),
    titleLarge = style(20, 26, 600, -0.01),
    // Title.
    titleMedium = style(18, 24, 600),
    titleSmall = style(15, 20, 600),
    // Body.
    bodyLarge = style(16, 24, 400),
    bodyMedium = style(14, 20, 400),
    bodySmall = style(12, 17, 400),
    labelLarge = style(14, 20, 600),
    labelMedium = style(12, 16, 500),
    labelSmall = style(11, 14, 500),
)

/** Mono text styles that Material's type scale has no slot for. */
object CtrlType {
    /** "TEXT/CTRL" in the top bar. */
    val Wordmark = style(13, 16, 600, 0.14, JetBrainsMono)
    /** Section headers: callers upper-case the text; shown in the muted colour. */
    val Label = style(11, 14, 500, 0.16, JetBrainsMono)
    /** Small data next to a title: times, numbers, counts. */
    val Meta = style(12, 16, 400, 0.0, JetBrainsMono)
    /** Status words at the end of a row. */
    val Status = style(11, 14, 500, 0.1, JetBrainsMono)
    /** Lock screen keys and other big mono readouts. */
    val Key = style(26, 30, 500, 0.0, JetBrainsMono)
    /** The giant number on a hero card. */
    val Readout = style(64, 58, 700, -0.05)
}

private val CtrlShapes = Shapes(
    extraSmall = RoundedCornerShape(9.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/** Horizontal page margin used everywhere. */
val Gutter = 20.dp

/** The Ctrl / Signal theme: dark only, no wallpaper colours. */
@Composable
fun TextCtrlTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CtrlScheme, typography = CtrlTypography, shapes = CtrlShapes, content = content)
}
