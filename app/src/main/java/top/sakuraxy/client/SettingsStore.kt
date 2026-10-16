package top.sakuraxy.client

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import top.sakuraxy.client.ui.theme.DefaultPresetThemeId
import top.sakuraxy.client.ui.theme.ThemeMode

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * 应用偏好（外观与浏览习惯），与登录会话的 "session" DataStore 分开存储。
 * 首版字段：主题模式 / 动态取色 / 预设主题 / 纯黑模式 / 默认排序。
 */
data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 动态取色（Android 12+ 跟随壁纸），默认开 —— 与参考项目 rikkahub 的默认行为一致 */
    val dynamicColor: Boolean = true,
    val presetThemeId: String = DefaultPresetThemeId,
    /** 深色模式下使用纯黑背景（OLED 省电 / 观感） */
    val amoledDark: Boolean = false,
    /** 首页默认排序；空字符串 = 站点默认（推荐） */
    val defaultSort: String = "",
)

class SettingsStore(private val context: Context) {

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")
    private val presetThemeIdKey = stringPreferencesKey("preset_theme_id")
    private val amoledDarkKey = booleanPreferencesKey("amoled_dark")
    private val defaultSortKey = stringPreferencesKey("default_sort")

    val flow: Flow<Settings> = context.settingsDataStore.data.map { prefs ->
        Settings(
            themeMode = ThemeMode.entries.firstOrNull { it.name == prefs[themeModeKey] }
                ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[dynamicColorKey] ?: true,
            presetThemeId = prefs[presetThemeIdKey] ?: DefaultPresetThemeId,
            amoledDark = prefs[amoledDarkKey] ?: false,
            defaultSort = prefs[defaultSortKey] ?: "",
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[themeModeKey] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[dynamicColorKey] = enabled }
    }

    suspend fun setPresetThemeId(id: String) {
        context.settingsDataStore.edit { it[presetThemeIdKey] = id }
    }

    suspend fun setAmoledDark(enabled: Boolean) {
        context.settingsDataStore.edit { it[amoledDarkKey] = enabled }
    }

    suspend fun setDefaultSort(key: String) {
        context.settingsDataStore.edit { it[defaultSortKey] = key }
    }
}
