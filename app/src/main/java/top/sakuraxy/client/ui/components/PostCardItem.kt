package top.sakuraxy.client.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import top.sakuraxy.client.core.SiteParser

/**
 * 资源卡片：与站点列表卡片信息对齐——
 * 封面（16:9，兜底图）、角标、标题、付费徽章、分类、#标签、作者、时间、三项统计。
 * 分类与标签 chip 都可点：分类进分类页、标签进标签页（同一套归档页组件）。
 */
@Composable
fun PostCardItem(
    card: SiteParser.PostCard,
    onClick: () -> Unit,
    onTermClick: (SiteParser.CardTerm) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        // 封面（无封面或加载失败时露出手动占位底图，不留空白块）
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
        ) {
            CoverPlaceholder()
            if (card.coverUrl != null) {
                AsyncImage(
                    model = card.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            card.badge?.let { badge ->
                MiniChip(
                    text = badge,
                    container = MaterialTheme.colorScheme.primary,
                    content = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                )
            }
        }

        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (card.payLabel != null || card.categories.isNotEmpty() || card.tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    card.payLabel?.let { label ->
                        MiniChip(
                            text = if (card.payPoints != null) "$label ${card.payPoints}" else label,
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    card.categories.forEach { term ->
                        MiniChip(text = term.name, onClick = { onTermClick(term) })
                    }
                    card.tags.take(4).forEach { tag ->
                        MiniChip(
                            text = "# ${tag.name}",
                            container = MaterialTheme.colorScheme.surfaceVariant,
                            content = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { onTermClick(tag) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (card.authorName != null || card.authorAvatar != null) {
                    UserAvatar(url = card.authorAvatar, size = 20.dp)
                    Spacer(Modifier.width(6.dp))
                }
                card.authorName?.let { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                card.authorVipBadge?.let { vip ->
                    Spacer(Modifier.width(4.dp))
                    MiniChip(
                        text = vip,
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                card.dateText?.let { date ->
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.weight(1f))
                StatsRow(
                    commentCount = card.commentCount,
                    viewCount = card.viewCount,
                    likeCount = card.likeCount,
                )
            }
        }
    }
}
