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
import top.sakuraxy.client.core.Html

/**
 * 网站使用手册（站点独立页面 /manual，2026-10-16 实测：
 * 顶部导航「使用手册」→ WordPress page，REST pages?slug=manual 可取全文）。
 * 只读渲染，与网页看的是同一份内容。
 */
class ManualViewModel(private val app: SakuApp) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val title: String = "使用手册",
        val blocks: List<ContentBlock> = emptyList(),
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
                val page = app.client.rest.pageBySlug("manual")
                if (page == null) {
                    _state.update { it.copy(loading = false, error = "站点上没有找到使用手册页面") }
                } else {
                    _state.update {
                        it.copy(
                            loading = false,
                            title = Html.toText(page.title.rendered).ifBlank { "使用手册" },
                            blocks = ContentParser.parse(page.content?.rendered.orEmpty()),
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "加载失败，请重试") }
            }
        }
    }
}
