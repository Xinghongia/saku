package top.sakuraxy.client.ui.detail

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import top.sakuraxy.client.SakuApp
import top.sakuraxy.client.core.CashierInfo
import top.sakuraxy.client.core.ContentBlock
import top.sakuraxy.client.core.Html
import top.sakuraxy.client.core.PayBox
import top.sakuraxy.client.core.PayImage
import top.sakuraxy.client.core.PostComment
import top.sakuraxy.client.core.SiteParser
import top.sakuraxy.client.ui.AndroidActions
import top.sakuraxy.client.ui.auth.SliderCaptchaDialog
import top.sakuraxy.client.ui.components.ContentBlockItem
import top.sakuraxy.client.ui.components.ContentViewerDialog
import top.sakuraxy.client.ui.components.ErrorRetry
import top.sakuraxy.client.ui.components.ImageActionsSheet
import top.sakuraxy.client.ui.components.MiniChip
import top.sakuraxy.client.ui.components.SakuTopAppBar
import top.sakuraxy.client.ui.components.SiteOnlyDialog
import top.sakuraxy.client.ui.components.rememberSaveImage
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
    /** 全屏查看的付费图片（null = 未打开） */
    var viewerImage by remember { mutableStateOf<PayImage?>(null) }
    /** 全屏查看的正文图片（与付费图库互不相干） */
    var contentViewer by remember { mutableStateOf<ContentBlock.Image?>(null) }
    /** 长按正文图片弹出的操作菜单 */
    var contentImageMenu by remember { mutableStateOf<ContentBlock.Image?>(null) }
    val saveImage = rememberSaveImage(app.client.http)

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
            // 首帧即整页骨架（正文来自快速接口、头部来自慢速整页解析，避免互相挤位）
            state.loading -> DetailSkeletonPage(Modifier.padding(padding))

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
                // 作者行（头像/名字/徽章/等级 + 关注/私信）+ 计数行 + 面包屑；
                // 页面解析未返回前用骨架占住头部位置（正文始终在其下方，不被挤动）
                if (detail != null) {
                    val isSelf = state.myUserId != null && state.myUserId == detail.header.authorId
                    item {
                        PostHeaderSection(
                            header = detail.header,
                            following = state.following,
                            isSelf = isSelf,
                            busy = "follow" in state.busyKeys,
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
                } else if (!state.detailFailed) {
                    item { DetailHeaderSkeleton() }
                }

                // 正文（块渲染与使用手册共用 ContentBlockItem）；
                // 正文图片：点开全屏（可缩放），长按弹常用操作（保存到相册等）
                items(state.blocks) { block ->
                    ContentBlockItem(
                        block = block,
                        linkColor = linkColor,
                        onLink = ::handleLink,
                        onImageTap = { contentViewer = it },
                        onImageLongPress = { contentImageMenu = it },
                    )
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

                // 付费卡（未购 = 积分购买；已购 = 订单信息 + 下载按钮 / 付费图片）
                if (detail != null && detail.payBox !is PayBox.Free) {
                    item {
                        PayCard(
                            pay = detail.payBox,
                            resolvingHref = state.resolvingHref,
                            busy = "cashier" in state.busyKeys,
                            qr = state.qr,
                            onBuy = vm::openCashier,
                            onResolve = vm::resolveDownload,
                            onOpenImage = { image ->
                                viewerImage = image
                                vm.decodeQr(image.url)
                            },
                            onShowQrLink = { image, link ->
                                vm.showQrLink(image.label?.let { "网盘二维码 $it" } ?: "网盘二维码", link)
                            },
                            onRetryQr = vm::retryQr,
                        )
                    }
                }

                // 评分卡
                detail?.rating?.let { rating ->
                    item { RatingSection(rating = rating, busy = "rate" in state.busyKeys, onRate = vm::rate) }
                }

                // 标签
                if (detail != null && detail.tags.isNotEmpty()) {
                    item {
                        // 标签可点：进标签归档页（与网页点标签等价；复用分类页组件）
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            detail.tags.take(10).forEach { tag ->
                                MiniChip(
                                    text = "# ${tag.name}",
                                    container = MaterialTheme.colorScheme.surfaceVariant,
                                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                                    onClick = { onOpenCategory(tag.name, tag.url) },
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
                            // 各按钮只在自己的操作在途时禁用；分享是本地操作，始终可用
                            likeBusy = "like" in state.busyKeys,
                            favBusy = "fav" in state.busyKeys,
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
                            busy = "follow" in state.busyKeys,
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
                        busy = "comment" in state.busyKeys,
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
                            // 只禁这一条评论自己的点赞（其他评论不受影响）
                            busy = "clike-${comment.id}" in state.busyKeys,
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
    if (state.pendingComment != null && "comment" !in state.busyKeys) {
        SliderCaptchaDialog(
            httpClient = app.client.http,
            onDismiss = vm::cancelPendingComment,
            onSolved = { solution -> vm.submitCommentWithCaptcha(solution) },
        )
    }

    state.cashier?.let { info ->
        ConfirmBuyDialog(
            info = info,
            busy = "buy" in state.busyKeys,
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

    // 付费图片全屏查看（底部同时给出已识别的二维码链接）
    viewerImage?.let { image ->
        PayImageViewerDialog(
            image = image,
            qr = state.qr[image.url],
            onRetry = { vm.retryQr(image.url) },
            onDismiss = { viewerImage = null },
        )
    }

    // 正文图片全屏查看（双指缩放/双击放大；底部保存/复制/分享）
    contentViewer?.let { image ->
        ContentViewerDialog(
            url = image.url,
            description = image.alt,
            onDismiss = { contentViewer = null },
            onSave = { saveImage(image.url) },
            onCopy = { AndroidActions.copyToClipboard(context, image.url) },
            onShare = { AndroidActions.shareText(context, image.url) },
        )
    }

    // 长按正文图片：常用操作
    contentImageMenu?.let { image ->
        ImageActionsSheet(
            onDismiss = { contentImageMenu = null },
            onSave = { contentImageMenu = null; saveImage(image.url) },
            onCopy = { contentImageMenu = null; AndroidActions.copyToClipboard(context, image.url) },
            onShare = { contentImageMenu = null; AndroidActions.shareText(context, image.url) },
            onOpenBrowser = { contentImageMenu = null; AndroidActions.openInBrowser(context, image.url) },
        )
    }

    state.resolved?.let { link ->
        AlertDialog(
            onDismissRequest = vm::dismissResolved,
            title = { Text("链接（${link.label}）") },
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

/**
 * 付费图片全屏查看器：黑底 Fit 展示原图（也可自己截图留用），
 * 底部给二维码识别状态与已解出的网盘链接（复制 / 浏览器打开）；点任意空白处关闭。
 */
@Composable
private fun PayImageViewerDialog(
    image: PayImage,
    qr: DetailViewModel.QrState?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            AsyncImage(
                model = image.url,
                contentDescription = image.label?.let { "资源图片 $it" },
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 40.dp, bottom = 140.dp),
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                image.label?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                val link = qr?.link
                when {
                    link != null -> {
                        Text(
                            link,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilledTonalButton(onClick = { AndroidActions.copyToClipboard(context, link) }) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("复制链接")
                            }
                            // 黑底上的描边按钮显式用白字白边（浅色主题默认深色文字看不清）
                            OutlinedButton(
                                onClick = { AndroidActions.openInBrowser(context, link) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                            ) {
                                Text("浏览器打开")
                            }
                        }
                    }

                    qr?.decoding == true -> Text(
                        "正在识别二维码…",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )

                    qr?.failed == true -> TextButton(onClick = onRetry) {
                        Text("未识别到二维码，点此重试")
                    }

                    else -> Unit
                }
            }
        }
    }
}
