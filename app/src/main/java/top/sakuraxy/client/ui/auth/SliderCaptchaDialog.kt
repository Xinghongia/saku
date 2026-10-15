package top.sakuraxy.client.ui.auth

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.sakuraxy.client.core.SliderCaptcha

/**
 * 滑块拼图验证弹窗 —— 复刻 zibll 主题网页端的滑块交互（2026-10-15 实测流程）。
 *
 * 与网页的对应关系：
 * - 出题：随机缺口位置 + 21 张背景图之一（[SliderCaptcha.newLocalChallenge]），
 *   并向站点 captcha.php 换取 token/rand_str/check；
 * - 拖动：手指按在下方滑动条上拖，拼图块同步移动（网页 220/240 换算，[SliderCaptcha.dragToDistance]）；
 *   拖动过程中记录纵向轨迹；
 * - 松手：|拖动位置 - 缺口| < 8 视为对齐，生成提交数据（数据全部来自真实拖动）；
 *   未对齐则换一张图重新来（与网页失败重试一致）。
 *
 * 弹窗只负责"产生真实的验证数据"；是否关闭、如何提交由调用方（登录页）决定。
 */
@Composable
fun SliderCaptchaDialog(
    httpClient: OkHttpClient,
    onDismiss: () -> Unit,
    onSolved: (SliderCaptcha.Solution) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 360.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Outlined.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text("滑动以完成验证", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(12.dp))

                SliderCaptchaContent(
                    httpClient = httpClient,
                    onSolved = onSolved,
                )
            }
        }
    }
}

private sealed interface Stage {
    data object Loading : Stage
    data class Ready(
        val challenge: SliderCaptcha.Challenge,
        val bitmap: ImageBitmap,
    ) : Stage

    data class Failed(val message: String) : Stage
}

@Composable
private fun SliderCaptchaContent(
    httpClient: OkHttpClient,
    onSolved: (SliderCaptcha.Solution) -> Unit,
) {
    var stage by remember { mutableStateOf<Stage>(Stage.Loading) }
    var attempt by remember { mutableIntStateOf(0) }
    var failTick by remember { mutableIntStateOf(0) }
    var failMessage by remember { mutableStateOf<String?>(null) }

    // 出题：本地随机数 + 站点 captcha.php 凭据 + 背景图；失败如实展示，可重试
    LaunchedEffect(attempt) {
        stage = Stage.Loading
        failMessage = null
        stage = try {
            val local = SliderCaptcha.newLocalChallenge()
            val challenge = SliderCaptcha.requestChallenge(httpClient, local)
            val bitmap = loadBitmap(httpClient, SliderCaptcha.imageUrl(local.imageIndex).toString())
                ?: error("背景图加载失败")
            Stage.Ready(challenge, bitmap)
        } catch (e: Exception) {
            Stage.Failed(e.message ?: "滑块数据加载失败")
        }
    }

    // 拖动未对齐：提示后自动换题重来（网页失败即刷新图片）
    LaunchedEffect(failTick) {
        if (failTick > 0) {
            delay(700)
            attempt++
        }
    }

    when (val s = stage) {
        is Stage.Loading -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                Spacer(Modifier.height(8.dp))
                Text("正在加载验证数据…", style = MaterialTheme.typography.bodySmall)
            }
        }

        is Stage.Failed -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("验证数据加载失败：${s.message}", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { attempt++ }) { Text("重试") }
        }

        is Stage.Ready -> Puzzle(
            challenge = s.challenge,
            bitmap = s.bitmap,
            failMessage = failMessage,
            onAligned = { distance, trail ->
                onSolved(
                    SliderCaptcha.solve(s.challenge, SliderCaptcha.DragResult(distance, trail))
                )
            },
            onMisaligned = {
                failMessage = "拼图未对齐，请再试一次"
                failTick++
            },
        )
    }
}

@Composable
private fun Puzzle(
    challenge: SliderCaptcha.Challenge,
    bitmap: ImageBitmap,
    failMessage: String?,
    onAligned: (distance: Int, trail: List<Int>) -> Unit,
    onMisaligned: () -> Unit,
) {
    // 拖动状态：a = 手指水平位移（逻辑坐标 0..240），trail = 纵向偏移轨迹
    var dragAmount by remember(challenge) { mutableStateOf(0f) }
    var trail by remember(challenge) { mutableStateOf(listOf<Int>()) }
    var startPoint by remember(challenge) { mutableStateOf(Offset.Zero) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // 拼图区：背景图 + 缺口 + 可移动拼图块（逻辑坐标 280×170 等比缩放）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(280f / 170f)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val scale = size.width / 280f
                val distance = SliderCaptcha.dragToDistance(dragAmount)
                withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
                    // 背景图（网页 canvas 内绘制尺寸 278×168）
                    drawImage(
                        bitmap,
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(278, 168),
                    )
                    // 缺口：网页 fill rgba(100,100,100,.7) + stroke rgba(255,255,255,.5)
                    val holePath = puzzlePath(challenge.x.toFloat(), challenge.y.toFloat())
                    drawPath(holePath, Color(100, 100, 100, alpha = 178))
                    drawPath(
                        holePath,
                        Color.White.copy(alpha = 0.5f),
                        style = Stroke(width = 2f),
                    )
                    // 拼图块：形状裁切 + 图片按缺口处的图案绘制，随拖动平移（d - x 与网页等效）
                    clipPath(puzzlePath(distance.toFloat(), challenge.y.toFloat())) {
                        withTransform({ translate((distance - challenge.x).toFloat(), 0f) }) {
                            drawImage(
                                bitmap,
                                dstOffset = IntOffset.Zero,
                                dstSize = IntSize(278, 168),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // 滑动条：整条都可按住拖动（移动端手指友好；位移语义与网页按钮拖动一致）
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(8.dp),
                )
                .pointerInput(challenge) {
                    val logicalScale = size.width / 280f
                    detectDragGestures(
                        onDragStart = { offset ->
                            startPoint = offset
                            dragAmount = 0f
                            trail = emptyList()
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val foot = change.position
                            dragAmount = ((foot.x - startPoint.x) / logicalScale)
                                .coerceIn(0f, SliderCaptcha.MAX_DRAG.toFloat())
                            trail = trail + ((foot.y - startPoint.y) / logicalScale).roundToInt()
                        },
                        onDragEnd = {
                            val distance = SliderCaptcha.dragToDistance(dragAmount)
                            if (SliderCaptcha.isAligned(distance, challenge.x)) {
                                onAligned(distance, trail)
                            } else {
                                onMisaligned()
                            }
                        },
                        onDragCancel = {
                            onMisaligned()
                        },
                    )
                },
        ) {
            val buttonSize = maxWidth * (40f / 280f)
            Text(
                text = if (failMessage != null) "请再试一次" else "向右滑动填充拼图",
                style = MaterialTheme.typography.bodySmall,
                color = if (failMessage != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.align(Alignment.Center),
            )
            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .offset(x = maxWidth * (dragAmount / 280f))
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = "拖动滑块",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(buttonSize * 0.6f),
                )
            }
        }
    }
}

/**
 * 拼图形状路径（复刻网页 slidercaptcha 的 canvas 路径：上凸 + 右凸 + 左凹）。
 * 坐标系与网页一致：x/y 为形状左上角（逻辑坐标），圆角参数 sliderL=42、sliderR=9。
 */
private fun puzzlePath(x: Float, y: Float): Path {
    val a = 42f
    val s = 9f
    return Path().apply {
        moveTo(x, y)
        // 上边中部凸起（网页 arc(r+a/2, c-s+2, s, .72π, 2.26π)）
        arcTo(
            rect = Rect(center = Offset(x + a / 2, y - s + 2), radius = s),
            startAngleDegrees = 129.6f,
            sweepAngleDegrees = 277.2f,
            forceMoveTo = false,
        )
        lineTo(x + a, y)
        // 右边中部凸起（网页 arc(r+a+s-2, c+a/2, s, 1.21π, 2.78π)）
        arcTo(
            rect = Rect(center = Offset(x + a + s - 2, y + a / 2), radius = s),
            startAngleDegrees = 217.8f,
            sweepAngleDegrees = 282.6f,
            forceMoveTo = false,
        )
        lineTo(x + a, y + a)
        lineTo(x, y + a)
        // 左边中部凹陷（网页 arc(r+s-2, c+a/2, s+.4, 2.76π, 1.24π, anticlockwise)）
        arcTo(
            rect = Rect(center = Offset(x + s - 2, y + a / 2), radius = s + 0.4f),
            startAngleDegrees = 496.8f,
            sweepAngleDegrees = -273.6f,
            forceMoveTo = false,
        )
        lineTo(x, y)
        close()
    }
}

/** 背景图下载（走与登录一致的 HTTP 客户端：UA/cookie 等效于网页） */
private suspend fun loadBitmap(client: OkHttpClient, url: String): ImageBitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                BitmapFactory.decodeStream(response.body.byteStream())?.asImageBitmap()
            }
        }.getOrNull()
    }
