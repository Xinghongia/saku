package top.sakuraxy.client.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import top.sakuraxy.client.core.UserStat

/** 等级图标（站点主题素材，与网页同源）：user-level-N.png */
private const val LEVEL_ICON_URL = "https://www.sakuraxy.top/wp-content/themes/zibll/img/user-level-%d.png"

/** 圆形头像：加载失败/为空时退回人形占位图标 */
@Composable
fun UserAvatar(
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    borderWidth: Dp = 0.dp,
) {
    val shape = CircleShape
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
            .size(size)
            .then(
                if (borderWidth > 0.dp) {
                    Modifier.border(borderWidth, MaterialTheme.colorScheme.surfaceContainerLow, shape)
                } else Modifier
            ),
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(shape),
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(size * 0.55f),
                )
            }
        }
    }
}

/** 等级小图标（LV1 → user-level-1.png） */
@Composable
fun LevelBadge(level: Int?, modifier: Modifier = Modifier) {
    if (level == null || level <= 0) return
    AsyncImage(
        model = LEVEL_ICON_URL.format(level),
        contentDescription = "LV$level",
        modifier = modifier.size(18.dp),
    )
}

/**
 * 徽章名列表。[onCover] = 叠在封面上时用半透明深色底 + 白字，保证对比度。
 * （站点素材为 SVG，客户端以文字小签展示）
 */
@Composable
fun MedalChips(
    medals: List<String>,
    modifier: Modifier = Modifier,
    onCover: Boolean = false,
) {
    if (medals.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        medals.forEach { name ->
            Surface(
                shape = CircleShape,
                color = if (onCover) {
                    androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.30f)
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                },
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (onCover) {
                        androidx.compose.ui.graphics.Color.White
                    } else {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** 统计行（文章/评论/收藏/粉丝），点击进入作者页对应标签 */
@Composable
fun UserStatRow(
    stats: List<UserStat>,
    onStatClick: (UserStat) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (stats.isEmpty()) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(vertical = 12.dp)) {
            stats.forEach { stat ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onStatClick(stat) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stat.count,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stat.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 付费类操作提示（充值 / 会员开通）。
 * 红线：客户端不经手支付、也不跳转——只提示驾驶员到网站自行操作。
 */
@Composable
fun SiteOnlyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("请在网站操作") },
        text = {
            Text("充值、会员开通等付费功能请在浏览器访问网站 www.sakuraxy.top，登录后到用户中心操作。")
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        },
    )
}
