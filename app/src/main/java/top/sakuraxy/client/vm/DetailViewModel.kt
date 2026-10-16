package top.sakuraxy.client.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.sakuraxy.client.SakuApp
import top.sakuraxy.client.PayImageQr
import top.sakuraxy.client.core.AjaxResult
import top.sakuraxy.client.core.CashierInfo
import top.sakuraxy.client.core.ContentBlock
import top.sakuraxy.client.core.ContentParser
import top.sakuraxy.client.core.Golink
import top.sakuraxy.client.core.PayBox
import top.sakuraxy.client.core.PayDownload
import top.sakuraxy.client.core.PayKind
import top.sakuraxy.client.core.PostComment
import top.sakuraxy.client.core.PostDetail
import top.sakuraxy.client.core.Site
import top.sakuraxy.client.core.SliderCaptcha
import top.sakuraxy.client.core.WpPost

/**
 * 文章详情：REST 正文 + 页面解析（作者/互动/评分/付费区/作者卡/评论）+ 写操作。
 *
 * 写操作统一模式（2026-10-16 设计）：
 * 互斥键防重入（同一操作在途时防双击；不同操作可并行，见 [UiState.busyKeys]）→ 快照 →
 * 乐观更新 → 成功用响应 `action`（add/remove=最终态）
 * 与 `cuont`（新计数）校准 → 失败回滚 + 提示。站点的 JS 在失败时也把计数写成 0，
 * 那是它的 bug，客户端不跟。
 *
 * 登录态：订阅 session.revision 并按 isLoggedIn() **变化**去重（冷启动 restore 也会
 * bump revision，不能双加载）；变化后整页重取并丢弃乐观态（匿名赞与登录赞是两笔记录，
 * 旧 nonce 换会话后失效）。
 */
class DetailViewModel(private val app: SakuApp, private val postId: Long) : ViewModel() {

    data class ResolvedLink(val label: String, val url: String)

    /** 待提交的评论（等待滑块验证） */
    data class PendingComment(val content: String, val parent: Long)

    /** 付费图片的二维码识别状态（键 = 图片地址） */
    data class QrState(val decoding: Boolean = false, val link: String? = null, val failed: Boolean = false)

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val post: WpPost? = null,
        val blocks: List<ContentBlock> = emptyList(),
        /** 免费文章的 golink 下载按钮（正文内） */
        val golinkButtons: List<Golink.Button> = emptyList(),
        /** 页面解析（作者/计数/互动/评分/付费区/作者卡/面包屑/标签） */
        val detail: PostDetail? = null,
        /** 页面解析是否失败（详情页骨架屏据此让位给正文；失败原因走 message 提示） */
        val detailFailed: Boolean = false,
        /** 当前用户是否已关注作者（头部与作者卡两处按钮共用） */
        val following: Boolean = false,
        val loggedIn: Boolean = false,
        /** 当前登录用户 id（用于隐藏自己文章上的关注/私信按钮；未登录为 null） */
        val myUserId: Long? = null,
        val comments: List<PostComment> = emptyList(),
        val commentsPage: Int = 1,
        val commentsHasMore: Boolean = false,
        val commentsLoadingMore: Boolean = false,
        /** 写操作互斥键集合（like/fav/follow/rate/cashier/buy/comment/clike-{id}）：
         *  同一键在途时防重复提交；**不同键可并行**——点赞/收藏/关注是互不影响的字段，
         *  一个操作在途不应把全页按钮都禁掉（2026-10-16 驾驶员走查反馈） */
        val busyKeys: Set<String> = emptySet(),
        /** 一次性提示（消费后置空） */
        val message: String? = null,
        /** 需要登录提示（收藏/评分/评论/购买/关注） */
        val needsLogin: Boolean = false,
        /** 真钱支付：请到网站操作 */
        val siteOnlyHint: Boolean = false,
        /** 待提交评论（非空时 UI 弹滑块） */
        val pendingComment: PendingComment? = null,
        /** 评论提交成功计数（UI 据此清空输入框） */
        val commentSuccessTick: Int = 0,
        /** 收银台确认框数据（积分购买） */
        val cashier: CashierInfo? = null,
        /** 付费图片的二维码识别状态（键 = 图片地址；已购图片型专用） */
        val qr: Map<String, QrState> = emptyMap(),
        val resolvingHref: String? = null,
        val resolved: ResolvedLink? = null,
        val resolveError: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var pageUrl: String? = null

    init {
        load()
        viewModelScope.launch {
            var last = app.client.session.isLoggedIn()
            _state.update { it.copy(loggedIn = last) }
            app.client.session.revision.drop(1).collect {
                val now = app.client.session.isLoggedIn()
                if (now != last) {
                    last = now
                    // 登录态变化：整页重取（新 nonce/新互动状态），丢弃乐观态
                    pageUrl?.let { loadDetail(it) }
                    _state.update { it.copy(loggedIn = now) }
                    loadMyUserId()
                }
            }
        }
    }

    // ---------- 加载 ----------

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, detailFailed = false) }
            try {
                val post = app.client.rest.post(postId)
                val content = post.content?.rendered.orEmpty()
                pageUrl = post.link?.takeIf { it.isNotBlank() } ?: "${Site.BASE_URL}/$postId"
                _state.update {
                    it.copy(
                        loading = false,
                        post = post,
                        blocks = ContentParser.parse(content),
                        golinkButtons = Golink.extractButtons(content),
                        loggedIn = app.client.session.isLoggedIn(),
                    )
                }
                loadMyUserId()
                loadDetail(pageUrl!!)
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "加载失败，请重试") }
            }
        }
    }

    /** 页面信息（互动/付费/评论首屏）；失败不影响正文展示（骨架屏让位，Toast 提示原因） */
    private suspend fun loadDetail(link: String) {
        runCatching { app.client.site.postDetail(link) }
            .onSuccess { detail ->
                _state.update {
                    it.copy(
                        detail = detail,
                        detailFailed = false,
                        following = detail.header.following,
                        comments = detail.commentPage?.comments ?: emptyList(),
                        commentsPage = detail.commentPage?.currentPage ?: 1,
                        commentsHasMore = detail.commentPage?.nextPageUrl != null,
                    )
                }
                autoDecodeQr(detail)
            }
            .onFailure {
                _state.update { s ->
                    s.copy(detailFailed = true, message = "页面信息加载失败：${it.message ?: "网络异常"}")
                }
            }
    }

    /** 登录用户 id（失败不致命：仅影响自己文章上关注/私信按钮的隐藏） */
    private suspend fun loadMyUserId() {
        val id = if (app.client.session.isLoggedIn()) {
            runCatching { app.client.users.currentUser()?.id }.getOrNull()
        } else null
        _state.update { it.copy(myUserId = id) }
    }

    /** 重新拉页面解析（购买成功后刷新付费区/评论提交后刷新列表） */
    private fun reloadDetail() {
        pageUrl?.let { link -> viewModelScope.launch { loadDetail(link) } }
    }

    // ---------- 点赞 / 收藏 ----------

    /** 点赞（匿名可点，与网页一致） */
    fun toggleLike() = toggleAction(
        key = "like",
        current = _state.value.detail?.actions?.liked == true,
        count = _state.value.detail?.actions?.likeCount,
        apply = { liked, count ->
            _state.update { s ->
                s.copy(detail = s.detail?.copy(actions = s.detail.actions.copy(liked = liked, likeCount = count)))
            }
        },
        post = { nonce -> app.client.post.like(postId, nonce) },
    )

    /** 收藏（需要登录） */
    fun toggleFavorite() {
        if (!_state.value.loggedIn) {
            _state.update { it.copy(needsLogin = true) }
            return
        }
        toggleAction(
            key = "fav",
            current = _state.value.detail?.actions?.favorited == true,
            count = _state.value.detail?.actions?.favoriteCount,
            apply = { active, count ->
                _state.update { s ->
                    s.copy(detail = s.detail?.copy(actions = s.detail.actions.copy(favorited = active, favoriteCount = count)))
                }
            },
            post = { nonce -> app.client.post.favorite(postId, nonce) },
        )
    }

    /** 点赞/收藏共用：占用键防重入 → 乐观 → action/cuont 校准 → 失败回滚 */
    private fun toggleAction(
        key: String,
        current: Boolean,
        count: Int?,
        apply: (Boolean, Int?) -> Unit,
        post: suspend (String) -> AjaxResult,
    ) {
        val nonce = _state.value.detail?.header?.postActionNonce
        if (nonce.isNullOrBlank()) {
            _state.update { it.copy(message = "页面数据未就绪，请稍后重试") }
            return
        }
        if (!beginBusy(key)) return
        val target = !current
        val optimisticCount = ((count ?: 0) + if (target) 1 else -1).coerceAtLeast(0)
        apply(target, optimisticCount)
        viewModelScope.launch {
            try {
                when (val r = post(nonce)) {
                    is AjaxResult.Ok -> {
                        val active = r.activeState() ?: target
                        val fresh = r.newCount() ?: optimisticCount
                        apply(active, fresh)
                        app.notifyContentChanged()
                    }
                    is AjaxResult.Fail -> {
                        apply(current, count) // 回滚快照
                        _state.update { it.copy(message = r.msg, needsLogin = it.needsLogin || r.msg.contains("登录")) }
                    }
                    is AjaxResult.NeedsCaptcha -> {
                        apply(current, count)
                        _state.update { it.copy(message = r.msg) }
                    }
                    is AjaxResult.NotJson -> {
                        apply(current, count)
                        _state.update { it.copy(message = "操作失败：站点响应异常") }
                    }
                }
            } catch (e: Exception) {
                apply(current, count)
                _state.update { it.copy(message = e.message ?: "操作失败，请重试") }
            } finally {
                endBusy(key)
            }
        }
    }

    // ---------- 写操作互斥键 ----------

    /** 占用互斥键；同一键已在途返回 false（防双击），不同键互不影响 */
    private fun beginBusy(key: String): Boolean {
        if (key in _state.value.busyKeys) return false
        _state.update { it.copy(busyKeys = it.busyKeys + key) }
        return true
    }

    private fun endBusy(key: String) = _state.update { it.copy(busyKeys = it.busyKeys - key) }

    // ---------- 关注作者（头部与作者卡同步） ----------

    fun toggleFollow() {
        val st = _state.value
        if (!st.loggedIn) {
            _state.update { it.copy(needsLogin = true) }
            return
        }
        val authorId = st.detail?.authorCard?.authorId ?: st.detail?.header?.authorId ?: return
        val nonce = st.detail?.header?.postActionNonce
        if (nonce.isNullOrBlank()) {
            _state.update { it.copy(message = "页面数据未就绪，请稍后重试") }
            return
        }
        val prev = st.following
        if (!beginBusy("follow")) return
        _state.update { it.copy(following = !prev) }
        viewModelScope.launch {
            try {
                when (val r = app.client.author.follow(authorId, nonce)) {
                    is AjaxResult.Ok -> {
                        _state.update {
                            it.copy(message = r.msg, following = r.activeState() ?: !prev)
                        }
                        app.notifyContentChanged()
                    }
                    is AjaxResult.Fail ->
                        _state.update { it.copy(following = prev, message = r.msg, needsLogin = it.needsLogin || r.msg.contains("登录")) }
                    else ->
                        _state.update { it.copy(following = prev, message = "操作失败，请重试") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(following = prev, message = e.message ?: "操作失败，请重试") }
            } finally {
                endBusy("follow")
            }
        }
    }

    // ---------- 评分 ----------

    fun rate(stars: Int) {
        val st = _state.value
        if (!st.loggedIn) {
            _state.update { it.copy(needsLogin = true) }
            return
        }
        val rating = st.detail?.rating ?: return
        if (rating.rated) {
            _state.update { it.copy(message = "您已经评过分了") }
            return
        }
        val nonce = rating.nonce
        if (nonce.isNullOrBlank()) {
            _state.update { it.copy(message = "评分暂不可用，请稍后重试") }
            return
        }
        if (!beginBusy("rate")) return
        viewModelScope.launch {
            try {
                when (val r = app.client.post.rate(postId, stars, nonce)) {
                    is AjaxResult.Ok -> {
                        val avg = r.json["average"]?.jsonPrimitive?.contentOrNull
                        val count = r.json["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                        _state.update { s ->
                            val prev = s.detail?.rating
                            s.copy(
                                detail = if (s.detail != null && prev != null) {
                                    s.detail.copy(
                                        rating = prev.copy(
                                            average = avg ?: prev.average,
                                            votes = count ?: prev.votes,
                                            rated = true,
                                        ),
                                    )
                                } else s.detail,
                                message = r.msg.ifBlank { "感谢您的评分" },
                            )
                        }
                    }
                    is AjaxResult.Fail ->
                        _state.update { s ->
                            val prev = s.detail?.rating
                            s.copy(
                                // 「您已经评过分了」属信息态：标记已评，不再重复提交
                                detail = if (r.msg.contains("评过") && s.detail != null && prev != null) {
                                    s.detail.copy(rating = prev.copy(rated = true))
                                } else s.detail,
                                message = r.msg,
                            )
                        }
                    else -> _state.update { it.copy(message = "评分失败，请重试") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "评分失败，请重试") }
            } finally {
                endBusy("rate")
            }
        }
    }

    // ---------- 积分购买 ----------

    /** 打开收银台：积分支付 → 确认框；真钱支付 → 请到网站操作 */
    fun openCashier() {
        if (!_state.value.loggedIn) {
            _state.update { it.copy(needsLogin = true) }
            return
        }
        if (!beginBusy("cashier")) return
        viewModelScope.launch {
            try {
                val info = app.client.post.cashier(postId)
                when {
                    info == null -> _state.update { it.copy(message = "收银台加载失败，请重试") }
                    info.action == "points_initiate_pay" -> _state.update { it.copy(cashier = info) }
                    else -> _state.update { it.copy(siteOnlyHint = true) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "收银台加载失败，请重试") }
            } finally {
                endBusy("cashier")
            }
        }
    }

    /** 确认积分支付（真实扣积分；成功整页重取 → 付费区变已购） */
    fun confirmBuy() {
        val info = _state.value.cashier ?: return
        if (!beginBusy("buy")) return
        viewModelScope.launch {
            try {
                when (val r = app.client.post.buyPoints(info)) {
                    is AjaxResult.Ok -> {
                        _state.update { it.copy(cashier = null, message = r.msg.ifBlank { "购买成功" }) }
                        app.notifyContentChanged() // 积分已扣，我的页资产卡需刷新
                        reloadDetail()
                    }
                    is AjaxResult.Fail ->
                        _state.update { it.copy(cashier = null, message = r.msg) }
                    is AjaxResult.NeedsCaptcha ->
                        _state.update { it.copy(cashier = null, message = r.msg) }
                    is AjaxResult.NotJson ->
                        _state.update { it.copy(cashier = null, message = "购买失败：站点响应异常") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(cashier = null, message = e.message ?: "购买失败，请重试") }
            } finally {
                endBusy("buy")
            }
        }
    }

    fun dismissCashier() = _state.update { it.copy(cashier = null) }

    // ---------- 付费图片的二维码识别 ----------

    /** 同时最多 2 张图在解码（图片本身很小，纯粹克制起见） */
    private val qrSemaphore = Semaphore(2)

    /** 已购图片型资源：自动识别前 8 张（更多时点开哪张解哪张；失败只手动重试） */
    private fun autoDecodeQr(detail: PostDetail) {
        val pay = detail.payBox as? PayBox.Paid ?: return
        if (pay.kind != PayKind.Images) return
        pay.images.take(8).forEach { image -> decodeQr(image.url) }
    }

    /** 解码单张图（幂等：已有终态/在途的地址直接跳过——购买后重载、登录态刷新都会走到这） */
    fun decodeQr(url: String) {
        val existing = _state.value.qr[url]
        if (existing != null && (existing.decoding || existing.link != null || existing.failed)) return
        _state.update { it.copy(qr = it.qr + (url to QrState(decoding = true))) }
        viewModelScope.launch {
            val link = qrSemaphore.withPermit {
                runCatching { PayImageQr.fetchAndDecode(app.client.http, url) }.getOrNull()
            }
            _state.update { s ->
                s.copy(qr = s.qr + (url to QrState(decoding = false, link = link, failed = link == null)))
            }
        }
    }

    /** 识别失败后的手动重试（先清旧状态再解） */
    fun retryQr(url: String) {
        if (_state.value.qr[url]?.decoding == true) return
        _state.update { it.copy(qr = it.qr - url) }
        decodeQr(url)
    }

    /** 已识别的二维码链接 → 复用通用「链接」对话框（复制/浏览器打开） */
    fun showQrLink(label: String?, url: String) =
        _state.update { it.copy(resolved = ResolvedLink(label ?: "网盘链接", url)) }

    // ---------- 评论 ----------

    fun loadMoreComments() {
        if (_state.value.commentsLoadingMore || !_state.value.commentsHasMore) return
        val link = pageUrl ?: return
        val next = _state.value.commentsPage + 1
        _state.update { it.copy(commentsLoadingMore = true) }
        viewModelScope.launch {
            try {
                val page = app.client.site.commentsPage(link, next)
                _state.update { s ->
                    s.copy(
                        commentsLoadingMore = false,
                        comments = (s.comments + (page?.comments ?: emptyList())).distinctBy { it.id },
                        commentsPage = page?.currentPage ?: next,
                        commentsHasMore = page?.nextPageUrl != null,
                    )
                }
            } catch (_: Exception) {
                _state.update { it.copy(commentsLoadingMore = false, message = "评论加载失败，请重试") }
            }
        }
    }

    /** 点「发表」：未登录提示；已登录进入滑块前置流程（每次提交都要滑块） */
    fun requestSubmitComment(content: String, parent: Long) {
        val st = _state.value
        if (!st.loggedIn) {
            _state.update { it.copy(needsLogin = true) }
            return
        }
        val text = content.trim()
        if (text.isBlank()) return
        if (st.detail?.header?.commentFormNonce.isNullOrBlank()) {
            _state.update { it.copy(message = "评论表单失效，请重新进入页面") }
            return
        }
        _state.update { it.copy(pendingComment = PendingComment(text, parent)) }
    }

    /** 滑块完成 → 带 captcha 提交；服务端再要滑块则保留待提交（UI 重弹） */
    fun submitCommentWithCaptcha(solution: SliderCaptcha.Solution?) {
        val pending = _state.value.pendingComment ?: return
        val nonce = _state.value.detail?.header?.commentFormNonce ?: return
        if (!beginBusy("comment")) return
        viewModelScope.launch {
            try {
                when (val r = app.client.post.submitComment(postId, pending.content, pending.parent, nonce, solution)) {
                    is AjaxResult.Ok -> {
                        val approved = r.json["comment"]?.jsonObject
                            ?.get("comment_approved")?.jsonPrimitive?.contentOrNull
                        val tip = when {
                            approved == "0" -> "评论已提交，审核通过后显示"
                            r.msg.isNotBlank() -> r.msg
                            else -> "评论成功"
                        }
                        _state.update {
                            it.copy(
                                pendingComment = null,
                                message = tip,
                                commentSuccessTick = it.commentSuccessTick + 1,
                            )
                        }
                        app.notifyContentChanged() // 评论数变化，我的页统计需刷新
                        refreshComments()
                    }
                    is AjaxResult.NeedsCaptcha ->
                        _state.update { it.copy(message = r.msg) } // 保留 pendingComment → UI 重弹滑块
                    is AjaxResult.Fail ->
                        _state.update { it.copy(pendingComment = null, message = r.msg, needsLogin = it.needsLogin || r.msg.contains("登录")) }
                    is AjaxResult.NotJson ->
                        _state.update { it.copy(pendingComment = null, message = "评论失败：站点响应异常") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(pendingComment = null, message = e.message ?: "评论失败，请重试") }
            } finally {
                endBusy("comment")
            }
        }
    }

    fun cancelPendingComment() = _state.update { it.copy(pendingComment = null) }

    /** 评论提交成功后重取第 1 页（新评论在最前） */
    private fun refreshComments() {
        val link = pageUrl ?: return
        viewModelScope.launch {
            runCatching { app.client.site.commentsPage(link, 1) }.onSuccess { page ->
                _state.update {
                    it.copy(
                        comments = page?.comments ?: emptyList(),
                        commentsPage = page?.currentPage ?: 1,
                        commentsHasMore = page?.nextPageUrl != null,
                    )
                }
            }
        }
    }

    /** 评论点赞（匿名可点，与文章点赞一致） */
    fun toggleCommentLike(comment: PostComment) {
        val nonce = _state.value.detail?.header?.postActionNonce
        if (nonce.isNullOrBlank()) return
        val key = "clike-${comment.id}"
        if (!beginBusy(key)) return
        val target = !comment.liked
        val optimistic = ((comment.likeCount ?: 0) + if (target) 1 else -1).coerceAtLeast(0)
        updateComment(comment.id) { it.copy(liked = target, likeCount = optimistic) }
        viewModelScope.launch {
            try {
                when (val r = app.client.post.commentLike(comment.id, nonce)) {
                    is AjaxResult.Ok -> updateComment(comment.id) {
                        it.copy(liked = r.activeState() ?: target, likeCount = r.newCount() ?: optimistic)
                    }
                    is AjaxResult.Fail -> {
                        updateComment(comment.id) { it.copy(liked = comment.liked, likeCount = comment.likeCount) }
                        _state.update { it.copy(message = r.msg, needsLogin = it.needsLogin || r.msg.contains("登录")) }
                    }
                    else -> updateComment(comment.id) { it.copy(liked = comment.liked, likeCount = comment.likeCount) }
                }
            } catch (_: Exception) {
                updateComment(comment.id) { it.copy(liked = comment.liked, likeCount = comment.likeCount) }
            } finally {
                endBusy(key)
            }
        }
    }

    private fun updateComment(id: Long, f: (PostComment) -> PostComment) {
        _state.update { s -> s.copy(comments = s.comments.map { if (it.id == id) f(it) else it }) }
    }

    // ---------- 下载 ----------

    /** 免费文章 golink 按钮（不跳过站点自己的跳转步骤） */
    fun resolve(button: Golink.Button) = resolveUrl(button.href, button.label)

    /** 已购文章的资源下载（pay-download 链接同样先经站点跳转页） */
    fun resolveDownload(download: PayDownload) = resolveUrl(download.url, download.label)

    private fun resolveUrl(href: String, label: String) {
        if (_state.value.resolvingHref != null) return
        viewModelScope.launch {
            _state.update { it.copy(resolvingHref = href, resolveError = null) }
            when (val result = Golink.resolve(app.client.http, href)) {
                is Golink.ResolveResult.Resolved ->
                    _state.update {
                        it.copy(resolvingHref = null, resolved = ResolvedLink(label, result.url))
                    }
                is Golink.ResolveResult.Failed ->
                    _state.update { it.copy(resolvingHref = null, resolveError = result.reason) }
            }
        }
    }

    // ---------- 提示与对话框 ----------

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun dismissNeedsLogin() = _state.update { it.copy(needsLogin = false) }

    fun dismissSiteOnlyHint() = _state.update { it.copy(siteOnlyHint = false) }

    fun dismissResolved() = _state.update { it.copy(resolved = null) }

    fun dismissResolveError() = _state.update { it.copy(resolveError = null) }
}

/** action.php 响应里的最终状态（add=已操作 / 其他=未操作）；无该字段返回 null */
private fun AjaxResult.activeState(): Boolean? {
    val json = (this as? AjaxResult.Ok)?.json ?: return null
    val action = json["action"]?.jsonPrimitive?.contentOrNull ?: return null
    return action == "add"
}

/** action.php 响应里的新计数（cuont，js 端同名）；无该字段返回 null */
private fun AjaxResult.newCount(): Int? {
    val json = (this as? AjaxResult.Ok)?.json ?: return null
    return json["cuont"]?.jsonPrimitive?.intOrNull
        ?: json["cuont"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
}
