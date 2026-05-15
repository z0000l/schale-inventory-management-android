package net.terryu16.schale.inventory.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object SchaleColors {
    val Primary = Color(0xFF3D8FE0)
    val PrimaryDark = Color(0xFF1F5FA8)
    val Accent = Color(0xFFFFD33C)

    val BgMain = Color(0xFF0E141C)
    val BgPanel = Color(0xFF161E2A)
    val BgPanelHigh = Color(0xFF1F2A3A)

    val BoardBg = Color(0xFF11171F)
    val BoardSurface = Color(0xFF1B2330)
    val CoverUnopened = Color(0xFF2A364B)
    val CoverOpened = Color(0xFF424F66)
    val GridLine = Color(0x22FFFFFF)

    val Item1 = Color(0xFFE15B6E)
    val Item2 = Color(0xFFFFD93D)
    val Item3 = Color(0xFF6FC8EE)

    val BestGlow = Color(0xFFFFC85C)
    val TextPrimary = Color(0xFFF6F8FB)
    val TextSecondary = Color(0xFFB3BCCB)
    val TextDisabled = Color(0xFF6E7889)
    val Divider = Color(0x22FFFFFF)
}

fun itemColor(itemIndex: Int): Color = when (itemIndex) {
    0 -> SchaleColors.Item1
    1 -> SchaleColors.Item2
    else -> SchaleColors.Item3
}

private val DarkColors = darkColorScheme(
    primary = SchaleColors.Primary,
    onPrimary = Color.White,
    primaryContainer = SchaleColors.PrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = SchaleColors.Accent,
    onSecondary = Color(0xFF1A1A1A),
    background = SchaleColors.BgMain,
    onBackground = SchaleColors.TextPrimary,
    surface = SchaleColors.BgPanel,
    onSurface = SchaleColors.TextPrimary,
    surfaceVariant = SchaleColors.BgPanelHigh,
    onSurfaceVariant = SchaleColors.TextSecondary,
    outline = SchaleColors.Divider,
    error = Color(0xFFFF5C7A),
)

private val SchaleTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 13.sp),
    bodySmall = TextStyle(fontSize = 11.sp, color = SchaleColors.TextSecondary),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.sp),
)

private val SchaleShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun SchaleInventoryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = SchaleTypography,
        shapes = SchaleShapes,
        content = content,
    )
}
