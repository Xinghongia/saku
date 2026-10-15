package top.sakuraxy.client.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.sakuraxy.client.SakuApp

/** 我的：登录状态、账号入口与关于（签到/收藏等登录后功能随 P6 实现） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    app: SakuApp,
    onOpenLogin: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loggedIn by remember { mutableStateOf(app.client.session.snapshot().isNotEmpty()) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("我的") }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 账号卡片
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (loggedIn) "已登录" else "未登录",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = if (loggedIn) {
                                "登录状态已保存；签到、收藏等登录后功能随版本更新加入"
                            } else {
                                "登录后可获得与网页相同的访问权限"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            // 已登录时不再显示登录入口（与网页一致：登录后显示账号态而非登录按钮）
            if (!loggedIn) {
                EntryItem(Icons.Outlined.Login, "登录 / 切换账号", onOpenLogin)
            }
            EntryItem(Icons.Outlined.CalendarMonth, "每日签到", null)
            EntryItem(Icons.Outlined.FavoriteBorder, "我的收藏", null)
            EntryItem(Icons.Outlined.Info, "关于", null)

            Spacer(Modifier.height(4.dp))
            Text(
                text = "SakuraXy 客户端 0.2.0 · 数据来自 sakuraxy.top（第三方非官方客户端）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EntryItem(icon: ImageVector, title: String, onClick: (() -> Unit)?) {
    Card(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            if (onClick == null) {
                Text(
                    "即将支持",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
