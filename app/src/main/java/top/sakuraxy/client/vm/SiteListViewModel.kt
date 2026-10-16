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
        val page: Int = 1,
        val maxPage: Int? = null,
        val error: String? = null,
        val header: SiteParser.CategoryHeader? = null,
    ) {
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
            load(reset = true)
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
        load(reset = true)
    }

    fun refresh() = load(reset = true)

    fun retry() = load(reset = true)

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || !current.canLoadMore) return
        load(reset = false)
    }

    /** 跳到指定页（站点分页控件的「跳页」等价物） */
    fun jumpTo(targetPage: Int) {
        val max = _state.value.maxPage ?: return
        val page = targetPage.coerceIn(1, max)
        if (page == _state.value.page) return
        _state.update { it.copy(page = page) }
        load(reset = true, forcePage = page)
    }

    private fun load(reset: Boolean, forcePage: Int? = null) {
        viewModelScope.launch {
            val targetPage = when {
                forcePage != null -> forcePage
                reset -> 1
                else -> _state.value.page + 1
            }
            _state.update {
                if (reset) it.copy(loading = true, error = null)
                else it.copy(loadingMore = true, error = null)
            }
            try {
                val result = app.client.site.listPage(
                    categoryUrlOrPath = categoryUrl,
                    sort = _state.value.sort,
                    page = targetPage,
                )
                _state.update {
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        cards = if (reset) result.cards else it.cards + result.cards,
                        page = result.currentPage ?: targetPage,
                        maxPage = result.maxPage,
                    )
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
