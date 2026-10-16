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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import top.sakuraxy.client.SakuApp
import top.sakuraxy.client.core.AjaxResult
import top.sakuraxy.client.core.AuthApi
import top.sakuraxy.client.core.PanelForm
import top.sakuraxy.client.core.SliderCaptcha

/**
 * 注册（网站注册流程的原生复刻；表单来自登录页 #tab-sign-up，2026-10-16 实测）。
 * 用户名 + 邮箱 + 邮箱验证码 + 密码 + 同意用户协议；
 * 与网页 zib_ajax 行为一致：**发验证码与提交注册都先过滑块**（数据来自用户真实拖动）。
 *
 * 注意：注册是真实写操作（会真实创建站点账号）——资料全部由用户在界面亲手填写，
 * 客户端不预填、不代注册、不批量注册。
 */
class SignupViewModel(private val app: SakuApp) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        // ---- 输入 ----
        val name: String = "",
        val email: String = "",
        val code: String = "",
        val password: String = "",
        val agree: Boolean = false,
        // ---- 状态 ----
        /** 发送验证码倒计时（秒），>0 时按钮禁用 */
        val countdown: Int = 0,
        val sending: Boolean = false,
        val submitting: Boolean = false,
        val showSlider: Boolean = false,
        /** 注册成功（界面提示后返回；[autoLoggedIn] 时连同登录页一起退出） */
        val success: Boolean = false,
        /** 站点开启了「注册后自动登录」：注册应答已带登录 cookie（与网页刷新后一致） */
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

    fun onNameChange(v: String) = _state.update { it.copy(name = v) }
    fun onEmailChange(v: String) = _state.update { it.copy(email = v) }
    fun onCodeChange(v: String) = _state.update { it.copy(code = v) }
    fun onPasswordChange(v: String) = _state.update { it.copy(password = v) }
    fun onAgreeChange(v: Boolean) = _state.update { it.copy(agree = v) }

    // ---- 发送邮箱验证码（先过滑块，与网页 .captchsubmit 一致） ----

    fun sendCode() {
        val s = _state.value
        if (s.sending || s.countdown > 0) return
        if (s.name.isBlank()) return fail("请先填写用户名")
        if (!s.email.looksLikeEmail()) return fail("请填写正确的邮箱")
        val form = forms?.signup ?: return fail("表单未加载，请返回重试")
        if (forms?.signupSendAction == null) return fail("表单未加载，请返回重试")
        withCaptcha { captcha ->
            viewModelScope.launch {
                _state.update { it.copy(sending = true) }
                try {
                    val result = app.client.auth.sendSignupCaptcha(form, s.name, s.email, captcha)
                    handleResult(result) { json ->
                        _message.value = json.msgOf() ?: "验证码已发送"
                        // 站点可能在响应里给 remaining_time（重发间隔），优先采用
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

    // ---- 提交注册（先过滑块） ----

    fun submit() {
        val s = _state.value
        if (s.submitting) return
        if (s.name.isBlank()) return fail("请输入用户名")
        if (!s.email.looksLikeEmail()) return fail("请填写正确的邮箱")
        if (s.code.isBlank()) return fail("请输入邮箱验证码")
        if (s.password.length < 6) return fail("密码至少 6 位")
        if (!s.agree) return fail("请先阅读并同意用户协议")
        val form = forms?.signup ?: return fail("表单未加载，请返回重试")
        withCaptcha { captcha ->
            viewModelScope.launch {
                _state.update { it.copy(submitting = true) }
                try {
                    val result = app.client.auth.signUp(form, s.name, s.email, s.code, s.password, captcha)
                    handleResult(result) {
                        // 站点开启了「注册后自动登录」（2026-10-16 驾驶员实测）：应答本身已带
                        // 登录 cookie。如实检测并落盘会话（与网页注册后刷新即是登录态一致），
                        // 界面据此连同登录页一起退出；未自动登录则维持「请用新密码登录」。
                        val autoLoggedIn = app.client.session.isLoggedIn()
                        if (autoLoggedIn) app.sessionStore.save(app.client.session.snapshot())
                        _message.value =
                            if (autoLoggedIn) "注册成功，已自动登录" else "注册成功，请使用新密码登录"
                        _state.update { it.copy(success = true, autoLoggedIn = autoLoggedIn) }
                    }
                } catch (e: Exception) {
                    fail(e.message ?: "注册失败，请重试")
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

    private suspend fun handleResult(result: AjaxResult, onOk: suspend (JsonObject) -> Unit) {
        when (result) {
            is AjaxResult.Ok -> onOk(result.json)
            is AjaxResult.Fail -> fail(result.msg)
            // 服务端仍要求验证（如滑块数据过期）：重新弹滑块，如实呈现站点消息
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

/** 站点 ajax 响应里的提示文案（msg/message 兜底） */
internal fun JsonObject.msgOf(): String? =
    this["msg"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
        ?: this["message"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }

/** 轻量邮箱格式校验（服务端才是权威；这里只避免把明显写错的邮箱发出去） */
internal fun String.looksLikeEmail(): Boolean =
    Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""").matches(this)
