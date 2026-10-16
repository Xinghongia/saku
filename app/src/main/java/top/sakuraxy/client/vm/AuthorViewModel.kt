package top.sakuraxy.client.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.sakuraxy.client.SakuApp
import top.sakuraxy.client.core.AjaxResult
import top.sakuraxy.client.core.AuthorComment
import top.sakuraxy.client.core.AuthorFollowList
import top.sakuraxy.client.core.AuthorInfo
import top.sakuraxy.client.core.AuthorUserCard
import top.sakuraxy.client.core.ChatMessage
import top.sakuraxy.client.core.ChatSummary
import top.sakuraxy.client.core.MsgTab
import top.sakuraxy.client.core.SiteParser

/**
 * 作者主页：头部 + 四个标签（文章/评论/收藏/粉丝）+ 关注。
 * tab 切换与分页直接请求整页（站点服务端渲染激活标签，客户端不做 ajax 片段拼装）。
 * 文章/收藏两个标签带分页（PageWindow 窗口模型，与列表页一致）。
 */
class AuthorViewModel(
    private val app: SakuApp,
    private val authorId: Long,
    initialTab: String?,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val loadingMore: Boolean = false,
        val error: String? = null,
        val info: AuthorInfo? = null,
        val tab: String = "post",
        val cards: List<SiteParser.PostCard> = emptyList(),
        val comments: List<AuthorComment> = emptyList(),
        val followList: AuthorFollowList? = null,
        /** 粉丝 tab 内当前查看的子列表：followed=粉丝 / follow=关注 */
        val followSub: String = "followed",
        val window: PageWindow = PageWindow(),
        val followBusy: Boolean = false,
        /** 一次性滚动目标（列表项下标）；界面滚动后调 consumeScrollTarget 清空 */
        val scrollTarget: Int? = null,
    ) {
        val page: Int get() = window.loadedMax
        val maxPage: Int? get() = window.maxPage
        val pagedTab: Boolean get() = tab in PAGED_TABS
        val canLoadMore: Boolean get() = pagedTab && maxPage != null && page < (maxPage ?: 1)

        companion object {
            val PAGED_TABS = setOf("post", "favorite")
        }
    }

    private val _state = MutableStateFlow(UiState(tab = initialTab ?: "post"))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        load(1, Mode.Reset)
    }

    fun setTab(tab: String) {
        if (tab == _state.value.tab) return
        _state.update { it.copy(tab = tab) }
        load(1, Mode.Reset)
    }

    /** 列表末尾「加载更多」：追加下一页（不动滚动位置） */
    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || !current.canLoadMore) return
        load(current.window.loadedMax + 1, Mode.Append)
    }

    fun retry() = load(1, Mode.Reset)

    /** 上一页 / 下一页（visiblePage = 由列表滚动位置换算的当前页） */
    fun prevPage(visiblePage: Int) = seek(visiblePage - 1)

    fun nextPage(visiblePage: Int) = seek(visiblePage + 1)

    /** 跳页（页码格子选择） */
    fun jumpTo(page: Int) = seek(page)

    private fun seek(page: Int) {
        val s = _state.value
        val max = s.window.maxPage ?: return
        if (!s.pagedTab || page !in 1..max || s.loading || s.loadingMore) return
        when (val action = s.window.actionFor(page)) {
            is PageWindow.Action.ScrollTo -> _state.update { it.copy(scrollTarget = action.index) }
            PageWindow.Action.Append -> load(page, Mode.Append, scrollToStart = true)
            PageWindow.Action.Prepend -> load(page, Mode.Prepend)
            PageWindow.Action.Reset -> load(page, Mode.Reset)
        }
    }

    fun consumeScrollTarget() = _state.update { it.copy(scrollTarget = null) }

    private enum class Mode { Reset, Append, Prepend }

    private fun load(page: Int, mode: Mode, scrollToStart: Boolean = false) {
        viewModelScope.launch {
            _state.update {
                if (mode == Mode.Reset) it.copy(loading = true, error = null) else it.copy(loadingMore = true)
            }
            try {
                val tab = _state.value.tab
                val result = app.client.author.page(authorId, tab, page)
                val actual = result.listPage?.currentPage ?: page
                _state.update { s ->
                    if (tab !in UiState.PAGED_TABS) {
                        s.copy(
                            loading = false,
                            loadingMore = false,
                            info = result.info,
                            cards = s.cards,
                            comments = result.comments,
                            followList = result.followList ?: s.followList,
                        )
                    } else {
                        when (mode) {
                            Mode.Reset -> s.copy(
                                loading = false,
                                loadingMore = false,
                                info = result.info,
                                cards = result.cards,
                                comments = result.comments,
                                followList = result.followList ?: s.followList,
                                window = s.window.afterReset(actual, result.listPage?.maxPage),
                                scrollTarget = 0,
                            )

                            Mode.Append -> {
                                val start = s.cards.size
                                s.copy(
                                    loading = false,
                                    loadingMore = false,
                                    info = result.info,
                                    cards = s.cards + result.cards,
                                    comments = result.comments,
                                    followList = result.followList ?: s.followList,
                                    window = s.window.afterAppend(actual, start, result.listPage?.maxPage),
                                    scrollTarget = if (scrollToStart) start else s.scrollTarget,
                                )
                            }

                            Mode.Prepend -> s.copy(
                                loading = false,
                                loadingMore = false,
                                info = result.info,
                                cards = result.cards + s.cards,
                                comments = result.comments,
                                followList = result.followList ?: s.followList,
                                window = s.window.afterPrepend(actual, result.cards.size),
                                scrollTarget = 0,
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, loadingMore = false, error = e.message ?: "加载失败，请重试")
                }
            }
        }
    }

    /** 粉丝 tab 内切换「粉丝 / 关注」两个子列表 */
    fun switchFollowSub(sub: String) {
        val current = _state.value
        if (sub == current.followSub) return
        val url = (if (sub == "follow") current.followList?.followingUrl
        else current.followList?.followersUrl) ?: return
        viewModelScope.launch {
            _state.update { it.copy(followSub = sub) }
            try {
                val list = app.client.author.followList(url)
                _state.update { it.copy(followList = list) }
            } catch (e: Exception) {
                _message.value = e.message ?: "加载失败"
            }
        }
    }

    /** 关注 / 取关当前作者（乐观更新，失败回滚；nonce 用最近一次页面加载的） */
    fun toggleFollow() {
        val current = _state.value
        val info = current.info ?: return
        val nonce = info.postActionNonce ?: run {
            _message.value = "页面信息过期，请刷新后重试"
            return
        }
        if (current.followBusy) return
        _state.update {
            it.copy(
                followBusy = true,
                info = info.copy(following = !info.following),
            )
        }
        viewModelScope.launch {
            try {
                when (val result = app.client.author.follow(authorId, nonce)) {
                    is AjaxResult.Ok -> {
                        val added = result.json["action"]?.toString()?.trim('"') != "remove"
                        _message.value = result.msg.ifBlank { if (added) "已关注" else "已取消关注" }
                        refreshInfo()
                    }
                    is AjaxResult.Fail -> {
                        _state.update { it.copy(info = info) }
                        _message.value = result.msg
                    }
                    is AjaxResult.NeedsCaptcha -> {
                        _state.update { it.copy(info = info) }
                        _message.value = result.msg
                    }
                    is AjaxResult.NotJson -> {
                        _state.update { it.copy(info = info) }
                        _message.value = "操作失败：站点响应异常"
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(info = info) }
                _message.value = e.message ?: "操作失败，请重试"
            } finally {
                _state.update { it.copy(followBusy = false) }
            }
        }
    }

    /** 关注成功后静默刷新头部（拿新的关注数与 nonce） */
    private fun refreshInfo() {
        viewModelScope.launch {
            runCatching { app.client.author.page(authorId, _state.value.tab, 1) }
                .onSuccess { page -> _state.update { it.copy(info = page.info) } }
        }
    }

    /** 粉丝列表里关注/取关某个用户（同 channel，nonce 用当前页的） */
    fun toggleFollowUser(user: AuthorUserCard) {
        val nonce = _state.value.info?.postActionNonce ?: return
        val uid = user.userId ?: return
        viewModelScope.launch {
            try {
                when (val result = app.client.author.follow(uid, nonce)) {
                    is AjaxResult.Ok -> {
                        val added = result.json["action"]?.toString()?.trim('"') != "remove"
                        _message.value = result.msg.ifBlank { if (added) "已关注" else "已取消关注" }
                        _state.update { current ->
                            current.copy(
                                followList = current.followList?.copy(
                                    users = current.followList.users.map {
                                        if (it.userId == uid) it.copy(following = added) else it
                                    },
                                ),
                            )
                        }
                    }
                    is AjaxResult.Fail -> _message.value = result.msg
                    else -> _message.value = "操作失败，请重试"
                }
            } catch (e: Exception) {
                _message.value = e.message ?: "操作失败，请重试"
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}

/** 消息中心：五个标签（未读消息/文章评论/点赞喜欢/私信消息/系统通知） */
class MessageCenterViewModel(
    private val app: SakuApp,
    private val initialTab: String?,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val tabs: List<MsgTab> = emptyList(),
        val activeTab: String = "news",
        val messages: List<top.sakuraxy.client.core.MessageItem> = emptyList(),
        val chats: List<ChatSummary> = emptyList(),
        val empty: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState(activeTab = initialTab ?: "news"))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var firstLoad = true

    init {
        load()
    }

    fun load() {
        // 首次进入若未指定标签，交给站点决定默认激活标签；之后按当前标签请求
        val tab = if (firstLoad) initialTab else _state.value.activeTab
        firstLoad = false
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val center = app.client.messages.center(tab)
                _state.update {
                    it.copy(
                        loading = false,
                        tabs = center.tabs.ifEmpty { it.tabs },
                        activeTab = center.activeTab ?: tab ?: it.activeTab,
                        messages = center.messages,
                        chats = center.chats,
                        empty = center.empty,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "加载失败，请重试") }
            }
        }
    }

    fun setTab(tab: String) {
        if (tab == _state.value.activeTab) return
        _state.update { it.copy(activeTab = tab, messages = emptyList(), chats = emptyList(), empty = false) }
        load()
    }

    fun retry() = load()
}
