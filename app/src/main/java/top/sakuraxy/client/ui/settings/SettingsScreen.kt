package top.sakuraxy.client.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import kotlinx.coroutines.launch
import top.sakuraxy.client.BuildConfig
import top.sakuraxy.client.SakuApp
import top.sakuraxy.client.Settings
import top.sakuraxy.client.core.SiteParser
import top.sakuraxy.client.ui.AndroidActions
import top.sakuraxy.client.ui.components.GroupIcon
import top.sakuraxy.client.ui.components.GroupTone
import top.sakuraxy.client.ui.components.SakuTopAppBar
import top.sakuraxy.client.ui.components.SettingsGroup
import top.sakuraxy.client.ui.theme.PresetTheme
import top.sakuraxy.client.ui.theme.PresetThemes
import top.sakuraxy.client.ui.theme.ThemeMode

private const val REPO_URL = "https://github.com/Xinghongia/saku"

/** 排序选项：与首页/分类页一致（null = 推荐） */
private val sortOptions = listOf(null to "推荐") + SiteParser.knownSortOptions.map { it.key to it.label }

/** 设置页：外观（主题模式/动态取色/预设主题/纯黑）、浏览（默认排序）、数据（清缓存）、关于 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(app: SakuApp, onBack: () -> Unit) {
    val settings by app.settingsStore.flow.collectAsState(initial = Settings())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var sortExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            SakuTopAppBar(
                title = "设置",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsGroup(title = "外观") {
                block {
                    Text("主题模式", style = MaterialTheme.typography.bodyLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = settings.themeMode == mode,
                                onClick = { scope.launch { app.settingsStore.setThemeMode(mode) } },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ThemeMode.entries.size,
                                ),
                            ) {
                                Text(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> "跟随系统"
                                        ThemeMode.LIGHT -> "浅色"
                                        ThemeMode.DARK -> "深色"
                                    }
                                )
                            }
                        }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    switchItem(
                        headline = "动态取色",
                        supporting = "跟随壁纸生成整套配色（Android 12+）",
                        icon = Icons.Outlined.AutoAwesome,
                        tone = GroupTone.PRIMARY,
                        checked = settings.dynamicColor,
                        onCheckedChange = { on ->
                            scope.launch { app.settingsStore.setDynamicColor(on) }
                        },
                    )
                }

                if (!settings.dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    block {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GroupIcon(Icons.Outlined.Palette, GroupTone.PRIMARY)
                            Spacer(Modifier.width(16.dp))
                            Text("主题", style = MaterialTheme.typography.bodyLarge)
                        }
                        PresetThemeGrid(
                            selectedId = settings.presetThemeId,
                            onSelect = { id -> scope.launch { app.settingsStore.setPresetThemeId(id) } },
                        )
                    }
                } else {
                    item(
                        headline = "主题",
                        supporting = "已启用动态取色，关闭后可选择预设主题",
                        icon = Icons.Outlined.Palette,
                        tone = GroupTone.PRIMARY,
                    )
                }

                switchItem(
                    headline = "纯黑模式",
                    supporting = "深色模式下使用纯黑背景（OLED 更省电）",
                    icon = Icons.Outlined.DarkMode,
                    tone = GroupTone.PRIMARY,
                    checked = settings.amoledDark,
                    onCheckedChange = { on -> scope.launch { app.settingsStore.setAmoledDark(on) } },
                )
            }

            SettingsGroup(title = "浏览") {
                val currentSortLabel = sortOptions.firstOrNull { it.first == settings.defaultSort.ifBlank { null } }?.second
                    ?: "推荐"
                item(
                    headline = "默认排序",
                    supporting = "首页打开时使用的排序方式",
                    icon = Icons.Outlined.Sort,
                    tone = GroupTone.TERTIARY,
                    onClick = { sortExpanded = true },
                    trailing = {
                        Box {
                            Text(
                                currentSortLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            DropdownMenu(
                                expanded = sortExpanded,
                                onDismissRequest = { sortExpanded = false },
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ) {
                                sortOptions.forEach { (key, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            sortExpanded = false
                                            scope.launch { app.settingsStore.setDefaultSort(key ?: "") }
                                        },
                                        trailingIcon = {
                                            if ((settings.defaultSort.ifBlank { null }) == key) {
                                                Icon(
                                                    Icons.Outlined.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            }

            SettingsGroup(title = "数据") {
                item(
                    headline = "清理图片缓存",
                    supporting = "清空已缓存的封面与头像图片，不影响登录与其他数据",
                    icon = Icons.Outlined.CleaningServices,
                    tone = GroupTone.SECONDARY,
                    onClick = {
                        val loader = SingletonImageLoader.get(context)
                        loader.memoryCache?.clear()
                        loader.diskCache?.clear()
                        Toast.makeText(context, "图片缓存已清理", Toast.LENGTH_SHORT).show()
                    },
                )
            }

            SettingsGroup(title = "关于") {
                item(
                    headline = "版本",
                    icon = Icons.Outlined.Info,
                    tone = GroupTone.PRIMARY,
                    trailing = { Text(BuildConfig.VERSION_NAME, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
                item(
                    headline = "开源地址",
                    supporting = "GitHub · Xinghongia/saku",
                    icon = Icons.Outlined.Code,
                    tone = GroupTone.PRIMARY,
                    onClick = { AndroidActions.openInBrowser(context, REPO_URL) },
                )
                item(
                    headline = "分享应用",
                    icon = Icons.Outlined.Share,
                    tone = GroupTone.PRIMARY,
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "SakuraXy——sakuraxy.top 的安卓第三方客户端：$REPO_URL")
                        }
                        try {
                            context.startActivity(Intent.createChooser(intent, "分享应用"))
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, "没有可用的分享目标", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
                block {
                    Text(
                        "免责声明：本应用是 sakuraxy.top 的第三方非官方客户端，与站点官方无关。" +
                            "所有内容与访问权限均来自站点本身、与网页保持一致，不绕过任何付费、积分、登录或权限限制。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 预设主题色卡网格：每张卡预览该主题的四个代表色，选中描边 + 对勾 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetThemeGrid(selectedId: String, onSelect: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 3,
    ) {
        PresetThemes.forEach { preset ->
            ThemeSwatch(preset = preset, selected = preset.id == selectedId, onClick = { onSelect(preset.id) })
        }
    }
}

@Composable
private fun ThemeSwatch(preset: PresetTheme, selected: Boolean, onClick: () -> Unit) {
    val scheme = preset.lightScheme
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surface)
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SwatchDot(scheme.primary)
            SwatchDot(scheme.secondary)
            SwatchDot(scheme.tertiary)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(2.dp))
            }
            Text(
                preset.label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SwatchDot(color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(color)
    )
}
