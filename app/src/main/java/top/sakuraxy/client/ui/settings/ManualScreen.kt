package top.sakuraxy.client.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import top.sakuraxy.client.SakuApp
import top.sakuraxy.client.core.SiteParser
import top.sakuraxy.client.ui.AndroidActions
import top.sakuraxy.client.ui.components.CenterLoading
import top.sakuraxy.client.ui.components.ContentBlockItem
import top.sakuraxy.client.ui.components.ContentViewerDialog
import top.sakuraxy.client.ui.components.ErrorRetry
import top.sakuraxy.client.ui.components.ImageActionsSheet
import top.sakuraxy.client.ui.components.SakuTopAppBar
import top.sakuraxy.client.ui.components.rememberSaveImage
import top.sakuraxy.client.vm.ManualViewModel

/**
 * 网站使用手册（站点 /manual 页的原生只读渲染）：
 * 认识本站 / 注册 / 登录与找回密码 / 用户中心 / 积分与余额等章节，与网页同一份内容。
 * 正文里的站内链接跳进 App，其余链接打开浏览器。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualScreen(
    app: SakuApp,
    onBack: () -> Unit,
    onOpenPost: (Long) -> Unit,
) {
    val vm: ManualViewModel = viewModel { ManualViewModel(app) }
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    /** 正文图片：点开全屏 / 长按常用操作（与详情页一致） */
    var contentViewer by remember { mutableStateOf<top.sakuraxy.client.core.ContentBlock.Image?>(null) }
    var contentImageMenu by remember { mutableStateOf<top.sakuraxy.client.core.ContentBlock.Image?>(null) }
    val saveImage = rememberSaveImage(app.client.http)

    fun handleLink(url: String) {
        val linkedId = SiteParser.postIdFromUrl(url)
        if (linkedId != null) onOpenPost(linkedId) else AndroidActions.openInBrowser(context, url)
    }

    Scaffold(
        topBar = {
            SakuTopAppBar(
                title = state.title,
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
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(state.blocks) { block ->
                    ContentBlockItem(
                        block = block,
                        linkColor = linkColor,
                        onLink = ::handleLink,
                        onImageTap = { contentViewer = it },
                        onImageLongPress = { contentImageMenu = it },
                    )
                }
            }
        }
    }

    // 正文图片全屏查看 / 长按操作（与详情页同一套组件）
    contentViewer?.let { image ->
        ContentViewerDialog(
            url = image.url,
            description = image.alt,
            onDismiss = { contentViewer = null },
            onSave = { saveImage(image.url) },
            onCopy = { AndroidActions.copyToClipboard(context, image.url) },
            onShare = { AndroidActions.shareText(context, image.url) },
        )
    }
    contentImageMenu?.let { image ->
        ImageActionsSheet(
            onDismiss = { contentImageMenu = null },
            onSave = { contentImageMenu = null; saveImage(image.url) },
            onCopy = { contentImageMenu = null; AndroidActions.copyToClipboard(context, image.url) },
            onShare = { contentImageMenu = null; AndroidActions.shareText(context, image.url) },
            onOpenBrowser = { contentImageMenu = null; AndroidActions.openInBrowser(context, image.url) },
        )
    }
}
