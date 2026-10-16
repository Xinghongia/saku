package top.sakuraxy.client.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import top.sakuraxy.client.SakuApp
import top.sakuraxy.client.core.AjaxResult
import top.sakuraxy.client.core.AuthApi
import top.sakuraxy.client.core.SliderCaptcha

/**
 * 找回密码（未登录态；网站登录页 #tab-resetpassword 的原生复刻，2026-10-16 实测）：
 * 邮箱 + 邮箱验证码（resetpassword_captcha）+ 新密码；发码与提交都先过滑块。
 * 与账户安全里的「重置密码」是同一条站点流程（action=reset_password），
 * 区别只是表单与 nonce 来自登录页（此处），而那边来自账户安全弹窗（登录态）。
 */
class ResetPasswordViewModel(private val app: SakuApp) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        // ---- 输入 ----
        val email: String = "",
        val code: String = "",
        val password: String = "",
        val confirm: String = "",
        // ---- 状态 ----
        /** 发送验证码倒计时（秒），>0 时按钮禁用 */
        val countdown: Int = 0,
        val sending: Boolean = false,
        val submitting: Boolean = false,
        val showSlider: Boolean = false,
        /** 重置成功（界面提示后退出；[autoLoggedIn] 时连同登录页一起退出） */
        val success: Boolean = false,
        /** 站点在重置应答里直接给了登录 cookie（与注册同机制，如实检测） */
        val autoLoggedIn: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var forms: AuthApi.AuthForms? = null
    private var pending: ((SliderCaptcha.Solution) -> Unit)? = null
    private var countdownJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                forms = app.client.auth.fetchAuthForms()
                _state.update { it.copy(loading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "加载失败，请重试") }
            }
        }
    }

    fun onEmailChange(v: String) = _state.update { it.copy(email = v) }
    fun onCodeChange(v: String) = _state.update { it.copy(code = v) }
    fun onPasswordChange(v: String) = _state.update { it.copy(password = v) }
    fun onConfirmChange(v: String) = _state.update { it.copy(confirm = v) }

    // ---- 发送邮箱验证码（先过滑块） ----

    fun sendCode() {
        val s = _state.value
        if (s.sending || s.countdown > 0) return
        if (!s.email.looksLikeEmail()) return fail("请填写正确的邮箱")
        val form = forms?.reset ?: return fail("表单未加载，请返回重试")
        if (forms?.resetSendAction == null) return fail("表单未加载，请返回重试")
        withCaptcha { captcha ->
            viewModelScope.launch {
                _state.update { it.copy(sending = true) }
                try {
                    val result = app.client.auth.sendResetCaptcha(form, s.email, captcha)
                    handleResult(result) { json ->
                        _message.value = json.msgOf() ?: "验证码已发送"
                        startCountdown((json["remaining_time"]?.jsonPrimitive?.intOrNull ?: 60).coerceAtLeast(1))
                    }
                } catch (e: Exception) {
                    fail(e.message ?: "发送失败，请重试")
                } finally {
                    _state.update { it.copy(sending = false) }
                }
            }
        }
    }

    // ---- 提交重置（先过滑块） ----

    fun submit() {
        val s = _state.value
        if (s.submitting) return
        if (!s.email.looksLikeEmail()) return fail("请填写正确的邮箱")
        if (s.code.isBlank()) return fail("请输入邮箱验证码")
        if (s.password.length < 6) return fail("新密码至少 6 位")
        if (s.password != s.confirm) return fail("两次输入的新密码不一致")
        val form = forms?.reset ?: return fail("表单未加载，请返回重试")
        withCaptcha { captcha ->
            viewModelScope.launch {
                _state.update { it.copy(submitting = true) }
                try {
                    val result = app.client.auth.resetPassword(form, s.email, s.code, s.password, s.confirm, captcha)
                    handleResult(result) {
                        // 与注册同机制：如实检测应答是否已带登录 cookie（站点可能直接登录）
                        val autoLoggedIn = app.client.session.isLoggedIn()
                        if (autoLoggedIn) app.sessionStore.save(app.client.session.snapshot())
                        _message.value =
                            if (autoLoggedIn) "密码已重置，已自动登录" else "密码已重置，请使用新密码登录"
                        _state.update { it.copy(success = true, autoLoggedIn = autoLoggedIn) }
                    }
                } catch (e: Exception) {
                    fail(e.message ?: "重置失败，请重试")
                } finally {
                    _state.update { it.copy(submitting = false) }
                }
            }
        }
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

    fun consumeMessage() {
        _message.value = null
    }

    // ---- 内部 ----

    private fun withCaptcha(block: (SliderCaptcha.Solution) -> Unit) {
        pending = block
        _state.update { it.copy(showSlider = true) }
    }

    private suspend fun handleResult(result: AjaxResult, onOk: suspend (kotlinx.serialization.json.JsonObject) -> Unit) {
        when (result) {
            is AjaxResult.Ok -> onOk(result.json)
            is AjaxResult.Fail -> fail(result.msg)
            is AjaxResult.NeedsCaptcha -> {
                _message.value = result.msg
                _state.update { it.copy(showSlider = true) }
            }

            is AjaxResult.NotJson -> fail("站点响应异常：${result.snippet}")
        }
    }

    private fun fail(text: String) {
        _message.value = text
    }

    private fun startCountdown(seconds: Int) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var left = seconds
            while (left > 0) {
                _state.update { it.copy(countdown = left) }
                delay(1000)
                left--
            }
            _state.update { it.copy(countdown = 0) }
        }
    }
}
