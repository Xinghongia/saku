package top.sakuraxy.client.ui.user

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import top.sakuraxy.client.SakuApp
import top.sakuraxy.client.core.SocialBind
import top.sakuraxy.client.ui.AndroidActions
import top.sakuraxy.client.ui.auth.SliderCaptchaDialog
import top.sakuraxy.client.ui.components.CenterLoading
import top.sakuraxy.client.ui.components.ErrorRetry
import top.sakuraxy.client.ui.components.SakuTopAppBar
import top.sakuraxy.client.vm.AccountSecurityViewModel
import top.sakuraxy.client.vm.AccountSecurityViewModel.Step

/**
 * 账户安全：改邮箱（两步验证）/ 改密码 / 重置密码 / 社交绑定。
 * 与网页一致：每次提交（含发送验证码）都要完成滑块验证；社交绑定走站点自己的 OAuth 外链。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSecurityScreen(app: SakuApp, onBack: () -> Unit) {
    val viewModel: AccountSecurityViewModel = viewModel { AccountSecurityViewModel(app) }
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.message.collect { text ->
            text?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                viewModel.consumeMessage()
            }
        }
    }

    // 流程中按系统返回键回到账户安全首页（与点顶栏返回一致）
    BackHandler(enabled = state.step != Step.MENU) { viewModel.backToMenu() }

    val title = when (state.step) {
        Step.MENU -> "账户安全"
        Step.EMAIL_VERIFY, Step.EMAIL_BIND -> "修改邮箱"
        Step.PASSWORD -> "修改密码"
        Step.RESET -> "重置密码"
    }

    Scaffold(
        topBar = {
            SakuTopAppBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = { if (state.step == Step.MENU) onBack() else viewModel.backToMenu() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        when {
            state.loading -> CenterLoading(Modifier.padding(padding))
            state.error != null -> ErrorRetry(state.error!!, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (state.step) {
                    Step.MENU -> MenuContent(state, viewModel, context)
                    Step.EMAIL_VERIFY -> EmailVerifyContent(state, viewModel)
                    Step.EMAIL_BIND -> EmailBindContent(state, viewModel)
                    Step.PASSWORD -> PasswordContent(state, viewModel)
                    Step.RESET -> ResetContent(state, viewModel)
                }
            }
        }
    }

    if (state.showSlider) {
        SliderCaptchaDialog(
            httpClient = app.client.http,
            onDismiss = viewModel::dismissSlider,
            onSolved = viewModel::onSliderSolved,
        )
    }
}

// ---- 首页（入口列表） ----

@Composable
private fun MenuContent(
    state: AccountSecurityViewModel.UiState,
    viewModel: AccountSecurityViewModel,
    context: android.content.Context,
) {
    SectionCard("绑定邮箱") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("当前邮箱", style = MaterialTheme.typography.bodyMedium)
                Text(
                    state.email ?: "未获取到邮箱",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = { viewModel.openStep(Step.EMAIL_VERIFY) }) { Text("修改") }
        }
    }

    SectionCard("绑定社交账号") {
        if (state.socials.isEmpty()) {
            Text(
                "站点未提供可绑定的社交账号",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.socials.forEach { social -> SocialRow(social, context) }
        }
    }

    SectionCard("账户密码") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("登录密码", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { viewModel.openStep(Step.PASSWORD) }) { Text("修改密码") }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "修改邮箱与密码需要完成滑块验证（与网页一致）；社交绑定在站点自己的页面完成。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SocialRow(social: SocialBind, context: android.content.Context) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(social.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (social.bound) "已绑定" else "暂未绑定",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = { social.bindUrl?.let { AndroidActions.openInBrowser(context, it) } },
            enabled = social.bindUrl != null,
        ) {
            Text(if (social.bound) "管理" else "绑定")
        }
    }
}

// ---- 修改邮箱：步骤 1（验证当前邮箱） ----

@Composable
private fun EmailVerifyContent(
    state: AccountSecurityViewModel.UiState,
    viewModel: AccountSecurityViewModel,
) {
    StepIntro("第一步：验证当前邮箱", "验证码将发送到 ${state.email ?: "当前邮箱"}，输入后进入下一步。")
    CodeField(
        code = state.code,
        onCodeChange = viewModel::onCodeChange,
        countdown = state.countdown,
        sending = state.sending,
        onSend = { viewModel.sendCode(AccountSecurityViewModel.SendKind.VERIFY_OLD) },
    )
    SubmitButton("安全验证", state.submitting, enabled = state.code.isNotBlank()) {
        viewModel.verifyOldEmail()
    }
}

// ---- 修改邮箱：步骤 2（绑定新邮箱） ----

@Composable
private fun EmailBindContent(
    state: AccountSecurityViewModel.UiState,
    viewModel: AccountSecurityViewModel,
) {
    StepIntro("第二步：绑定新邮箱", "输入新的邮箱地址，验证码将发送到新邮箱。")
    OutlinedTextField(
        value = state.newEmail,
        onValueChange = viewModel::onNewEmailChange,
        label = { Text("新邮箱") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    CodeField(
        code = state.code,
        onCodeChange = viewModel::onCodeChange,
        countdown = state.countdown,
        sending = state.sending,
        onSend = { viewModel.sendCode(AccountSecurityViewModel.SendKind.BIND_NEW) },
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = state.agree, onCheckedChange = viewModel::onAgreeChange)
        Text(
            "我已阅读并同意站点的用户协议与隐私政策",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    SubmitButton(
        "确认提交",
        state.submitting,
        enabled = state.newEmail.isNotBlank() && state.code.isNotBlank() && state.agree,
    ) {
        viewModel.bindEmail()
    }
}

// ---- 修改密码 ----

@Composable
private fun PasswordContent(
    state: AccountSecurityViewModel.UiState,
    viewModel: AccountSecurityViewModel,
) {
    StepIntro("修改登录密码", "需要先输入原密码；修改成功后如需重新登录，请使用新密码。")
    OutlinedTextField(
        value = state.passwordOld,
        onValueChange = viewModel::onPasswordOldChange,
        label = { Text("原密码") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.passwordNew,
        onValueChange = viewModel::onPasswordNewChange,
        label = { Text("新密码（至少 6 位）") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.passwordConfirm,
        onValueChange = viewModel::onPasswordConfirmChange,
        label = { Text("确认新密码") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    SubmitButton(
        "确认提交",
        state.submitting,
        enabled = state.passwordOld.isNotBlank() && state.passwordNew.isNotBlank() &&
            state.passwordConfirm.isNotBlank(),
    ) {
        viewModel.changePassword()
    }
    TextButton(onClick = { viewModel.openStep(Step.RESET) }) {
        Text("忘记原密码？重置密码")
    }
}

// ---- 重置密码（忘记原密码） ----

@Composable
private fun ResetContent(
    state: AccountSecurityViewModel.UiState,
    viewModel: AccountSecurityViewModel,
) {
    StepIntro("重置密码", "验证码将发送到填写的邮箱；重置成功后请使用新密码登录。")
    OutlinedTextField(
        value = state.resetEmail,
        onValueChange = viewModel::onResetEmailChange,
        label = { Text("邮箱") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    CodeField(
        code = state.code,
        onCodeChange = viewModel::onCodeChange,
        countdown = state.countdown,
        sending = state.sending,
        onSend = { viewModel.sendCode(AccountSecurityViewModel.SendKind.RESET) },
    )
    OutlinedTextField(
        value = state.passwordNew,
        onValueChange = viewModel::onPasswordNewChange,
        label = { Text("新密码（至少 6 位）") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.passwordConfirm,
        onValueChange = viewModel::onPasswordConfirmChange,
        label = { Text("确认新密码") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    SubmitButton(
        "确认重置",
        state.submitting,
        enabled = state.resetEmail.isNotBlank() && state.code.isNotBlank() &&
            state.passwordNew.isNotBlank() && state.passwordConfirm.isNotBlank(),
    ) {
        viewModel.resetPassword()
    }
}

// ---- 复用件 ----

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun StepIntro(title: String, hint: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 验证码输入 + 「获取验证码」按钮（发送中/倒计时禁用，与网页 captchsubmit 一致） */
@Composable
private fun CodeField(
    code: String,
    onCodeChange: (String) -> Unit,
    countdown: Int,
    sending: Boolean,
    onSend: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            label = { Text("验证码") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onSend, enabled = countdown == 0 && !sending) {
            if (sending) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text(if (countdown > 0) "${countdown}s 后重发" else "获取验证码")
            }
        }
    }
}

@Composable
private fun SubmitButton(text: String, submitting: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !submitting && enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (submitting) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text)
    }
}
