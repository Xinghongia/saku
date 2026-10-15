package top.sakuraxy.client.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import top.sakuraxy.client.SakuApp
import top.sakuraxy.client.core.ContentBlock
import top.sakuraxy.client.core.Html
import top.sakuraxy.client.core.SiteParser
import top.sakuraxy.client.core.WpComment
import top.sakuraxy.client.ui.AndroidActions
import top.sakuraxy.client.ui.components.MiniChip
import top.sakuraxy.client.ui.components.StatsRow
import top.sakuraxy.client.vm.DetailViewModel

/** 文章详情：正文（含内链）、作者与统计、分类面包屑、标签、下载、评论 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    app: SakuApp,
    postId: Long,
    onBack: () -> Unit,
    onOpenPost: (Long) -> Unit,
    onOpenCategory: (name: String, url: String) -> Unit,
    onOpenLogin: () -> Unit,
) {
    val vm: DetailViewModel = viewModel(key = "detail-$postId") { DetailViewModel(app, postId) }
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    fun handleLink(url: String) {
        val linkedId = SiteParser.postIdFromUrl(url)
        if (linkedId != null) onOpenPost(linkedId) else AndroidActions.openInBrowser(context, url)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.post?.let { Html.toText(it.title.rendered) } ?: "加载中…",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    state.post?.link?.let { link ->
                        IconButton(onClick = { AndroidActions.openInBrowser(context, link) }) {
                            Icon(Icons.Outlined.Share, contentDescription = "在浏览器打开")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                Alignment.Center,
            ) { CircularProgressIndicator() }

            state.error != null ->
                top.sakuraxy.client.ui.components.ErrorRetry(
                    state.error!!,
                    vm::load,
                    Modifier.padding(padding),
                )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 标题
                state.post?.let { post ->
                    item {
                        Text(
                            text = Html.toText(post.title.rendered),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // 作者 + 时间 + 统计
                val stats = state.stats
                if (stats != null) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (stats.authorAvatar != null) {
                                    AsyncImage(
                                        model = stats.authorAvatar,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                stats.authorName?.let { name ->
                                    Text(name, style = MaterialTheme.typography.labelLarge)
                                }
                                stats.dateTitle?.let { date ->
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = date,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                StatsRow(
                                    commentCount = stats.commentCount,
                                    viewCount = stats.viewCount,
                                    likeCount = stats.likeCount,
                                )
                            }
                            if (stats.breadcrumbCategories.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    stats.breadcrumbCategories.forEach { term ->
                                        MiniChip(
                                            text = term.name,
                                            onClick = { onOpenCategory(term.name, term.url) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item { HorizontalDivider() }
                }

                // 正文
                items(state.blocks) { block ->
                    when (block) {
                        is ContentBlock.Paragraph -> Text(
                            text = block.text.toAnnotated(context, block.links, ::handleLink),
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        is ContentBlock.Heading -> Text(
                            text = block.text,
                            style = MaterialTheme.typography.titleMedium,
                        )

                        is ContentBlock.Image -> AsyncImage(
                            model = block.url,
                            contentDescription = block.alt,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp)),
                        )

                        ContentBlock.Separator -> HorizontalDivider()
                    }
                }

                // 标签
                if (!stats?.tags.isNullOrEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            stats!!.tags.take(10).forEach { tag ->
                                MiniChip(
                                    text = "# $tag",
                                    container = MaterialTheme.colorScheme.surfaceVariant,
                                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // 下载
                if (state.buttons.isNotEmpty()) {
                    item { HorizontalDivider() }
                    item { Text("下载", style = MaterialTheme.typography.titleMedium) }
                    items(state.buttons) { button ->
                        OutlinedButton(
                            onClick = { vm.resolve(button) },
                            enabled = state.resolvingHref == null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (state.resolvingHref == button.href) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Icon(
                                    Icons.Outlined.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(button.label.ifBlank { "获取下载链接" })
                        }
                    }
                }

                // 评论
                item { HorizontalDivider() }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("评论", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = (stats?.commentCount ?: state.comments.size).toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state.comments.isEmpty()) {
                    item {
                        Text(
                            if (state.commentsLoading) "评论加载中…" else "还没有评论（发表评论需登录，见「我的」）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(state.comments, key = { it.id }) { comment ->
                        CommentItem(comment)
                    }
                    if (state.commentsCanLoadMore) {
                        item {
                            TextButton(onClick = vm::loadMoreComments) { Text("加载更多评论") }
                        }
                    }
                }
            }
        }
    }

    state.resolved?.let { link ->
        AlertDialog(
            onDismissRequest = vm::dismissResolved,
            title = { Text("下载链接（${link.label}）") },
            text = { Text(link.url, style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                TextButton(
                    onClick = {
                        AndroidActions.copyToClipboard(context, link.url)
                        vm.dismissResolved()
                    },
                ) { Text("复制链接") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { AndroidActions.openInBrowser(context, link.url) }) {
                        Text("浏览器打开")
                    }
                    TextButton(onClick = vm::dismissResolved) { Text("关闭") }
                }
            },
        )
    }

    state.resolveError?.let { reason ->
        AlertDialog(
            onDismissRequest = vm::dismissResolveError,
            title = { Text("没能获取到下载链接") },
            text = { Text("$reason\n\n如果这篇需要登录才能查看，请先登录后再试。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.dismissResolveError()
                        onOpenLogin()
                    },
                ) { Text("去登录") }
            },
            dismissButton = {
                Row {
                    state.post?.link?.let { link ->
                        TextButton(
                            onClick = {
                                AndroidActions.openInBrowser(context, link)
                                vm.dismissResolveError()
                            },
                        ) { Text("打开网页版") }
                    }
                    TextButton(onClick = vm::dismissResolveError) { Text("关闭") }
                }
            },
        )
    }
}

/** 段落文本 + 可点击链接（站内文章链接跳转进 App，其余打开浏览器） */
private fun String.toAnnotated(
    context: android.content.Context,
    links: List<top.sakuraxy.client.core.TextLink>,
    onLink: (String) -> Unit,
) = buildAnnotatedString {
    append(this@toAnnotated)
    links.forEach { link ->
        addLink(
            LinkAnnotation.Url(
                url = link.url,
                styles = TextLinkStyles(
                    style = androidx.compose.ui.text.SpanStyle(
                        color = androidx.compose.ui.graphics.Color(0xFFB03A73),
                        textDecoration = TextDecoration.Underline,
                    ),
                ),
                linkInteractionListener = { onLink(link.url) },
            ),
            start = link.start,
            end = link.end,
        )
    }
}

@Composable
private fun CommentItem(comment: WpComment) {
    Row(Modifier.fillMaxWidth()) {
        comment.avatarUrl?.let { avatar ->
            AsyncImage(
                model = avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
            )
            Spacer(Modifier.width(10.dp))
        }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.authorName.ifBlank { "匿名" },
                    style = MaterialTheme.typography.labelLarge,
                )
                comment.date?.let { date ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = date.take(10),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (comment.parent != 0L) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "回复",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = Html.toText(comment.content.rendered),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
