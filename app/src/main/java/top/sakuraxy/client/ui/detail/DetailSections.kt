package top.sakuraxy.client.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import top.sakuraxy.client.core.Html
import top.sakuraxy.client.core.MiniPost
import top.sakuraxy.client.core.PayBox
import top.sakuraxy.client.core.PayDownload
import top.sakuraxy.client.core.PostAuthorCard
import top.sakuraxy.client.core.PostComment
import top.sakuraxy.client.core.PostHeader
import top.sakuraxy.client.core.StarRating
import top.sakuraxy.client.ui.components.LevelBadge
import top.sakuraxy.client.ui.components.MedalChips
import top.sakuraxy.client.ui.components.StatsRow
import top.sakuraxy.client.ui.components.UserAvatar

/**
 * 详情页各区块（对齐网页文章页 2026-10-16 实测结构）：
 * 作者行（关注/私信）→ 付费卡（积分购买/已购下载）→ 评分卡 → 互动栏（赞/享/藏）
 * → 作者信息卡（统计/简介/TA的文章）→ 评论（点赞/回复/发表）。
 */

// ---------- 作者行 + 计数行 ----------

@Composable
fun PostHeaderSection(
    header: PostHeader,
    following: Boolean,
    isSelf: Boolean,
    busy: Boolean,
    onOpenAuthor: (Long) -> Unit,
    onOpenChat: (Long) -> Unit,
    onToggleFollow: () -> Unit,
) {
    val authorId = header.authorId
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        UserAvatar(
            url = header.authorAvatar,
            size = 44.dp,
            modifier = if (authorId != null) {
                Modifier.clickable { onOpenAuthor(authorId) }
            } else Modifier,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = header.authorName ?: "佚名",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (authorId != null) {
                        Modifier.clickable { onOpenAuthor(authorId) }
                    } else Modifier,
                )
                Spacer(Modifier.width(4.dp))
                LevelBadge(header.authorLevel)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = header.dateText ?: header.dateTitle ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (header.medals.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                MedalChips(header.medals)
            }
        }
        if (!isSelf && authorId != null) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalButton(
                    onClick = onToggleFollow,
                    enabled = !busy,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Icon(
                        if (following) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (following) "已关注" else "关注", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = { onOpenChat(authorId) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("私信", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** 计数行：评论 / 浏览 / 点赞（独立一行，不再与作者挤在一起） */
@Composable
fun PostCountsRow(header: PostHeader) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        StatsRow(
            commentCount = header.commentCount,
            viewCount = header.viewCount,
            likeCount = header.likeCount,
        )
    }
}

// ---------- 付费卡 ----------

@Composable
fun PayCard(
    pay: PayBox,
    resolvingHref: String?,
    busy: Boolean,
    onBuy: () -> Unit,
    onResolve: (PayDownload) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        when (pay) {
            PayBox.Free -> Unit

            is PayBox.Unpaid -> Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer) {
                        Text(
                            "付费资源",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    pay.soldText?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = pay.price ?: "-",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = pay.unit ?: "积分",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (pay.vipHints.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "会员价：" + pay.vipHints.joinToString("，"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onBuy, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Outlined.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("立即购买")
                }
            }

            is PayBox.Paid -> Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("已购买", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32))
                    pay.soldText?.let {
                        Spacer(Modifier.weight(1f))
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(6.dp))
                InfoRow("订单号", pay.orderNo)
                InfoRow("支付时间", pay.payTime)
                InfoRow("支付金额", pay.payAmount)
                if (pay.downloads.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Text("资源下载", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    pay.downloads.forEach { download ->
                        OutlinedButton(
                            onClick = { onResolve(download) },
                            enabled = resolvingHref == null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (resolvingHref == download.url) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(download.label.ifBlank { "获取下载链接" })
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelMedium)
    }
}

// ---------- 评分卡 ----------

@Composable
fun RatingSection(
    rating: StarRating,
    busy: Boolean,
    onRate: (Int) -> Unit,
) {
    val average = rating.average?.toFloatOrNull() ?: 0f
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("为这篇文章评分", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                if (rating.rated) {
                    Text(
                        "已评分",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = rating.average ?: "0.0",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    // 显示星：按平均分取整点亮；可点击评分（已评过/提交中不可点）
                    Row {
                        repeat(5) { index ->
                            val filled = index < average + 0.5f
                            Icon(
                                if (filled) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = "${index + 1} 星",
                                tint = if (filled) Color(0xFFFFB300) else MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier
                                    .size(26.dp)
                                    .clickable(enabled = !rating.rated && !busy) { onRate(index + 1) },
                            )
                        }
                    }
                    Text(
                        if (rating.rated) "感谢您的评分" else "点击星星打分",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "共${rating.votes ?: 0}位网友评分",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (rating.distribution.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                rating.distribution.forEach { (star, count) ->
                    val votes = (rating.votes ?: 0).coerceAtLeast(1)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Text("$star 星", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(34.dp))
                        LinearProgressIndicator(
                            progress = { count.toFloat() / votes },
                            modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${count}人",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(36.dp),
                        )
                    }
                }
            }
        }
    }
}

// ---------- 互动栏（点赞 / 分享 / 收藏） ----------

@Composable
fun InteractionBar(
    likeCount: Int?,
    liked: Boolean,
    favoriteCount: Int?,
    favorited: Boolean,
    enabled: Boolean,
    onLike: () -> Unit,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "喜欢就支持一下吧",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ActionItem(
                    icon = { tint ->
                        Icon(Icons.Outlined.ThumbUp, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                    },
                    label = "点赞",
                    count = likeCount,
                    active = liked,
                    enabled = enabled,
                    onClick = onLike,
                )
                ActionItem(
                    icon = { tint ->
                        Icon(Icons.Outlined.Share, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                    },
                    label = "分享",
                    count = null,
                    active = false,
                    enabled = true,
                    onClick = onShare,
                )
                ActionItem(
                    icon = { tint ->
                        Icon(
                            if (favorited) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    label = if (favorited) "已收藏" else "收藏",
                    count = favoriteCount,
                    active = favorited,
                    enabled = enabled,
                    onClick = onFavorite,
                )
            }
        }
    }
}

@Composable
private fun ActionItem(
    icon: @Composable (Color) -> Unit,
    label: String,
    count: Int?,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.outlineVariant
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 4.dp),
    ) {
        icon(tint)
        Spacer(Modifier.height(2.dp))
        Text(
            text = if (count != null && count > 0) "$label $count" else label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}

// ---------- 作者卡 ----------

@Composable
fun AuthorCardSection(
    card: PostAuthorCard,
    following: Boolean,
    isSelf: Boolean,
    busy: Boolean,
    onToggleFollow: () -> Unit,
    onOpenAuthor: (Long) -> Unit,
    onOpenPost: (Long) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val authorId = card.authorId
                UserAvatar(
                    url = card.avatarUrl,
                    size = 48.dp,
                    modifier = if (authorId != null) Modifier.clickable { onOpenAuthor(authorId) } else Modifier,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            card.name ?: "佚名",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = if (authorId != null) Modifier.clickable { onOpenAuthor(authorId) } else Modifier,
                        )
                        Spacer(Modifier.width(4.dp))
                        LevelBadge(card.level)
                        card.vipBadge?.let {
                            Spacer(Modifier.width(4.dp))
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    if (card.medals.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        MedalChips(card.medals)
                    }
                }
                if (!isSelf && card.authorId != null) {
                    FilledTonalButton(
                        onClick = onToggleFollow,
                        enabled = !busy,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp),
                    ) {
                        Icon(
                            if (following) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (following) "已关注" else "关注", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                AuthorStat(card.postCount, "文章")
                AuthorStat(card.commentCount, "评论")
                AuthorStat(card.followerCount, "粉丝")
                AuthorStat(card.popularity, "人气")
            }
            card.desc?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (card.recentPosts.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("TA 的文章", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    card.recentPosts.forEach { post -> MiniPostCard(post, onOpenPost) }
                }
            }
        }
    }
}

@Composable
private fun AuthorStat(value: String?, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value ?: "-",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MiniPostCard(post: MiniPost, onOpenPost: (Long) -> Unit) {
    Column(
        Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = post.postId != null) { post.postId?.let(onOpenPost) },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            post.coverUrl?.let { cover ->
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(84.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            post.title,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            listOfNotNull(post.dateText, post.views).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------- 评论 ----------

@Composable
fun CommentRow(
    comment: PostComment,
    busy: Boolean,
    onLike: () -> Unit,
    onReply: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = (comment.depth * 16).dp),
    ) {
        UserAvatar(url = comment.avatarUrl, size = 32.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.authorName ?: "匿名",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(4.dp))
                LevelBadge(comment.authorLevel)
                comment.timeText?.let {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = Html.toText(comment.contentHtml),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !busy, onClick = onLike)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Icon(
                        Icons.Outlined.ThumbUp,
                        contentDescription = "点赞",
                        tint = if (comment.liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = (comment.likeCount ?: 0).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (comment.liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "回复",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onReply)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** 发表评论框（未登录 = 登录引导；已登录 = 输入 + 提交，提交前走滑块） */
@Composable
fun CommentComposer(
    loggedIn: Boolean,
    replyToName: String?,
    busy: Boolean,
    /** 提交成功计数：变化时清空输入框（失败/取消时保留草稿） */
    clearTick: Int,
    onSubmit: (String) -> Unit,
    onCancelReply: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        if (!loggedIn) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "登录后可参与评论",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onOpenLogin) { Text("去登录") }
            }
        } else {
            var text by remember { mutableStateOf("") }
            androidx.compose.runtime.LaunchedEffect(clearTick) {
                if (clearTick > 0) text = ""
            }
            Column(Modifier.padding(12.dp)) {
                if (replyToName != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "回复给 $replyToName",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onCancelReply) { Text("取消回复") }
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("欢迎您留下宝贵的见解！") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = { onSubmit(text) },
                        enabled = text.isNotBlank() && !busy,
                    ) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("提交评论")
                    }
                }
            }
        }
    }
}
