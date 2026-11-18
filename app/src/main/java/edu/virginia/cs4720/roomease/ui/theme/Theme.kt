package edu.virginia.cs4720.roomease.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import edu.virginia.cs4720.roomease.data.ThemeMode

// Color ROLES, not colors (Lecture 8): every Material component picks these up.
private val LightColors = lightColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,
    primaryContainer = BrandGreenSoft,
    onPrimaryContainer = BrandGreen,
    secondary = BrandBlue,
    onSecondary = Color.White,
    secondaryContainer = BrandBlueSoft,
    onSecondaryContainer = BrandBlue,
    tertiary = Warning,
    tertiaryContainer = WarningSoft,
    onTertiaryContainer = Warning,
    background = BgCanvas,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = BgSubtle,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Surface,
    surfaceContainerLow = Surface,
    surfaceContainerHigh = BgSubtle,
    outline = TextTertiary,
    outlineVariant = BorderDefault,
    error = Danger,
    onError = Color.White,
    errorContainer = DangerSoft,
    onErrorContainer = Danger
)

private val DarkColors = darkColorScheme(
    primary = BrandGreenDark,
    onPrimary = Color(0xFF00382A),
    primaryContainer = BrandGreenDarkContainer,
    onPrimaryContainer = BrandGreenDark,
    secondary = BrandBlueDark,
    onSecondary = Color(0xFF00344F),
    secondaryContainer = BrandBlueDarkContainer,
    onSecondaryContainer = BrandBlueDark,
    tertiary = Color(0xFFF2C46B),
    tertiaryContainer = Color(0xFF4A3500),
    onTertiaryContainer = Color(0xFFF2C46B),
    background = DarkCanvas,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSubtle,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainer = DarkSurface,
    surfaceContainerLow = DarkSurface,
    surfaceContainerHigh = DarkSubtle,
    outline = DarkTextSecondary,
    outlineVariant = DarkBorder,
    error = DangerDark,
    onError = Color(0xFF5C0A0A),
    errorContainer = Color(0xFF5C1A1A),
    onErrorContainer = DangerDark
)

// Rounded cards and pill buttons from the mockups.
private val RoomEaseShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun RoomEaseTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    // Settings screen's theme mode (DataStore) decides light/dark; SYSTEM follows the phone.
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = Typography,
        shapes = RoomEaseShapes,
        content = content
    )
}
