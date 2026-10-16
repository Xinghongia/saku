package top.sakuraxy.client.ui.auth

import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
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
import top.sakuraxy.client.core.Site
import top.sakuraxy.client.ui.AndroidActions
import top.sakuraxy.client.ui.components.CenterLoading
import top.sakuraxy.client.ui.components.ErrorRetry
import top.sakuraxy.client.ui.components.SakuTopAppBar
import top.sakuraxy.client.vm.SignupViewModel

/**
 * 注册（网站注册流程的原生复刻）：
 * 用户名 + 邮箱 + 邮箱验证码 + 密码 + 同意用户协议；发码与提交都先过滑块。
 * 注册会真实创建站点账号（与网页注册等效）——资料由用户亲手填写。
 *
 * [onRegistered] 参数 = 站点是否「注册后自动登录」（应答带登录 cookie，与网页刷新后一致）：
 * 自动登录时由调用方连同登录页一起退出（回到来处，已是登录态）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
    app: SakuApp,
    onBack: () -> Unit,
    onRegistered: (autoLoggedIn: Boolean) -> Unit,
) {
    val vm: SignupViewModel = viewModel { SignupViewModel(app) }
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    // 注册成功后短暂展示提示，然后退出（自动登录时连登录页一起退出）
    LaunchedEffect(state.success) {
        if (state.success) {
            delay(1200)
            onRegistered(state.autoLoggedIn)
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
                title = "注册账号",
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
                    "注册站点账号（与网页注册等效，会真实创建账号）；验证码与提交都需完成滑块验证。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.name,
                    onValueChange = vm::onNameChange,
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
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
                    label = { Text("密码（至少 6 位）") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = state.agree, onCheckedChange = vm::onAgreeChange)
                    Text("我已阅读并同意", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "《用户协议》",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            AndroidActions.openInBrowser(context, "${Site.BASE_URL}/user-agreement")
                        },
                    )
                    Text("、", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "《隐私声明》",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            AndroidActions.openInBrowser(context, "${Site.BASE_URL}/privacy-policy")
                        },
                    )
                }
                Button(
                    onClick = vm::submit,
                    enabled = !state.submitting && !state.success,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.submitting) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.success) "注册成功" else "注册")
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // 滑块验证弹窗（发验证码与提交注册前；数据来自用户真实拖动）
    if (state.showSlider) {
        SliderCaptchaDialog(
            httpClient = app.client.http,
            onDismiss = vm::dismissSlider,
            onSolved = vm::onSliderSolved,
        )
    }
}
