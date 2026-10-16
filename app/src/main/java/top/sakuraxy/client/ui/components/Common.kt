package top.sakuraxy.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 统一顶栏：透明融入页面背景，可选小字副标题（双行）。
 * 与页面底色无缝衔接，避免灰色顶栏色带；标题加粗提升层次。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SakuTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
    )
}

/**
 * 排序菜单按钮：点击弹出下拉选择（替代横向滚动的排序条）。
 * selected = null 表示「推荐」（选项列表第一项）。
 */
@Composable
fun SortMenuButton(
    options: List<Pair<String?, String>>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second
        ?: options.firstOrNull()?.second.orEmpty()

    Box(modifier) {
        TextButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(selectedLabel, style = MaterialTheme.typography.labelLarge)
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = "选择排序",
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            options.forEach { (key, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            label,
                            color = if (key == selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(key)
                    },
                    trailingIcon = {
                        if (key == selected) {
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
}

@Composable
fun ErrorRetry(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("重试") }
    }
}

@Composable
fun CenterLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
}

/**
 * 封面默认底图：文章/资源没有封面、或封面加载失败时显示（图片图标 + 淡色底），
 * 避免出现空白块。用法：垫在 AsyncImage 下层，失败时它自然露出。
 */
@Composable
fun CoverPlaceholder(
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 40.dp,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 评论 / 浏览 / 点赞 计数行（与站点卡片一致的三项统计） */
@Composable
fun StatsRow(
    commentCount: Int?,
    viewCount: Int?,
    likeCount: Int?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        StatItem(Icons.Outlined.ChatBubbleOutline, commentCount, tint)
        Spacer(Modifier.width(12.dp))
        StatItem(Icons.Outlined.Visibility, viewCount, tint)
        Spacer(Modifier.width(12.dp))
        StatItem(Icons.Outlined.FavoriteBorder, likeCount, tint)
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int?,
    tint: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = tint)
        Spacer(Modifier.width(3.dp))
        Text(
            text = count?.toString() ?: "-",
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}

/** 小标签（分类 / #标签 / 徽章），站点卡片上的 chip 样式 */
@Composable
fun MiniChip(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = content,
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** 列表底部：加载中 / 加载更多 / 已到底 / 重试 */
@Composable
fun ListFooter(
    loading: Boolean,
    canLoadMore: Boolean,
    error: String?,
    page: Int,
    maxPage: Int?,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onJump: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            error != null -> TextButton(onClick = onRetry) { Text("加载失败，点击重试") }
            canLoadMore -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onLoadMore) { Text("加载更多") }
                    if (onJump != null) {
                        TextButton(onClick = onJump) {
                            Text(if (maxPage != null) "第 $page / $maxPage 页 · 跳页" else "跳页")
                        }
                    }
                }
            }

            page > 0 && maxPage != null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "第 $page / $maxPage 页 · 已经到底了",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (onJump != null) {
                        TextButton(onClick = onJump) { Text("跳页") }
                    }
                }
            }
        }
    }
}

/** 跳页对话框（对应站点分页控件的「跳转」输入框） */
@Composable
fun PageJumpDialog(
    maxPage: Int?,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (maxPage != null) "跳转到第几页（1-$maxPage）" else "跳转到第几页") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { value: String -> text = value.filter { it.isDigit() }.take(6) },
                singleLine = true,
                placeholder = { Text("页码") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val page = text.toIntOrNull()
                    if (page != null && page >= 1 && (maxPage == null || page <= maxPage)) {
                        onJump(page)
                    }
                    onDismiss()
                },
            ) { Text("跳转") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
