package top.sakuraxy.client.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.sakuraxy.client.BuildConfig
import top.sakuraxy.client.SakuApp
import top.sakuraxy.client.ui.components.GroupTone
import top.sakuraxy.client.ui.components.SakuTopAppBar
import top.sakuraxy.client.ui.components.SettingsGroup

/** 「即将支持」占位小标签 */
@Composable
private fun PlaceholderBadge(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 我的：登录状态、账号入口与关于（签到/收藏等登录后功能随 P6 实现） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    app: SakuApp,
    onOpenLogin: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loggedIn by remember { mutableStateOf(app.client.session.snapshot().isNotEmpty()) }

    Scaffold(
        topBar = {
            SakuTopAppBar(
                title = "我的",
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "设置")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // 账号卡片
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                color = if (loggedIn) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Person,
                        contentDescription = null,
                        tint = if (loggedIn) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(30.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (loggedIn) "已登录" else "未登录",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (loggedIn) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        Text(
                            text = if (loggedIn) {
                                "登录状态已保存；签到、收藏等登录后功能随版本更新加入"
                            } else {
                                "登录后可获得与网页相同的访问权限"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (loggedIn) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    if (loggedIn) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    app.client.session.clear()
                                    app.sessionStore.clear()
                                    loggedIn = false
                                }
                            },
                        ) {
                            Icon(Icons.Outlined.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("退出")
                        }
                    }
                }
            }

            // 功能入口（登录相关功能占位说明，功能本身按 P6 排期实现）
            SettingsGroup(title = "账号与功能") {
                if (!loggedIn) {
                    item(
                        headline = "登录 / 切换账号",
                        supporting = "使用站点账号登录",
                        icon = Icons.Outlined.Login,
                        tone = GroupTone.PRIMARY,
                        onClick = onOpenLogin,
                    )
                }
                item(
                    headline = "每日签到",
                    supporting = "登录后签到领取积分",
                    icon = Icons.Outlined.CalendarMonth,
                    tone = GroupTone.TERTIARY,
                    trailing = { PlaceholderBadge("即将支持") },
                )
                item(
                    headline = "我的收藏",
                    supporting = "收藏的资源与文章",
                    icon = Icons.Outlined.FavoriteBorder,
                    tone = GroupTone.SECONDARY,
                    trailing = { PlaceholderBadge("即将支持") },
                )
            }

            Text(
                text = "SakuraXy 客户端 ${BuildConfig.VERSION_NAME} · 数据来自 sakuraxy.top（第三方非官方客户端）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
