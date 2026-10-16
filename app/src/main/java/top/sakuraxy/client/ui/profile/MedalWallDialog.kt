package top.sakuraxy.client.ui.profile

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import top.sakuraxy.client.core.MedalCategory
import top.sakuraxy.client.core.MedalInfo
import top.sakuraxy.client.core.MedalModal
import top.sakuraxy.client.ui.components.UserAvatar
import top.sakuraxy.client.vm.ProfileViewModel

/** 未获得徽章的灰显滤镜（网页上未获得也是灰色剪影） */
private val GRAYSCALE = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** 分类标签的粉底（对齐网页 pill 样式） */
private val PILL_BG = Color(0xFFFFE0EC)
private val PILL_FG = Color(0xFFE2557E)

/**
 * 「我的徽章」弹窗（对齐网页个人卡「N枚徽章」弹窗，2026-10-16 探测）：
 * 顶部色带（头像 + 我的徽章 + 当前佩戴）；正文按分类分组展示全部徽章，
 * 已获得彩色 / 未获得灰显。站点素材为 SVG（Coil 已注册 SvgDecoder）。
 *
 * 交互：打开时徽章依次弹出（错峰入场动画）；点任意徽章 → 放大展示（弹性缩放）；
 * 当前佩戴徽章在顶栏轻微呼吸。
 */
@Composable
fun MedalWallDialog(
    state: ProfileViewModel.MedalUi,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    var selected by remember { mutableStateOf<MedalInfo?>(null) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        ) {
            Column {
                MedalDialogHeader(
                    modal = (state as? ProfileViewModel.MedalUi.Content)?.modal,
                    onDismiss = onDismiss,
                )
                when (state) {
                    ProfileViewModel.MedalUi.Loading -> Box(
                        Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    is ProfileViewModel.MedalUi.Error -> Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onRetry) { Text("重试") }
                    }

                    is ProfileViewModel.MedalUi.Content -> Column(
                        // 注意顺序：高度上限必须在 verticalScroll 之外（视口高度），
                        // 放在里面会变成对内容截断 → 划不动且后面的分类被压没
                        Modifier
                            .heightIn(max = 520.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        var baseIndex = 0
                        state.modal.categories.forEach { category ->
                            MedalCategorySection(
                                category = category,
                                startIndex = baseIndex,
                                onMedalClick = { selected = it },
                            )
                            baseIndex += category.medals.size
                        }
                    }
                }
            }
        }
    }

    // 放大的单枚徽章（叠在主弹窗之上；点空白/返回键收起）
    selected?.let { medal ->
        EnlargedMedalDialog(medal = medal, onDismiss = { selected = null })
    }
}

/** 顶部色带：头像 + 「我的徽章」+ 当前佩戴徽章小图标（轻微呼吸提示） */
@Composable
private fun MedalDialogHeader(modal: MedalModal?, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF8ABAFF), Color(0xFFDCEBFF)))),
    ) {
        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
            Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = Color(0xFF33517D))
        }
        Column(
            Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            UserAvatar(url = modal?.avatarUrl, size = 44.dp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "我的徽章",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2C4A75),
                )
                val worn = modal?.currentWorn
                val wornIcon = modal?.let { m ->
                    worn?.let { name ->
                        m.categories.flatMap { it.medals }.firstOrNull { it.name == name }?.iconUrl
                    }
                }
                if (wornIcon != null) {
                    val breathing = rememberInfiniteTransition(label = "wornBreath")
                    val scale by breathing.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.18f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(900, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "wornScale",
                    )
                    Spacer(Modifier.width(6.dp))
                    AsyncImage(
                        model = wornIcon,
                        contentDescription = "当前佩戴：$worn",
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                    )
                }
            }
        }
    }
}

/** 单个分类：粉底分类标签 + 每行 5 个徽章 */
@Composable
private fun MedalCategorySection(
    category: MedalCategory,
    startIndex: Int,
    onMedalClick: (MedalInfo) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(shape = CircleShape, color = PILL_BG) {
                Text(
                    category.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PILL_FG,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            category.medals.chunked(5).forEachIndexed { rowIdx, rowMedals ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    rowMedals.forEachIndexed { col, medal ->
                        MedalCell(
                            medal = medal,
                            index = startIndex + rowIdx * 5 + col,
                            onClick = { onMedalClick(medal) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(5 - rowMedals.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** 单个徽章：图标 + 名称；已获得彩色 / 未获得灰显；打开时按 [index] 错峰弹出 */
@Composable
private fun MedalCell(
    medal: MedalInfo,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // 依次弹出的展示动画：按序号错峰（16ms/枚，封顶 480ms 避免末尾等太久）
        delay((index * 16L).coerceAtMost(480L))
        shown = true
    }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "medalCellIn",
    )
    Column(
        modifier
            .padding(vertical = 6.dp)
            .graphicsLayer {
                alpha = progress.coerceAtMost(1f)
                val s = 0.5f + 0.5f * progress
                scaleX = s
                scaleY = s
            }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = medal.iconUrl,
            contentDescription = medal.name,
            alpha = if (medal.earned) 1f else 0.55f,
            colorFilter = if (medal.earned) null else GRAYSCALE,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            medal.name,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 单枚徽章的放大展示（弹性缩放进入）。
 * 网页点单枚徽章会开详情弹窗，但站点该接口当前异常（实测连自己已获得的徽章也返回
 * 「参数传入错误或没有此徽章」）→ 客户端本地放大展示图标 + 名称 + 获得状态。
 */
@Composable
private fun EnlargedMedalDialog(medal: MedalInfo, onDismiss: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "medalZoom",
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = progress.coerceAtMost(1f)
                    val s = 0.4f + 0.6f * progress
                    scaleX = s
                    scaleY = s
                },
        ) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AsyncImage(
                    model = medal.iconUrl,
                    contentDescription = medal.name,
                    alpha = if (medal.earned) 1f else 0.6f,
                    colorFilter = if (medal.earned) null else GRAYSCALE,
                    modifier = Modifier.size(132.dp),
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    medal.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = CircleShape,
                    color = if (medal.earned) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                ) {
                    Text(
                        if (medal.earned) "已获得" else "未获得",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (medal.earned) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}
