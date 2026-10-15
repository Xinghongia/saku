package top.sakuraxy.client.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * 主题配色。
 *
 * 深浅色跟随系统（isSystemInDarkTheme）。
 * Android 12+ 默认使用系统动态取色（跟随壁纸），保证与系统观感一致；
 * 低版本或动态取色不可用时退回品牌配色（取站点主色系的樱花粉）。
 */
@Composable
fun SakuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

// 品牌配色（站点主色 #f156b4 的 Material 3 色调化结果，深浅两套）
private val LightColors = lightColorScheme(
    primary = Color(0xFFB03A73),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD8EA),
    onPrimaryContainer = Color(0xFF3E0021),
    secondary = Color(0xFF8A4F6D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD8EA),
    onSecondaryContainer = Color(0xFF3A0024),
    tertiary = Color(0xFF7A5732),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFF8F9),
    onBackground = Color(0xFF201A1C),
    surface = Color(0xFFFFF8F9),
    onSurface = Color(0xFF201A1C),
    surfaceVariant = Color(0xFFF3DDE4),
    onSurfaceVariant = Color(0xFF514348),
    outline = Color(0xFF837378),
    outlineVariant = Color(0xFFD5C2C7),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB0D0),
    onPrimary = Color(0xFF65003B),
    primaryContainer = Color(0xFF8E0054),
    onPrimaryContainer = Color(0xFFFFD8EA),
    secondary = Color(0xFFFEB1D3),
    onSecondary = Color(0xFF50203A),
    secondaryContainer = Color(0xFF6B3651),
    onSecondaryContainer = Color(0xFFFFD8EA),
    tertiary = Color(0xFFEBBE8F),
    onTertiary = Color(0xFF452B09),
    background = Color(0xFF181113),
    onBackground = Color(0xFFEDE0E2),
    surface = Color(0xFF181113),
    onSurface = Color(0xFFEDE0E2),
    surfaceVariant = Color(0xFF514348),
    onSurfaceVariant = Color(0xFFD5C2C7),
    outline = Color(0xFF9E8C91),
    outlineVariant = Color(0xFF514348),
)
