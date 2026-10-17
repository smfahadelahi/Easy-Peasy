package com.fahad.easypeasy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalConfiguration
import com.fahad.easypeasy.R

private val Light = lightColorScheme(
    primary = Color(0xFF653D83), onPrimary = Color.White,
    secondary = Color(0xFF496245), onSecondary = Color.White,
    tertiary = Color(0xFF825326), background = Color(0xFFFFF8F0),
    surface = Color(0xFFFFF8F0), surfaceVariant = Color(0xFFEDE3EF),
    onSurface = Color(0xFF29232E), onSurfaceVariant = Color(0xFF514653),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFDAB5F5), onPrimary = Color(0xFF37164F),
    secondary = Color(0xFFB2CDAA), background = Color(0xFF19151E),
    surface = Color(0xFF19151E), surfaceVariant = Color(0xFF3B3341),
    onSurface = Color(0xFFF0E6F3), onSurfaceVariant = Color(0xFFD3C4D7),
)
@Composable
fun EasyPeasyTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val family = FontFamily(Font(if (LocalConfiguration.current.locales[0].language == "bn") R.font.noto_sans_bengali else R.font.noto_sans_kr))
    val base = Typography()
    val type = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family), displayMedium = base.displayMedium.copy(fontFamily = family), displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family), headlineMedium = base.headlineMedium.copy(fontFamily = family), headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family), titleMedium = base.titleMedium.copy(fontFamily = family), titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family), bodyMedium = base.bodyMedium.copy(fontFamily = family), bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family), labelMedium = base.labelMedium.copy(fontFamily = family), labelSmall = base.labelSmall.copy(fontFamily = family),
    )
    MaterialTheme(typography = type, colorScheme = if (dark) Dark else Light,
        shapes = Shapes(medium = RoundedCornerShape(24.dp), large = RoundedCornerShape(24.dp)), content = content)
}
