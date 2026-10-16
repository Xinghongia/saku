package top.sakuraxy.client.ui.auth

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import top.sakuraxy.client.SakuApp
import top.sakuraxy.client.ui.components.SakuTopAppBar
import top.sakuraxy.client.vm.LoginViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    app: SakuApp,
    onBack: () -> Unit,
    onOpenSignup: () -> Unit,
    onOpenReset: () -> Unit,
) {
    val vm: LoginViewModel = viewModel { LoginViewModel(app) }
    val state by vm.state.collectAsState()

    // 登录成功后短暂展示成功提示，然后自动返回上一页（不再让用户手动退出登录页）
    LaunchedEffect(state.result) {
        if (state.result is LoginViewModel.LoginResult.Success) {
            delay(900)
            onBack()
        }
    }

    Scaffold(
        topBar = {
            SakuTopAppBar(
                title = "登录",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "用站点账号登录后，可获得与网页登录相同的访问权限。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.username,
                onValueChange = vm::onUsernameChange,
                label = { Text("用户名 / 邮箱") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = vm::onPasswordChange,
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = vm::submit,
                enabled = !state.submitting && state.username.isNotBlank() && state.password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("登录")
            }

            // 与网页登录页一致的两个入口：注册账号 / 找回密码
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onOpenSignup) { Text("注册账号") }
                Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onOpenReset) { Text("找回密码") }
            }

            when (val result = state.result) {
                is LoginViewModel.LoginResult.Success -> {
                    Spacer(Modifier.height(4.dp))
                    Text("登录成功：${result.message}（登录状态已保存）", color = Color(0xFF2E7D32))
                }

                is LoginViewModel.LoginResult.NeedsCaptcha -> {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "需要完成滑块验证：${result.message}",
                        color = Color(0xFFB26A00),
                    )
                }

                is LoginViewModel.LoginResult.Error -> {
                    Spacer(Modifier.height(4.dp))
                    Text("登录失败：${result.message}", color = MaterialTheme.colorScheme.error)
                }

                null -> Unit
            }
        }

        // 滑块验证弹窗：站点要求滑块时出现（数据来自用户真实拖动，见 SliderCaptchaDialog）
        if (state.showSlider) {
            SliderCaptchaDialog(
                httpClient = app.client.http,
                onDismiss = vm::dismissSlider,
                onSolved = vm::loginWith,
            )
        }
    }
}
