package com.fahad.easypeasy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
    MaterialTheme(colorScheme = if (dark) Dark else Light,
        shapes = Shapes(medium = RoundedCornerShape(24.dp), large = RoundedCornerShape(24.dp)), content = content)
}
