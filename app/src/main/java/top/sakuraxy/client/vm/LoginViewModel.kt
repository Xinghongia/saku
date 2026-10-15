package top.sakuraxy.client.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.sakuraxy.client.SakuApp
import top.sakuraxy.client.core.AuthApi
import top.sakuraxy.client.core.SliderCaptcha

class LoginViewModel(private val app: SakuApp) : ViewModel() {

    sealed interface LoginResult {
        data class Success(val message: String) : LoginResult
        data class NeedsCaptcha(val message: String) : LoginResult
        data class Error(val message: String) : LoginResult
    }

    data class UiState(
        val username: String = "",
        val password: String = "",
        val submitting: Boolean = false,
        /** 是否显示滑块验证弹窗（站点 captcha_mode=slider 时） */
        val showSlider: Boolean = false,
        val result: LoginResult? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun onUsernameChange(value: String) {
        _state.update { it.copy(username = value) }
    }

    fun onPasswordChange(value: String) {
        _state.update { it.copy(password = value) }
    }

    /**
     * 点「登录」：先看站点当前是否要求滑块（实测 captcha_mode=slider），
     * 要求则先弹滑块完成验证，再提交凭证；不要求则直接提交。
     * 与网页行为一致：网页在提交时若检测到未完成的验证也会先弹滑块。
     */
    fun submit() {
        val current = _state.value
        if (current.submitting || current.username.isBlank() || current.password.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, result = null) }
            val captchaMode = try {
                app.client.auth.fetchSigninPage().captchaMode
            } catch (e: Exception) {
                _state.update {
                    it.copy(submitting = false, result = LoginResult.Error(e.message ?: "网络错误，请重试"))
                }
                return@launch
            }
            when (captchaMode) {
                "slider" -> _state.update { it.copy(submitting = false, showSlider = true) }
                null, "" -> {
                    _state.update { it.copy(submitting = false) }
                    loginWith(null)
                }
                else -> _state.update {
                    it.copy(
                        submitting = false,
                        result = LoginResult.Error("站点启用了「$captchaMode」验证方式，客户端暂不支持"),
                    )
                }
            }
        }
    }

    /** 用户在滑块弹窗完成拖动后的提交（数据来自真实拖动，见 [SliderCaptcha]） */
    fun loginWith(captcha: SliderCaptcha.Solution?) {
        val current = _state.value
        if (current.username.isBlank() || current.password.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, showSlider = false, result = null) }
            val result = try {
                when (val r = app.client.auth.signIn(current.username, current.password, captcha)) {
                    is AuthApi.SigninResult.Success -> {
                        app.sessionStore.save(app.client.session.snapshot())
                        LoginResult.Success(r.message)
                    }

                    // 服务端仍要求验证（如凭据过期）：重新弹滑块，如实呈现站点消息
                    is AuthApi.SigninResult.NeedsCaptcha -> LoginResult.NeedsCaptcha(r.message)
                    is AuthApi.SigninResult.Failure -> LoginResult.Error(r.message)
                }
            } catch (e: Exception) {
                LoginResult.Error(e.message ?: "网络错误，请重试")
            }
            _state.update {
                it.copy(
                    submitting = false,
                    showSlider = result is LoginResult.NeedsCaptcha,
                    result = result,
                )
            }
        }
    }

    fun dismissSlider() {
        _state.update { it.copy(showSlider = false) }
    }
}
