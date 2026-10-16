package top.sakuraxy.client.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.sakuraxy.client.SakuApp
import top.sakuraxy.client.core.SiteParser

/**
 * 站点列表页（首页 / 分类页共用）：卡片 + 排序 + 分页。
 * categoryUrl 为空 = 首页；不为空 = 分类链接（term.link，含中文路径也不会二次编码）。
 *
 * 分页 = [PageWindow] 窗口模型：列表累积一段连续页码；上一页/下一页/跳页优先在
 * 已加载区间内滚动定位，窗口边缘向相邻页扩展，跳远则重开窗口（见 PageWindow 注释）。
 * 「加载更多」按钮保留为追加下一页的快捷方式（不改变滚动位置）。
 */
class SiteListViewModel(
    private val app: SakuApp,
    private val categoryUrl: String? = null,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val loadingMore: Boolean = false,
        val cards: List<SiteParser.PostCard> = emptyList(),
        val sort: String? = null,
        val window: PageWindow = PageWindow(),
        val error: String? = null,
        val header: SiteParser.CategoryHeader? = null,
        /** 一次性滚动目标（列表项下标）；界面滚动后调 consumeScrollTarget 清空 */
        val scrollTarget: Int? = null,
    ) {
        /** 已加载到的最大页码（列表底部语义） */
        val page: Int get() = window.loadedMax
        val maxPage: Int? get() = window.maxPage
        val canLoadMore: Boolean get() = maxPage != null && page < (maxPage ?: 1)
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        if (categoryUrl != null) loadHeader()
        viewModelScope.launch {
            // 首页使用设置里的默认排序；分类页保持站点默认（推荐）
            if (categoryUrl == null) {
                val preferred = app.settingsStore.flow.first().defaultSort
                if (preferred.isNotBlank()) {
                    _state.update { it.copy(sort = preferred) }
                }
            }
            load(1, Mode.Reset)
        }
    }

    private fun loadHeader() {
        viewModelScope.launch {
            runCatching { app.client.site.categoryHeader(categoryUrl!!) }
                .onSuccess { header -> _state.update { it.copy(header = header) } }
        }
    }

    fun setSort(key: String?) {
        if (key == _state.value.sort) return
        _state.update { it.copy(sort = key) }
        load(1, Mode.Reset)
    }

    fun refresh() = load(1, Mode.Reset)

    fun retry() = load(1, Mode.Reset)

    /** 列表末尾「加载更多」：追加下一页（不动滚动位置） */
    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || !current.canLoadMore) return
        load(current.window.loadedMax + 1, Mode.Append)
    }

    /** 上一页 / 下一页（visiblePage = 由列表滚动位置换算的当前页） */
    fun prevPage(visiblePage: Int) = seek(visiblePage - 1)

    fun nextPage(visiblePage: Int) = seek(visiblePage + 1)

    /** 跳页（页码格子选择） */
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
        viewModelScope.launch {
            _state.update {
                if (mode == Mode.Reset) {
                    it.copy(loading = true, error = null)
                } else {
                    it.copy(loadingMore = true, error = null)
                }
            }
            try {
                val result = app.client.site.listPage(
                    categoryUrlOrPath = categoryUrl,
                    sort = _state.value.sort,
                    page = page,
                )
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
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        error = e.message ?: "加载失败，请重试",
                    )
                }
            }
        }
    }
}
