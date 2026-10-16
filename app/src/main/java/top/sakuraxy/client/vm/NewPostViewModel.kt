package top.sakuraxy.client.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import top.sakuraxy.client.SakuApp
import top.sakuraxy.client.core.AjaxResult
import top.sakuraxy.client.core.ComposeCategory
import top.sakuraxy.client.core.ComposeForm
import top.sakuraxy.client.core.SiteParser
import top.sakuraxy.client.core.SliderCaptcha

/**
 * 投稿（/newposts）：标题 / 正文 / 分类多选 / 标签 → 存草稿或提交审核。
 *
 * 与网页行为一致（page-template 模块 `.new-posts-submit`）：
 * - 提交必过滑块（表单带 captcha_mode=slider），数据来自用户真实拖动；
 * - 草稿保存成功后站点返回 posts_id，之后的保存/发布携带同一 id（更新同一篇草稿）；
 * - 提交审核走 posts_save，成功响应含 open_url（发布后的文章地址）。
 */
class NewPostViewModel(private val app: SakuApp) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        /** 未登录（站点跳转到登录页） */
        val needLogin: Boolean = false,
        val categories: List<ComposeCategory> = emptyList(),
        val selected: Set<String> = emptySet(),
        val title: String = "",
        val content: String = "",
        val tags: String = "",
        val submitting: Boolean = false,
        val showSlider: Boolean = false,
        /** 发布前的确认框（会提交真实内容到站点审核） */
        val confirmPublish: Boolean = false,
        /** 提交审核成功后解析出的文章 id（可用于跳详情） */
        val publishedPostId: Long? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var form: ComposeForm? = null
    private var postsId: String = "0"

    /** 滑块完成后的待执行提交（NeedsCaptcha 重试时保留复用） */
    private var pending: ((SliderCaptcha.Solution) -> Unit)? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, needLogin = false) }
            if (!app.client.session.isLoggedIn()) {
                _state.update { it.copy(loading = false, needLogin = true) }
                return@launch
            }
            try {
                val parsed = app.client.newPost.composeForm()
                if (parsed == null) {
                    _state.update { it.copy(loading = false, error = "投稿表单加载失败（页面结构可能已变化）") }
                    return@launch
                }
                form = parsed
                postsId = parsed.fields["posts_id"] ?: "0"
                _state.update {
                    it.copy(
                        loading = false,
                        categories = parsed.categories,
                        // 分类太多时默认不选，由用户自己挑（与网页一致）
                        selected = emptySet(),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "加载失败，请重试") }
            }
        }
    }

    fun onTitleChange(v: String) = _state.update { it.copy(title = v) }
    fun onContentChange(v: String) = _state.update { it.copy(content = v) }
    fun onTagsChange(v: String) = _state.update { it.copy(tags = v) }

    fun toggleCategory(id: String) = _state.update {
        it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id)
    }

    /** 存草稿（可反复保存，站点返回的 posts_id 复用） */
    fun saveDraft() {
        if (_state.value.title.isBlank()) return fail("请先填写标题")
        submit(asDraft = true)
    }

    /** 点「提交审核」：先弹确认框（会提交真实内容到站点） */
    fun requestPublish() {
        val s = _state.value
        if (s.title.isBlank()) return fail("请先填写标题")
        if (s.content.isBlank()) return fail("请先填写正文")
        _state.update { it.copy(confirmPublish = true) }
    }

    fun dismissPublish() = _state.update { it.copy(confirmPublish = false) }

    fun confirmPublish() {
        _state.update { it.copy(confirmPublish = false) }
        submit(asDraft = false)
    }

    fun consumePublishedPostId() = _state.update { it.copy(publishedPostId = null) }

    fun consumeMessage() {
        _message.value = null
    }

    // ---- 滑块 ----

    fun onSliderSolved(solution: SliderCaptcha.Solution) {
        val block = pending ?: return
        _state.update { it.copy(showSlider = false) }
        block(solution)
    }

    fun dismissSlider() {
        pending = null
        _state.update { it.copy(showSlider = false) }
    }

    // ---- 内部 ----

    private fun submit(asDraft: Boolean) {
        val base = form ?: return fail("表单未加载，请返回重试")
        withCaptcha { captcha ->
            val current = base.copy(fields = base.fields + ("posts_id" to postsId))
            val s = _state.value
            viewModelScope.launch {
                _state.update { it.copy(submitting = true) }
                try {
                    val result = app.client.newPost.submit(
                        form = current,
                        title = s.title,
                        content = s.content,
                        categoryIds = s.selected.toList(),
                        tags = s.tags,
                        asDraft = asDraft,
                        captcha = captcha,
                    )
                    handleResult(result, asDraft)
                } catch (e: Exception) {
                    fail(e.message ?: "提交失败，请重试")
                } finally {
                    _state.update { it.copy(submitting = false) }
                }
            }
        }
    }

    private fun handleResult(result: AjaxResult, asDraft: Boolean) {
        when (result) {
            is AjaxResult.Ok -> {
                val json = result.json
                json["posts_id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?.let { postsId = it }
                if (asDraft) {
                    val time = json["time"]?.jsonPrimitive?.contentOrNull
                    _message.value = if (time.isNullOrBlank()) "草稿已保存" else "草稿已保存（$time）"
                } else {
                    val openUrl = json["open_url"]?.jsonPrimitive?.contentOrNull
                    _message.value = result.msg.ifBlank { "已提交审核" }
                    _state.update {
                        it.copy(
                            publishedPostId = openUrl?.let { url -> SiteParser.postIdFromUrl(url) },
                            title = "",
                            content = "",
                            tags = "",
                            selected = emptySet(),
                        )
                    }
                }
            }

            is AjaxResult.Fail -> fail(result.msg)
            is AjaxResult.NeedsCaptcha -> {
                // 滑块数据过期/未对齐：重新弹滑块，如实呈现站点消息
                _message.value = result.msg
                _state.update { it.copy(showSlider = true) }
            }

            is AjaxResult.NotJson -> fail("站点响应异常：${result.snippet}")
        }
    }

    private fun withCaptcha(block: (SliderCaptcha.Solution) -> Unit) {
        pending = block
        _state.update { it.copy(showSlider = true) }
    }

    private fun fail(text: String) {
        _message.value = text
    }
}
