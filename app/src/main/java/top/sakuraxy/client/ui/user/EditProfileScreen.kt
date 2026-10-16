package top.sakuraxy.client.ui.user

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import top.sakuraxy.client.SakuApp
import top.sakuraxy.client.ui.components.CenterLoading
import top.sakuraxy.client.ui.components.CoverPlaceholder
import top.sakuraxy.client.ui.components.ErrorRetry
import top.sakuraxy.client.ui.components.SakuTopAppBar
import top.sakuraxy.client.ui.components.UserAvatar
import top.sakuraxy.client.vm.EditProfileViewModel
import top.sakuraxy.client.vm.readUploadFile

/** 个人资料编辑：头像 / 封面 / 文本资料 / 隐私与性别（与网页用户中心 data 面板等效） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(app: SakuApp, onBack: () -> Unit) {
    val viewModel = viewModel { EditProfileViewModel(app) }
    val state by viewModel.state.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.message.collect { text ->
            text?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                viewModel.consumeMessage()
            }
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { readUploadFile(context, uri) }
            .onSuccess(viewModel::uploadAvatar)
            .onFailure { Toast.makeText(context, it.message ?: "图片读取失败", Toast.LENGTH_SHORT).show() }
    }
    val coverPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { readUploadFile(context, uri) }
            .onSuccess(viewModel::uploadCover)
            .onFailure { Toast.makeText(context, it.message ?: "图片读取失败", Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        topBar = {
            SakuTopAppBar(
                title = "个人资料",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.loading.not() ) {
                        Button(
                            onClick = viewModel::save,
                            enabled = !state.saving,
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text(if (state.saving) "保存中…" else "保存")
                        }
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        val error = state.error
        when {
            state.loading -> CenterLoading(Modifier.padding(padding))
            error != null -> ErrorRetry(error, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 封面
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                        ) {
                            CoverPlaceholder(iconSize = 28.dp)
                            if (!state.coverUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = state.coverUrl,
                                    contentDescription = "个人封面",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("个人封面（建议 1000x500）", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Button(
                                onClick = {
                                    coverPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                                enabled = !state.uploadingCover,
                            ) {
                                Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (state.uploadingCover) "上传中…" else "修改封面")
                            }
                        }
                    }
                }

                // 头像
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box {
                            UserAvatar(url = state.avatarUrl, size = 64.dp)
                            if (state.uploadingAvatar) {
                                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(28.dp))
                                }
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("头像", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "支持 jpg、gif，最大 3M（建议 150x150）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = {
                                avatarPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            enabled = !state.uploadingAvatar,
                        ) {
                            Text("修改头像")
                        }
                    }
                }

                // 文本资料
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("基本资料", style = MaterialTheme.typography.titleSmall)
                        EditProfileViewModel.FIELD_LABELS.forEach { (key, label) ->
                            OutlinedTextField(
                                value = state.values[key].orEmpty(),
                                onValueChange = { viewModel.setValue(key, it) },
                                label = { Text(label) },
                                singleLine = key != "desc",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        // 隐私与性别（站点下拉）
                        state.selects.forEach { select ->
                            var expanded by remember { mutableStateOf(false) }
                            val currentValue = state.values[select.name] ?: select.selectedValue.orEmpty()
                            val currentLabel = select.options.firstOrNull { it.value == currentValue }?.label
                                ?: currentValue
                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = it },
                            ) {
                                OutlinedTextField(
                                    value = currentLabel,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(if (select.name == "privacy") "社交资料可见性" else "性别") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                                )
                                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    select.options.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.label) },
                                            onClick = {
                                                viewModel.setValue(select.name, option.value)
                                                expanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 底部再放一个保存按钮，长表单滚到底不用回头
                Button(
                    onClick = viewModel::save,
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.saving) "保存中…" else "保存资料")
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
