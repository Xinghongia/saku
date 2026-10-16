package top.sakuraxy.client.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** 深浅模式偏好（跟随系统 / 固定浅色 / 固定深色） */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val AmoledBlack = Color(0xFF000000)

/**
 * 应用主题（2026-10-16 改版）。
 *
 * - 配色来源优先级：动态取色（Android 12+，默认开启，跟随壁纸）
 *   → 预设主题（[PresetThemes]，五套自研配色）
 * - 全部 surface 容器带主题色倾向，无中性灰（底栏/卡片/菜单不发灰）
 * - 状态栏/导航栏图标颜色跟随深浅模式（避免强制深色时图标看不清）
 *
 * 备注：material3 1.4.0（当前 BOM）的 MaterialExpressiveTheme/MotionScheme 仍是
 * internal API，暂不可用；组件动效沿用 Material 3 默认。
 */
@Composable
fun SakuTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    presetThemeId: String = DefaultPresetThemeId,
    amoledDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val baseScheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        else -> findPresetTheme(presetThemeId).scheme(darkTheme)
    }

    val colorScheme = if (darkTheme && amoledDark) {
        baseScheme.copy(background = AmoledBlack, surface = AmoledBlack)
    } else {
        baseScheme
    }

    // 状态栏 / 导航栏图标颜色跟随应用深浅（系统浅色下强制深色主题时也清晰）
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(view, darkTheme) {
            val window = (view.context as? android.app.Activity)?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            val prevStatus = controller?.isAppearanceLightStatusBars
            val prevNav = controller?.isAppearanceLightNavigationBars
            controller?.apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            onDispose {
                controller?.apply {
                    prevStatus?.let { isAppearanceLightStatusBars = it }
                    prevNav?.let { isAppearanceLightNavigationBars = it }
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
