package top.sakuraxy.client.ui.auth

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import top.sakuraxy.client.SakuApp
import top.sakuraxy.client.ui.components.CenterLoading
import top.sakuraxy.client.ui.components.ErrorRetry
import top.sakuraxy.client.ui.components.SakuTopAppBar
import top.sakuraxy.client.vm.ResetPasswordViewModel

/**
 * 找回密码（未登录态；网站登录页「找回密码」tab 的原生复刻）：
 * 邮箱 + 邮箱验证码 + 新密码；发码与提交都先过滑块。
 * 与账户安全里的「重置密码」是同一条站点流程，成功后用新密码登录。
 *
 * [onResetSuccess] 参数 = 站点是否在重置应答里直接给了登录 cookie：
 * 自动登录时由调用方连同登录页一起退出（回到来处，已是登录态）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetPasswordScreen(
    app: SakuApp,
    onBack: () -> Unit,
    onResetSuccess: (autoLoggedIn: Boolean) -> Unit,
) {
    val vm: ResetPasswordViewModel = viewModel { ResetPasswordViewModel(app) }
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    // 重置成功后短暂展示提示，然后退出（自动登录时连登录页一起退出）
    LaunchedEffect(state.success) {
        if (state.success) {
            delay(1200)
            onResetSuccess(state.autoLoggedIn)
        }
    }

    LaunchedEffect(vm) {
        vm.message.collect { text ->
            text?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                vm.consumeMessage()
            }
        }
    }

    Scaffold(
        topBar = {
            SakuTopAppBar(
                title = "找回密码",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        val error = state.error
        when {
            state.loading -> CenterLoading(Modifier.padding(padding))
            error != null -> ErrorRetry(error, vm::load, Modifier.padding(padding))
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "验证码将发送到填写的邮箱；重置成功后请使用新密码登录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.email,
                    onValueChange = vm::onEmailChange,
                    label = { Text("邮箱") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.code,
                        onValueChange = vm::onCodeChange,
                        label = { Text("邮箱验证码") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = vm::sendCode,
                        enabled = !state.sending && state.countdown == 0,
                    ) {
                        when {
                            state.sending -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            state.countdown > 0 -> Text("${state.countdown}s 后重发")
                            else -> Text("发送验证码")
                        }
                    }
                }
                OutlinedTextField(
                    value = state.password,
                    onValueChange = vm::onPasswordChange,
                    label = { Text("新密码（至少 6 位）") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.confirm,
                    onValueChange = vm::onConfirmChange,
                    label = { Text("确认新密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = vm::submit,
                    enabled = !state.submitting && !state.success,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.submitting) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.success) "已重置" else "重置密码")
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // 滑块验证弹窗（发验证码与提交重置前；数据来自用户真实拖动）
    if (state.showSlider) {
        SliderCaptchaDialog(
            httpClient = app.client.http,
            onDismiss = vm::dismissSlider,
            onSolved = vm::onSliderSolved,
        )
    }
}
