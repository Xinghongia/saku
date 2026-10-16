package top.sakuraxy.client.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import top.sakuraxy.client.SakuApp
import top.sakuraxy.client.core.AjaxResult
import top.sakuraxy.client.core.UserOverview

/**
 * 「我的」页：登录态驱动的用户中心总览 + 签到 + 拓展开关 + 退出登录。
 *
 * 登录态用核心层 [top.sakuraxy.client.core.Session.revision] 订阅（登录/登出/恢复自动刷新），
 * 页面回到前台时也会静默刷新（资料编辑等改动返回后立即可见）。
 */
class ProfileViewModel(private val app: SakuApp) : ViewModel() {

    sealed interface UiState {
        data object LoggedOut : UiState

        data object Loading : UiState

        data class LoggedIn(
            val userId: Long,
            val email: String,
            val overview: UserOverview,
        ) : UiState

        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** 一次性提示（签到/开关结果），消费后置空 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 写操作进行中（签到/开关），用于禁用按钮 */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** 付费类操作的「请到网站操作」提示 */
    private val _siteOnlyHint = MutableStateFlow(false)
    val siteOnlyHint: StateFlow<Boolean> = _siteOnlyHint.asStateFlow()

    private var loading = false

    init {
        viewModelScope.launch {
            // 跳过首次发射（初始加载由页面 ON_RESUME 触发），之后登录态变化即刷新
            app.client.session.revision.drop(1).collect { load() }
        }
    }

    /** 加载/刷新总览（并发调用时只保留一次在途请求） */
    fun load() = reload(force = false)

    private fun reload(force: Boolean) {
        if (loading && !force) return
        loading = true
        viewModelScope.launch {
            try {
                if (!app.client.session.isLoggedIn()) {
                    _state.value = UiState.LoggedOut
                    return@launch
                }
                if (_state.value !is UiState.LoggedIn) _state.value = UiState.Loading
                val overview = app.client.users.overview()
                val user = app.client.users.currentUser()
                _state.value = if (user == null) {
                    // cookie 已失效：按未登录处理（cookie 留待下次登录覆盖）
                    UiState.LoggedOut
                } else {
                    UiState.LoggedIn(userId = user.id, email = user.email, overview = overview)
                }
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "加载失败，请重试")
            } finally {
                loading = false
            }
        }
    }

    /** 每日签到；成功后刷新总览（积分/按钮态） */
    fun checkin() {
        if (_busy.value || loading) return
        viewModelScope.launch {
            _busy.value = true
            try {
                when (val result = app.client.users.checkin()) {
                    is AjaxResult.Ok -> {
                        _message.value = result.msg.ifBlank { "签到成功" }
                        reload(force = true)
                    }
                    is AjaxResult.Fail -> _message.value = result.msg
                    is AjaxResult.NeedsCaptcha -> _message.value = result.msg
                    is AjaxResult.NotJson -> _message.value = "签到失败：站点响应异常"
                }
            } catch (e: Exception) {
                _message.value = e.message ?: "签到失败，请重试"
            } finally {
                _busy.value = false
            }
        }
    }

    /** 拓展「屏蔽内容」：成功后用站点返回的新状态替换本地总览 */
    fun setR18Blocked(blocked: Boolean) {
        val current = _state.value as? UiState.LoggedIn ?: return
        if (_busy.value || current.overview.r18Blocked == blocked) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val fresh = app.client.users.setR18(blocked)
                _state.value = current.copy(overview = fresh)
                if (fresh.r18Blocked != blocked) {
                    _message.value = "开关未生效，当前为「${if (fresh.r18Blocked) "屏蔽" else "显示"}」"
                }
            } catch (e: Exception) {
                _message.value = e.message ?: "操作失败，请重试"
            } finally {
                _busy.value = false
            }
        }
    }

    /** 退出登录（清 cookie + 持久化会话） */
    fun logout() {
        viewModelScope.launch {
            app.client.session.clear()
            app.sessionStore.clear()
            _state.value = UiState.LoggedOut
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun showSiteOnlyHint() {
        _siteOnlyHint.value = true
    }

    fun dismissSiteOnlyHint() {
        _siteOnlyHint.value = false
    }
}
