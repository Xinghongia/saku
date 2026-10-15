package top.sakuraxy.client.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.sakuraxy.client.SakuApp
import top.sakuraxy.client.core.ContentBlock
import top.sakuraxy.client.core.ContentParser
import top.sakuraxy.client.core.Golink
import top.sakuraxy.client.core.SiteParser
import top.sakuraxy.client.core.WpComment
import top.sakuraxy.client.core.WpPost

/**
 * 文章详情：REST 正文 + 站点页统计（作者/浏览/点赞/面包屑/标签）+ 评论 + golink 下载。
 */
class DetailViewModel(private val app: SakuApp, private val postId: Long) : ViewModel() {

    data class ResolvedLink(val label: String, val url: String)

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val post: WpPost? = null,
        val blocks: List<ContentBlock> = emptyList(),
        val buttons: List<Golink.Button> = emptyList(),
        val stats: SiteParser.PostStats? = null,
        val comments: List<WpComment> = emptyList(),
        val commentsLoading: Boolean = false,
        val commentsCanLoadMore: Boolean = false,
        val resolvingHref: String? = null,
        val resolved: ResolvedLink? = null,
        val resolveError: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var commentsPage = 1

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val post = app.client.rest.post(postId)
                val content = post.content?.rendered.orEmpty()
                _state.update {
                    it.copy(
                        loading = false,
                        post = post,
                        blocks = ContentParser.parse(content),
                        buttons = Golink.extractButtons(content),
                    )
                }
                loadStats(post.link)
                loadComments(reset = true)
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "加载失败，请重试") }
            }
        }
    }

    private fun loadStats(link: String?) {
        if (link.isNullOrBlank()) return
        viewModelScope.launch {
            runCatching { app.client.site.postStats(link) }
                .onSuccess { stats -> _state.update { it.copy(stats = stats) } }
            // 统计失败不影响正文展示，保持静默（详情页仍有正文可用）
        }
    }

    private fun loadComments(reset: Boolean) {
        val page = if (reset) 1 else commentsPage + 1
        viewModelScope.launch {
            _state.update { it.copy(commentsLoading = true) }
            try {
                val batch = app.client.rest.comments(postId, page = page, perPage = 20)
                commentsPage = page
                _state.update {
                    it.copy(
                        commentsLoading = false,
                        comments = if (reset) batch else it.comments + batch,
                        commentsCanLoadMore = batch.size >= 20,
                    )
                }
            } catch (_: Exception) {
                _state.update { it.copy(commentsLoading = false) }
            }
        }
    }

    fun loadMoreComments() {
        if (_state.value.commentsLoading || !_state.value.commentsCanLoadMore) return
        loadComments(reset = false)
    }

    /** 走站点官方 golink 流程解析真实下载地址（不跳过站点自己的跳转步骤） */
    fun resolve(button: Golink.Button) {
        if (_state.value.resolvingHref != null) return
        viewModelScope.launch {
            _state.update { it.copy(resolvingHref = button.href, resolveError = null) }
            when (val result = Golink.resolve(app.client.http, button.href)) {
                is Golink.ResolveResult.Resolved ->
                    _state.update {
                        it.copy(resolvingHref = null, resolved = ResolvedLink(button.label, result.url))
                    }

                is Golink.ResolveResult.Failed ->
                    _state.update { it.copy(resolvingHref = null, resolveError = result.reason) }
            }
        }
    }

    fun dismissResolved() = _state.update { it.copy(resolved = null) }

    fun dismissResolveError() = _state.update { it.copy(resolveError = null) }
}
