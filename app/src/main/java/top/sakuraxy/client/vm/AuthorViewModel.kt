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
        val page: Int = 1,
        val maxPage: Int? = null,
        val followBusy: Boolean = false,
    ) {
        val canLoadMore: Boolean get() = maxPage != null && page < (maxPage ?: 1) && tab in setOf("post", "favorite")
    }

    private val _state = MutableStateFlow(UiState(tab = initialTab ?: "post"))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        load(reset = true)
    }

    fun setTab(tab: String) {
        if (tab == _state.value.tab) return
        _state.update { it.copy(tab = tab) }
        load(reset = true)
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || !current.canLoadMore) return
        load(reset = false)
    }

    fun retry() = load(reset = true)

    private fun load(reset: Boolean) {
        viewModelScope.launch {
            val targetPage = if (reset) 1 else _state.value.page + 1
            _state.update {
                if (reset) it.copy(loading = true, error = null) else it.copy(loadingMore = true)
            }
            try {
                val tab = _state.value.tab
                val result = app.client.author.page(authorId, tab, targetPage)
                _state.update {
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        info = result.info,
                        cards = if (tab in setOf("post", "favorite")) {
                            if (reset) result.cards else it.cards + result.cards
                        } else it.cards,
                        comments = result.comments,
                        followList = result.followList ?: it.followList,
                        page = targetPage,
                        maxPage = result.listPage?.maxPage,
                    )
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
