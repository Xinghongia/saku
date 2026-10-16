package top.sakuraxy.client.ui.components

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 正文图片（文章/手册里的图）的查看与常用操作：
 * 点图 → 全屏查看（双指缩放、双击放大、拖动平移）；长按 → 常用操作（保存到相册等）。
 * 保存 = 抓取图片本身（与网页右键另存的同一份图）写入系统相册，不经过任何第三方服务。
 */

/** 图片体积上限（站点配图通常 <5MB，25MB 足够宽松） */
private const val MAX_IMAGE_BYTES = 25L * 1024 * 1024

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentViewerDialog(
    url: String,
    description: String?,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var container by remember { mutableStateOf(IntSize.Zero) }
        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            val target = (scale * zoomChange).coerceIn(1f, 6f)
            scale = target
            offset = if (target <= 1f) {
                Offset.Zero
            } else {
                // 平移限制在放大后可移动的范围内，图片不会被拖出屏幕
                val maxX = (target - 1f) * container.width / 2f
                val maxY = (target - 1f) * container.height / 2f
                Offset(
                    (offset.x + panChange.x).coerceIn(-maxX, maxX),
                    (offset.y + panChange.y).coerceIn(-maxY, maxY),
                )
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = 44.dp, bottom = 120.dp)
                    .onSizeChanged { container = it }
                    .transformable(transformState)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            // 未放大时点空白处关闭；放大后单击不关闭（先双击复位）
                            onTap = { if (scale <= 1f) onDismiss() },
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = description,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = Color.White)
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "双指缩放 · 双击放大",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(10.dp))
                // 三按钮同排会超出屏宽（分享被挤成细条），改为「主按钮整行 + 两副按钮平分」
                FilledTonalButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("保存到相册")
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 黑底上的描边按钮必须显式用白字白边，否则跟随浅色主题的深色文字看不清
                    OutlinedButton(
                        onClick = onCopy,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("复制链接")
                    }
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("分享")
                    }
                }
            }
        }
    }
}

/** 长按图片弹出的常用操作（保存到相册 / 复制链接 / 分享链接 / 浏览器打开） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageActionsSheet(
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onOpenBrowser: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Text(
                "图片操作",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            SheetAction(Icons.Outlined.Download, "保存到本地相册", onSave)
            SheetAction(Icons.Outlined.ContentCopy, "复制图片链接", onCopy)
            SheetAction(Icons.Outlined.Share, "分享链接", onShare)
            SheetAction(Icons.Outlined.Language, "在浏览器打开", onOpenBrowser)
        }
    }
}

@Composable
private fun SheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * 保存图片到系统相册：返回一个「(url) -> Unit」动作，自带成败提示。
 * Android 9 及以下需要存储权限（先申请）；Android 10+ 走 MediaStore 沙箱路径，无需权限。
 */
@Composable
fun rememberSaveImage(client: OkHttpClient): (String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingUrl by remember { mutableStateOf<String?>(null) }

    fun doSave(url: String) {
        Toast.makeText(context, "正在保存…", Toast.LENGTH_SHORT).show()
        scope.launch {
            val result = saveImageToGallery(context, client, url)
            Toast.makeText(
                context,
                result.fold({ name -> "已保存到相册：$name" }, { "保存失败：${it.message ?: "网络异常"}" }),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val url = pendingUrl
        pendingUrl = null
        when {
            granted && url != null -> doSave(url)
            else -> Toast.makeText(context, "需要存储权限才能保存图片", Toast.LENGTH_SHORT).show()
        }
    }

    return remember(client) {
        { url ->
            val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                pendingUrl = url
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                doSave(url)
            }
        }
    }
}

/** 抓图（限流读取，超限放弃）→ 写入 MediaStore 相册；返回保存的文件名 */
private suspend fun saveImageToGallery(
    context: Context,
    client: OkHttpClient,
    url: String,
): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        val bytes = fetchBytes(client, url)
        val (mime, ext) = guessMime(url)
        val name = "sakuraxy_${System.currentTimeMillis()}.$ext"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/SakuraXy")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("无法创建相册文件")
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("无法写入文件")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                    null,
                    null,
                )
            }
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        name
    }
}

private suspend fun fetchBytes(client: OkHttpClient, url: String): ByteArray = withContext(Dispatchers.IO) {
    val request = Request.Builder().url(url).get().build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
        val declared = response.body.contentLength()
        if (declared > MAX_IMAGE_BYTES) throw IOException("图片过大（${declared / 1024 / 1024}MB）")
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        val input = response.body.byteStream()
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_IMAGE_BYTES) throw IOException("图片过大")
            out.write(buffer, 0, read)
        }
        if (out.size() == 0) throw IOException("图片为空")
        out.toByteArray()
    }
}

private fun guessMime(url: String): Pair<String, String> {
    val path = url.substringBefore('?').lowercase()
    return when {
        path.endsWith(".png") -> "image/png" to "png"
        path.endsWith(".webp") -> "image/webp" to "webp"
        path.endsWith(".gif") -> "image/gif" to "gif"
        else -> "image/jpeg" to "jpg"
    }
}
