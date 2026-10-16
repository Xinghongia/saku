package top.sakuraxy.client.ui.detail

import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import top.sakuraxy.client.SakuApp
import top.sakuraxy.client.core.CashierInfo
import top.sakuraxy.client.core.ContentBlock
import top.sakuraxy.client.core.Html
import top.sakuraxy.client.core.PayBox
import top.sakuraxy.client.core.PostComment
import top.sakuraxy.client.core.SiteParser
import top.sakuraxy.client.ui.AndroidActions
import top.sakuraxy.client.ui.auth.SliderCaptchaDialog
import top.sakuraxy.client.ui.components.ErrorRetry
import top.sakuraxy.client.ui.components.MiniChip
import top.sakuraxy.client.ui.components.SakuTopAppBar
import top.sakuraxy.client.ui.components.SiteOnlyDialog
import top.sakuraxy.client.vm.DetailViewModel

/**
 * 文章详情（对齐网页文章页 2026-10-16 实测结构）：
 * 标题 → 作者行（关注/私信）+ 计数行 + 面包屑 → 正文 → 下载（免费 golink / 付费卡）
 * → 评分 → 标签 → 互动栏（赞/享/藏）→ 作者卡 → 评论（发表/点赞/回复）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    app: SakuApp,
    postId: Long,
    onBack: () -> Unit,
    onOpenPost: (Long) -> Unit,
    onOpenAuthor: (Long) -> Unit,
    onOpenChat: (Long) -> Unit,
    onOpenCategory: (name: String, url: String) -> Unit,
    onOpenLogin: () -> Unit,
) {
    val vm: DetailViewModel = viewModel(key = "detail-$postId") { DetailViewModel(app, postId) }
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    var replyTo by remember { mutableStateOf<PostComment?>(null) }

    fun handleLink(url: String) {
        val linkedId = SiteParser.postIdFromUrl(url)
        if (linkedId != null) onOpenPost(linkedId) else AndroidActions.openInBrowser(context, url)
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            SakuTopAppBar(
                title = state.post?.let { Html.toText(it.title.rendered) } ?: "加载中…",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    state.post?.let { post ->
                        IconButton(
                            onClick = {
                                // 系统分享面板（标题 + 链接），对应网页的「分享」下拉
                                AndroidActions.shareText(
                                    context,
                                    "${Html.toText(post.title.rendered)} ${post.link.orEmpty()}",
                                )
                            },
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = "分享")
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
                ErrorRetry(
                    state.error!!,
                    vm::load,
                    Modifier.padding(padding),
                )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
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

                val detail = state.detail
                // 作者行（头像/名字/徽章/等级 + 关注/私信）+ 计数行 + 面包屑
                if (detail != null) {
                    val isSelf = state.myUserId != null && state.myUserId == detail.header.authorId
                    item {
                        PostHeaderSection(
                            header = detail.header,
                            following = state.following,
                            isSelf = isSelf,
                            busy = state.busyKey != null,
                            onOpenAuthor = onOpenAuthor,
                            onOpenChat = onOpenChat,
                            onToggleFollow = vm::toggleFollow,
                        )
                    }
                    item { PostCountsRow(detail.header) }
                    if (detail.breadcrumb.isNotEmpty()) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                detail.breadcrumb.forEach { term ->
                                    MiniChip(
                                        text = term.name,
                                        onClick = { onOpenCategory(term.name, term.url) },
                                    )
                                }
                            }
                        }
                    }
                }

                // 正文
                items(state.blocks) { block ->
                    when (block) {
                        is ContentBlock.Paragraph -> Text(
                            text = block.text.toAnnotated(block.links, linkColor, ::handleLink),
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

                        ContentBlock.Separator -> androidx.compose.material3.HorizontalDivider()
                    }
                }

                // 免费文章：正文内 golink 下载按钮
                if (state.golinkButtons.isNotEmpty()) {
                    item { Text("下载", style = MaterialTheme.typography.titleMedium) }
                    items(state.golinkButtons) { button ->
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

                // 付费卡（未购 = 积分购买；已购 = 订单信息 + 网盘下载）
                if (detail != null && detail.payBox !is PayBox.Free) {
                    item {
                        PayCard(
                            pay = detail.payBox,
                            resolvingHref = state.resolvingHref,
                            busy = state.busyKey != null,
                            onBuy = vm::openCashier,
                            onResolve = vm::resolveDownload,
                        )
                    }
                }

                // 评分卡
                detail?.rating?.let { rating ->
                    item { RatingSection(rating = rating, busy = state.busyKey != null, onRate = vm::rate) }
                }

                // 标签
                if (detail != null && detail.tags.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            detail.tags.take(10).forEach { tag ->
                                MiniChip(
                                    text = "# $tag",
                                    container = MaterialTheme.colorScheme.surfaceVariant,
                                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // 互动栏（点赞 / 分享 / 收藏）
                detail?.let { d ->
                    item {
                        InteractionBar(
                            likeCount = d.actions.likeCount,
                            liked = d.actions.liked,
                            favoriteCount = d.actions.favoriteCount,
                            favorited = d.actions.favorited,
                            enabled = state.busyKey == null,
                            onLike = vm::toggleLike,
                            onShare = {
                                state.post?.let { post ->
                                    AndroidActions.shareText(
                                        context,
                                        "${Html.toText(post.title.rendered)} ${post.link.orEmpty()}",
                                    )
                                }
                            },
                            onFavorite = vm::toggleFavorite,
                        )
                    }
                }

                // 作者信息卡
                detail?.authorCard?.let { card ->
                    item {
                        val isSelf = state.myUserId != null && state.myUserId == card.authorId
                        AuthorCardSection(
                            card = card,
                            following = state.following,
                            isSelf = isSelf,
                            busy = state.busyKey != null,
                            onToggleFollow = vm::toggleFollow,
                            onOpenAuthor = onOpenAuthor,
                            onOpenPost = onOpenPost,
                        )
                    }
                }

                // 评论区
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("评论", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = (detail?.header?.commentCount ?: state.comments.size).toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    CommentComposer(
                        loggedIn = state.loggedIn,
                        replyToName = replyTo?.authorName,
                        busy = state.busyKey == "comment",
                        clearTick = state.commentSuccessTick,
                        onSubmit = { text ->
                            vm.requestSubmitComment(text, replyTo?.id ?: 0L)
                            replyTo = null
                        },
                        onCancelReply = { replyTo = null },
                        onOpenLogin = onOpenLogin,
                    )
                }
                if (state.comments.isEmpty()) {
                    item {
                        Text(
                            "还没有评论",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(state.comments, key = { it.id }) { comment ->
                        CommentRow(
                            comment = comment,
                            busy = state.busyKey != null,
                            onLike = { vm.toggleCommentLike(comment) },
                            onReply = { replyTo = comment },
                        )
                    }
                }
                if (state.commentsHasMore) {
                    item {
                        TextButton(
                            onClick = vm::loadMoreComments,
                            enabled = !state.commentsLoadingMore,
                        ) {
                            if (state.commentsLoadingMore) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text("加载更多评论")
                        }
                    }
                }
            }
        }
    }

    // ---- 弹窗 ----

    // 评论滑块（每次提交都要；服务端再要滑块时 pendingComment 保留 → 自动重弹）
    if (state.pendingComment != null && state.busyKey != "comment") {
        SliderCaptchaDialog(
            httpClient = app.client.http,
            onDismiss = vm::cancelPendingComment,
            onSolved = { solution -> vm.submitCommentWithCaptcha(solution) },
        )
    }

    state.cashier?.let { info ->
        ConfirmBuyDialog(
            info = info,
            busy = state.busyKey == "buy",
            onConfirm = vm::confirmBuy,
            onDismiss = vm::dismissCashier,
        )
    }

    if (state.needsLogin) {
        AlertDialog(
            onDismissRequest = vm::dismissNeedsLogin,
            title = { Text("需要登录") },
            text = { Text("该操作需要登录后才能使用（点赞无需登录）。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.dismissNeedsLogin()
                        onOpenLogin()
                    },
                ) { Text("去登录") }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissNeedsLogin) { Text("取消") }
            },
        )
    }

    if (state.siteOnlyHint) SiteOnlyDialog(onDismiss = vm::dismissSiteOnlyHint)

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
            text = { Text("$reason\n\n如果这篇需要登录或购买，请先完成后重试；链接可能已过期，可重新进入页面再试。") },
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

/** 积分购买确认框：价格与「我的积分」都来自收银台实时返回（VIP 价与普通价不同） */
@Composable
private fun ConfirmBuyDialog(
    info: CashierInfo,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认购买") },
        text = {
            Column {
                info.title?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                }
                Row(Modifier.fillMaxWidth()) {
                    Text("价格", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text("${info.priceText ?: "-"} 积分", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text("我的积分", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(info.myPointsText ?: "-", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "购买后将从账号扣除相应积分，与网页购买等效。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                Text(if (busy) "支付中…" else "立即支付")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
        },
    )
}

/** 段落文本 + 可点击链接（站内文章链接跳转进 App，其余打开浏览器）；链接色随主题 */
private fun String.toAnnotated(
    links: List<top.sakuraxy.client.core.TextLink>,
    linkColor: androidx.compose.ui.graphics.Color,
    onLink: (String) -> Unit,
) = buildAnnotatedString {
    append(this@toAnnotated)
    links.forEach { link ->
        addLink(
            LinkAnnotation.Url(
                url = link.url,
                styles = TextLinkStyles(
                    style = androidx.compose.ui.text.SpanStyle(
                        color = linkColor,
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
