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
import top.sakuraxy.client.core.AccountModals
import top.sakuraxy.client.core.AjaxResult
import top.sakuraxy.client.core.PanelForm
import top.sakuraxy.client.core.SliderCaptcha
import top.sakuraxy.client.core.SocialBind

/**
 * 账户安全：改邮箱（两步：验证当前邮箱 → 绑定新邮箱）、改密码、重置密码（忘记原密码）。
 *
 * 与网页行为一致（2026-10-16 实测 + sign-register.js 复刻）：
 * - 四类表单都带 captcha_mode=slider，**每次提交（含发验证码）都要先过滑块**，
 *   数据来自用户真实拖动（[SliderCaptcha]）；
 * - 发送验证码按钮走按钮的 form-action（verify_user_captcha / bind_email_captcha / resetpassword_captcha）；
 * - 弹窗 nonce 在进入页面时现取，本次会话内即用即弃，成功后重新拉取。
 */
class AccountSecurityViewModel(private val app: SakuApp) : ViewModel() {

    /** 当前所处流程（MENU = 账户安全首页） */
    enum class Step { MENU, EMAIL_VERIFY, EMAIL_BIND, PASSWORD, RESET }

    /** 发送验证码的目标表单 */
    enum class SendKind { VERIFY_OLD, BIND_NEW, RESET }

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val step: Step = Step.MENU,
        val socials: List<SocialBind> = emptyList(),
        /** 当前绑定邮箱（get_current_user 明文，页面 HTML 里被 CDN 混淆） */
        val email: String? = null,
        // ---- 输入 ----
        val code: String = "",
        val newEmail: String = "",
        val resetEmail: String = "",
        val passwordOld: String = "",
        val passwordNew: String = "",
        val passwordConfirm: String = "",
        val agree: Boolean = true,
        // ---- 状态 ----
        /** 发送验证码倒计时（秒），>0 时按钮禁用 */
        val countdown: Int = 0,
        val sending: Boolean = false,
        val submitting: Boolean = false,
        val showSlider: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var modals: AccountModals? = null

    /** 滑块完成后的待执行提交（NeedsCaptcha 重试时保留复用） */
    private var pending: ((SliderCaptcha.Solution) -> Unit)? = null
    private var countdownJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val account = app.client.users.account()
                val modals = app.client.users.accountModals()
                val email = runCatching { app.client.users.currentUser()?.email }.getOrNull()
                this@AccountSecurityViewModel.modals = modals
                _state.update {
                    it.copy(
                        loading = false,
                        socials = account.socials,
                        email = email?.ifBlank { null } ?: modals?.currentEmail,
                        resetEmail = it.resetEmail.ifBlank { email.orEmpty() },
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "加载失败，请重试") }
            }
        }
    }

    // ---- 导航 ----

    fun openStep(step: Step) {
        countdownJob?.cancel()
        _state.update {
            it.copy(
                step = step,
                code = "",
                newEmail = "",
                passwordOld = "",
                passwordNew = "",
                passwordConfirm = "",
                agree = true,
                countdown = 0,
            )
        }
    }

    fun backToMenu() = openStep(Step.MENU)

    // ---- 输入 ----

    fun onCodeChange(v: String) = _state.update { it.copy(code = v) }
    fun onNewEmailChange(v: String) = _state.update { it.copy(newEmail = v) }
    fun onResetEmailChange(v: String) = _state.update { it.copy(resetEmail = v) }
    fun onPasswordOldChange(v: String) = _state.update { it.copy(passwordOld = v) }
    fun onPasswordNewChange(v: String) = _state.update { it.copy(passwordNew = v) }
    fun onPasswordConfirmChange(v: String) = _state.update { it.copy(passwordConfirm = v) }
    fun onAgreeChange(v: Boolean) = _state.update { it.copy(agree = v) }

    // ---- 发送验证码（发码前先过滑块，与网页 .captchsubmit 一致） ----

    fun sendCode(kind: SendKind) {
        val s = _state.value
        if (s.sending || s.countdown > 0) return
        val m = modals ?: return
        val (form, action, extra) = when (kind) {
            SendKind.VERIFY_OLD -> Triple(m.verifyForm, m.verifySendAction, emptyMap())
            SendKind.BIND_NEW -> {
                if (s.newEmail.isBlank()) return fail("请先填写新邮箱")
                Triple(m.bindForm, m.bindSendAction, mapOf("email" to s.newEmail))
            }

            SendKind.RESET -> {
                if (s.resetEmail.isBlank()) return fail("请先填写邮箱")
                Triple(m.resetForm, m.resetSendAction, mapOf("email" to s.resetEmail))
            }
        }
        if (form == null || action == null) return fail("表单未加载，请返回重试")
        withCaptcha { captcha ->
            viewModelScope.launch {
                _state.update { it.copy(sending = true) }
                try {
                    val result = app.client.users.submitForm(form, extra, actionOverride = action, captcha = captcha)
                    handleResult(result) { json ->
                        _message.value = json["msg"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
                            ?: "验证码已发送"
                        // 站点可能在响应里给 remaining_time（重发间隔），优先采用
                        val remaining = json["remaining_time"]?.jsonPrimitive?.intOrNull ?: 60
                        startCountdown(remaining.coerceAtLeast(1))
                    }
                } catch (e: Exception) {
                    fail(e.message ?: "发送失败，请重试")
                } finally {
                    _state.update { it.copy(sending = false) }
                }
            }
        }
    }

    // ---- 改邮箱：步骤 1 验证当前邮箱 ----

    fun verifyOldEmail() {
        val form = modals?.verifyForm ?: return fail("表单未加载，请返回重试")
        val code = _state.value.code.ifBlank { return fail("请输入验证码") }
        withCaptcha { captcha ->
            submit(form, mapOf("captch" to code), captcha) {
                countdownJob?.cancel()
                _state.update { it.copy(step = Step.EMAIL_BIND, code = "", countdown = 0) }
            }
        }
    }

    // ---- 改邮箱：步骤 2 绑定新邮箱 ----

    fun bindEmail() {
        val form = modals?.bindForm ?: return fail("表单未加载，请返回重试")
        val s = _state.value
        if (s.newEmail.isBlank()) return fail("请输入新邮箱")
        if (s.code.isBlank()) return fail("请输入验证码")
        if (!s.agree) return fail("请先阅读并同意用户协议")
        val values = mapOf(
            "email" to s.newEmail,
            "captch" to s.code,
            "user_agreement" to "on",
        )
        withCaptcha { captcha ->
            submit(form, values, captcha) {
                _message.value = "邮箱修改成功"
                openStep(Step.MENU)
                refreshEmail()
            }
        }
    }

    // ---- 修改密码 ----

    fun changePassword() {
        val form = modals?.passwordForm ?: return fail("表单未加载，请返回重试")
        val s = _state.value
        if (s.passwordOld.isBlank()) return fail("请输入原密码")
        if (s.passwordNew.length < 6) return fail("新密码至少 6 位")
        if (s.passwordNew != s.passwordConfirm) return fail("两次输入的新密码不一致")
        val values = mapOf(
            "passwordold" to s.passwordOld,
            "password" to s.passwordNew,
            "password2" to s.passwordConfirm,
        )
        withCaptcha { captcha ->
            submit(form, values, captcha) {
                _message.value = "密码修改成功"
                openStep(Step.MENU)
            }
        }
    }

    // ---- 重置密码（忘记原密码） ----

    fun resetPassword() {
        val form = modals?.resetForm ?: return fail("表单未加载，请返回重试")
        val s = _state.value
        if (s.resetEmail.isBlank()) return fail("请输入邮箱")
        if (s.code.isBlank()) return fail("请输入验证码")
        if (s.passwordNew.length < 6) return fail("新密码至少 6 位")
        if (s.passwordNew != s.passwordConfirm) return fail("两次输入的新密码不一致")
        val values = mapOf(
            "email" to s.resetEmail,
            "captch" to s.code,
            "password2" to s.passwordNew,
            "repassword" to s.passwordConfirm,
        )
        withCaptcha { captcha ->
            submit(form, values, captcha) {
                _message.value = "密码重置成功"
                openStep(Step.MENU)
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

    private fun submit(
        form: PanelForm,
        values: Map<String, String>,
        captcha: SliderCaptcha.Solution,
        onOk: () -> Unit,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(submitting = true) }
            try {
                handleResult(app.client.users.submitForm(form, values, captcha = captcha)) { onOk() }
            } catch (e: Exception) {
                fail(e.message ?: "提交失败，请重试")
            } finally {
                _state.update { it.copy(submitting = false) }
            }
        }
    }

    private fun handleResult(result: AjaxResult, onOk: (JsonObject) -> Unit) {
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

    private fun refreshEmail() {
        viewModelScope.launch {
            val email = runCatching { app.client.users.currentUser()?.email }.getOrNull()
            val modals = runCatching { app.client.users.accountModals() }.getOrNull()
            this@AccountSecurityViewModel.modals = modals ?: this@AccountSecurityViewModel.modals
            if (email != null) _state.update { it.copy(email = email.ifBlank { null }) }
        }
    }
}
