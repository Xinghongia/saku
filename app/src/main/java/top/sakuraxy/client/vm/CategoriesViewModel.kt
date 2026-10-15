package top.sakuraxy.client.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.sakuraxy.client.SakuApp
import top.sakuraxy.client.core.CategoryTree

/** 分类页：从 REST 拉全部分类并按 parent 组成多级树 */
class CategoriesViewModel(private val app: SakuApp) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val tree: List<CategoryTree.Node> = emptyList(),
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                // 分类总数不足 100 时一次拉完；超过则分页拉取
                val terms = buildList {
                    var page = 1
                    while (true) {
                        val batch = app.client.rest.categories(perPage = 100)
                        addAll(batch)
                        if (batch.size < 100 || page >= 5) break
                        page += 1
                    }
                }

                _state.update { it.copy(loading = false, tree = CategoryTree.build(terms)) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "分类加载失败") }
            }
        }
    }
}
