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

    fun submit() {
        val current = _state.value
        if (current.submitting || current.username.isBlank() || current.password.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, result = null) }
            val result = try {
                when (val r = app.client.auth.signIn(current.username, current.password)) {
                    is AuthApi.SigninResult.Success -> {
                        app.sessionStore.save(app.client.session.snapshot())
                        LoginResult.Success(r.message)
                    }

                    is AuthApi.SigninResult.NeedsCaptcha -> LoginResult.NeedsCaptcha(r.message)
                    is AuthApi.SigninResult.Failure -> LoginResult.Error(r.message)
                }
            } catch (e: Exception) {
                LoginResult.Error(e.message ?: "网络错误，请重试")
            }
            _state.update { it.copy(submitting = false, result = result) }
        }
    }
}
