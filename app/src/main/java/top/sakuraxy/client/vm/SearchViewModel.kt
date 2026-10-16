package top.sakuraxy.client.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.sakuraxy.client.SakuApp
import top.sakuraxy.client.core.SiteParser

/**
 * 搜索：走站点搜索页（与网页同一结果集），结果卡片信息与列表页一致。
 * 分页模型与 [SiteListViewModel] 相同（PageWindow 窗口：翻页优先滚动、边缘扩展、跳远重开）。
 */
class SearchViewModel(private val app: SakuApp) : ViewModel() {

    data class UiState(
        val query: String = "",
        val loading: Boolean = false,
        val loadingMore: Boolean = false,
        val searched: Boolean = false,
        val cards: List<SiteParser.PostCard> = emptyList(),
        val window: PageWindow = PageWindow(),
        val error: String? = null,
        /** 一次性滚动目标（列表项下标）；界面滚动后调 consumeScrollTarget 清空 */
        val scrollTarget: Int? = null,
    ) {
        val page: Int get() = window.loadedMax
        val maxPage: Int? get() = window.maxPage
        val canLoadMore: Boolean get() = maxPage != null && page < (maxPage ?: 1)
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun search() {
        val query = _state.value.query.trim()
        if (query.isEmpty() || _state.value.loading) return
        load(1, Mode.Reset)
    }

    /** 列表末尾「加载更多」：追加下一页（不动滚动位置） */
    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || !current.canLoadMore) return
        load(current.window.loadedMax + 1, Mode.Append)
    }

    fun prevPage(visiblePage: Int) = seek(visiblePage - 1)

    fun nextPage(visiblePage: Int) = seek(visiblePage + 1)

    fun jumpTo(page: Int) = seek(page)

    private fun seek(page: Int) {
        val s = _state.value
        val max = s.window.maxPage ?: return
        if (page !in 1..max || s.loading || s.loadingMore) return
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
        val query = _state.value.query.trim()
        viewModelScope.launch {
            _state.update {
                if (mode == Mode.Reset) {
                    it.copy(loading = true, error = null, searched = true)
                } else {
                    it.copy(loadingMore = true, error = null)
                }
            }
            try {
                val result = app.client.site.searchPage(query = query, page = page)
                val actual = result.currentPage ?: page
                _state.update { s ->
                    when (mode) {
                        Mode.Reset -> s.copy(
                            loading = false,
                            loadingMore = false,
                            cards = result.cards,
                            window = s.window.afterReset(actual, result.maxPage),
                            scrollTarget = 0,
                        )

                        Mode.Append -> {
                            val start = s.cards.size
                            s.copy(
                                loading = false,
                                loadingMore = false,
                                cards = s.cards + result.cards,
                                window = s.window.afterAppend(actual, start, result.maxPage),
                                scrollTarget = if (scrollToStart) start else s.scrollTarget,
                            )
                        }

                        Mode.Prepend -> s.copy(
                            loading = false,
                            loadingMore = false,
                            cards = result.cards + s.cards,
                            window = s.window.afterPrepend(actual, result.cards.size),
                            scrollTarget = 0,
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, loadingMore = false, error = e.message ?: "搜索失败，请重试")
                }
            }
        }
    }
}
