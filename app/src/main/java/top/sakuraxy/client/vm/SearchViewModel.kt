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

/** 搜索：走站点搜索页（与网页同一结果集），结果卡片信息与列表页一致 */
class SearchViewModel(private val app: SakuApp) : ViewModel() {

    data class UiState(
        val query: String = "",
        val loading: Boolean = false,
        val loadingMore: Boolean = false,
        val searched: Boolean = false,
        val cards: List<SiteParser.PostCard> = emptyList(),
        val page: Int = 1,
        val maxPage: Int? = null,
        val error: String? = null,
    ) {
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
        load(query, page = 1, reset = true)
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || !current.canLoadMore) return
        load(current.query.trim(), page = current.page + 1, reset = false)
    }

    private fun load(query: String, page: Int, reset: Boolean) {
        viewModelScope.launch {
            _state.update {
                if (reset) it.copy(loading = true, error = null, searched = true)
                else it.copy(loadingMore = true, error = null)
            }
            try {
                val result = app.client.site.searchPage(query = query, page = page)
                _state.update {
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        cards = if (reset) result.cards else it.cards + result.cards,
                        page = result.currentPage ?: page,
                        maxPage = result.maxPage,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, loadingMore = false, error = e.message ?: "搜索失败，请重试")
                }
            }
        }
    }
}
