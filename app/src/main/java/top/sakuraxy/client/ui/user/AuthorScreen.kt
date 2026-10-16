package top.sakuraxy.client.ui.user

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import top.sakuraxy.client.SakuApp
import top.sakuraxy.client.core.AuthorComment
import top.sakuraxy.client.core.AuthorUserCard
import top.sakuraxy.client.core.SiteParser
import top.sakuraxy.client.ui.components.CenterLoading
import top.sakuraxy.client.ui.components.CoverPlaceholder
import top.sakuraxy.client.ui.components.EmptyHint
import top.sakuraxy.client.ui.components.ErrorRetry
import top.sakuraxy.client.ui.components.LevelBadge
import top.sakuraxy.client.ui.components.ListFooter
import top.sakuraxy.client.ui.components.PagePickerSheet
import top.sakuraxy.client.ui.components.PagerBar
import top.sakuraxy.client.ui.components.PostCardItem
import top.sakuraxy.client.ui.components.SakuTopAppBar
import top.sakuraxy.client.ui.components.UserAvatar
import top.sakuraxy.client.vm.AuthorViewModel

/** 作者主页：头部（头像/昵称/等级/简介 + 关注/私信）+ 文章/评论/收藏/粉丝四标签 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorScreen(
    app: SakuApp,
    authorId: Long,
    initialTab: String?,
    onBack: () -> Unit,
    onOpenPost: (Long) -> Unit,
    onOpenChat: (Long) -> Unit,
    onOpenCategory: (SiteParser.CardTerm) -> Unit,
) {
    val viewModel = viewModel(key = "author_$authorId") { AuthorViewModel(app, authorId, initialTab) }
    val state by viewModel.state.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current
    var picker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // 当前可见页 = 列表第一个可见项落在哪一页（翻页条随滚动联动；仅文章/收藏两个标签有分页）
    val visiblePage by remember(state.window) {
        derivedStateOf { state.window.pageAt(listState.firstVisibleItemIndex) }
    }
    LaunchedEffect(state.scrollTarget) {
        state.scrollTarget?.let { target ->
            if (kotlin.math.abs(target - listState.firstVisibleItemIndex) <= 30) {
                listState.animateScrollToItem(target)
            } else {
                listState.scrollToItem(target)
            }
            viewModel.consumeScrollTarget()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.message.collect { text ->
            text?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                viewModel.consumeMessage()
            }
        }
    }

    Scaffold(
        topBar = {
            SakuTopAppBar(
                title = state.info?.displayName ?: "作者主页",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        bottomBar = {
            val maxPage = state.maxPage
            if (state.pagedTab && maxPage != null && maxPage > 1 && state.cards.isNotEmpty()) {
                PagerBar(
                    currentPage = visiblePage,
                    maxPage = maxPage,
                    loading = state.loading || state.loadingMore,
                    onPrev = { viewModel.prevPage(visiblePage) },
                    onNext = { viewModel.nextPage(visiblePage) },
                    onOpenPicker = { picker = true },
                )
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        val error = state.error
        when {
            state.loading -> CenterLoading(Modifier.padding(padding))
            error != null -> ErrorRetry(error, onRetry = viewModel::retry, modifier = Modifier.padding(padding))
            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                AuthorHeader(
                    viewModel = viewModel,
                    onOpenChat = { onOpenChat(authorId) },
                )
                // 标签栏（横向可滚动，窄屏四个标签不被挤压换行）
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.info?.stats?.forEach { stat ->
                        FilterChip(
                            selected = state.tab == stat.tab,
                            onClick = { viewModel.setTab(stat.tab) },
                            label = { Text("${stat.label} ${stat.count}") },
                        )
                    }
                }
                when (state.tab) {
                    "comment" -> CommentList(state.comments, onOpenPost)
                    "follow" -> FollowList(
                        users = state.followList?.users.orEmpty(),
                        sub = state.followSub,
                        onSwitchSub = viewModel::switchFollowSub,
                        onToggleFollow = viewModel::toggleFollowUser,
                        onOpenAuthor = { id -> /* 已是作者页，忽略自跳 */ },
                    )
                    else -> CardList(viewModel, state, listState, onOpenPost, onOpenCategory)
                }
            }
        }
    }

    val maxPage = state.maxPage
    if (picker && maxPage != null) {
        PagePickerSheet(
            currentPage = visiblePage,
            maxPage = maxPage,
            onDismiss = { picker = false },
            onPick = { page ->
                picker = false
                viewModel.jumpTo(page)
            },
        )
    }
}

@Composable
private fun AuthorHeader(
    viewModel: AuthorViewModel,
    onOpenChat: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val info = state.info ?: return
    Surface(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            // 封面（矮条；无封面/加载失败时露出占位底图）
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp),
            ) {
                CoverPlaceholder(iconSize = 22.dp)
                if (!info.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = info.coverUrl,
                        contentDescription = "封面",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(url = info.avatarUrl, size = 56.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = info.displayName ?: "未知用户",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(6.dp))
                        LevelBadge(info.level)
                    }
                    info.description?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (!info.isSelf) {
                    Spacer(Modifier.width(8.dp))
                    if (info.canChat) {
                        IconButton(onClick = onOpenChat) {
                            Icon(Icons.Outlined.Chat, contentDescription = "私信")
                        }
                    }
                    FollowButton(
                        following = info.following,
                        busy = state.followBusy,
                        onClick = viewModel::toggleFollow,
                    )
                }
            }
        }
    }
}

@Composable
private fun FollowButton(following: Boolean, busy: Boolean, onClick: () -> Unit) {
    if (following) {
        OutlinedButton(onClick = onClick, enabled = !busy) { Text("已关注") }
    } else {
        Button(onClick = onClick, enabled = !busy) { Text("关注") }
    }
}

@Composable
private fun CardList(
    viewModel: AuthorViewModel,
    state: AuthorViewModel.UiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onOpenPost: (Long) -> Unit,
    onOpenCategory: (SiteParser.CardTerm) -> Unit,
) {
    if (state.cards.isEmpty() && !state.loading) {
        EmptyHint("暂无内容")
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.cards, key = { it.id ?: it.url.hashCode().toLong() }) { card ->
            PostCardItem(
                card = card,
                onClick = { card.id?.let(onOpenPost) },
                onTermClick = onOpenCategory,
            )
        }
        item {
            ListFooter(
                loading = state.loadingMore,
                canLoadMore = state.canLoadMore,
                error = null,
                page = state.page,
                maxPage = state.maxPage,
                onLoadMore = viewModel::loadMore,
                onRetry = viewModel::retry,
            )
        }
    }
}

@Composable
private fun CommentList(comments: List<AuthorComment>, onOpenPost: (Long) -> Unit) {
    if (comments.isEmpty()) {
        EmptyHint("暂无评论")
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(comments) { comment ->
            Surface(
                Modifier.fillMaxWidth().clickable {
                    comment.postUrl?.let { url ->
                        Regex("""/(\d+)/?$""").find(url)?.groupValues?.get(1)?.toLongOrNull()
                            ?.let(onOpenPost)
                    }
                },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(Modifier.padding(14.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            comment.text ?: "（评论已删除）",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = comment.postTitle.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            comment.replyTo?.let {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "@$it",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        comment.timeText.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FollowList(
    users: List<AuthorUserCard>,
    sub: String,
    onSwitchSub: (String) -> Unit,
    onToggleFollow: (AuthorUserCard) -> Unit,
    onOpenAuthor: (Long) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = sub == "followed",
                onClick = { onSwitchSub("followed") },
                label = { Text("粉丝") },
            )
            FilterChip(
                selected = sub == "follow",
                onClick = { onSwitchSub("follow") },
                label = { Text("关注") },
            )
        }
        if (users.isEmpty()) {
            EmptyHint(if (sub == "followed") "暂无粉丝" else "暂无关注")
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // key 兜底用下标，避免解析异常时（userId/name 同时缺失）产生重复 key 崩溃
                itemsIndexed(
                    users,
                    key = { index, user -> user.userId ?: ("idx$index").hashCode().toLong() },
                ) { _, user ->
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UserAvatar(
                                url = user.avatarUrl,
                                size = 44.dp,
                                modifier = Modifier.clickable { user.userId?.let(onOpenAuthor) },
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        user.name ?: "未知用户",
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    LevelBadge(user.level)
                                }
                                user.description?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            FollowButton(
                                following = user.following,
                                busy = false,
                                onClick = { onToggleFollow(user) },
                            )
                        }
                    }
                }
            }
        }
    }
}
