package top.sakuraxy.client.ui.user

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import top.sakuraxy.client.SakuApp
import top.sakuraxy.client.ui.auth.SliderCaptchaDialog
import top.sakuraxy.client.ui.components.CenterLoading
import top.sakuraxy.client.ui.components.ErrorRetry
import top.sakuraxy.client.ui.components.SakuTopAppBar
import top.sakuraxy.client.vm.NewPostViewModel

/**
 * 发布文章（/newposts）：标题 / 正文 / 分类多选 / 标签 + 存草稿 / 提交审核。
 * 与网页一致：提交需要完成滑块验证；「提交审核」会把内容真实提交到站点。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewPostScreen(
    app: SakuApp,
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenPost: (Long) -> Unit,
) {
    val viewModel: NewPostViewModel = viewModel { NewPostViewModel(app) }
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

    // 提交审核成功后：能解析出文章 id 就跳到详情
    LaunchedEffect(state.publishedPostId) {
        state.publishedPostId?.let {
            onOpenPost(it)
            viewModel.consumePublishedPostId()
        }
    }

    Scaffold(
        topBar = {
            SakuTopAppBar(
                title = "发布文章",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        when {
            state.needLogin -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("登录后才能发布内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onOpenLogin) { Text("去登录") }
            }

            state.loading -> CenterLoading(Modifier.padding(padding))
            state.error != null -> ErrorRetry(state.error!!, onRetry = viewModel::load, modifier = Modifier.padding(padding))

            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::onTitleChange,
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.content,
                    onValueChange = viewModel::onContentChange,
                    label = { Text("正文") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp),
                )
                Text(
                    "正文按站点编辑器的文本模式提交：支持换行与 HTML 标签。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text("分类（可多选）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (state.categories.isEmpty()) {
                    Text(
                        "站点没有可选分类",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        state.categories.forEach { category ->
                            FilterChip(
                                selected = category.id in state.selected,
                                onClick = { viewModel.toggleCategory(category.id) },
                                label = {
                                    Text(if (category.depth > 0) "└ ${category.name}" else category.name)
                                },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = state.tags,
                    onValueChange = viewModel::onTagsChange,
                    label = { Text("标签") },
                    placeholder = { Text("多个标签用逗号分隔") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = viewModel::saveDraft,
                        enabled = !state.submitting,
                        modifier = Modifier.weight(1f),
                    ) { Text("存草稿") }
                    Button(
                        onClick = viewModel::requestPublish,
                        enabled = !state.submitting,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.submitting) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("提交审核")
                    }
                }
                Text(
                    "「提交审核」与网页的「提交 审核」一致：内容会真实提交到站点，由站点审核后发布。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (state.confirmPublish) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPublish,
            title = { Text("提交审核？") },
            text = { Text("文章将提交到站点进行审核（与网页行为一致）。") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmPublish) { Text("提交") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPublish) { Text("取消") }
            },
        )
    }

    if (state.showSlider) {
        SliderCaptchaDialog(
            httpClient = app.client.http,
            onDismiss = viewModel::dismissSlider,
            onSolved = viewModel::onSliderSolved,
        )
    }
}
