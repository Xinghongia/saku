package top.sakuraxy.client.ui.detail

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 详情页骨架屏（2026-10-16 走查反馈）：
 * 正文来自较快的 REST 接口、作者行/计数行来自较慢的整页解析；
 * 按到达顺序渲染时，正文会先占住头部的位置，随后被插入的头部挤下去。
 * 骨架屏预先占住头部空间（首帧即整页骨架），数据到齐后就地替换，正文不再跳动。
 */

/** 呼吸占位色：onSurface 低透明度脉动（浅色主题下浅灰、深色主题下深灰） */
@Composable
private fun skeletonColor(): Color {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    return MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f + 0.09f * pulse)
}

/** 一根占位条（默认占满宽度；宽度/形状由调用方给） */
@Composable
private fun Bar(
    color: Color,
    height: Dp,
    modifier: Modifier = Modifier.fillMaxWidth(),
    shape: Shape = RoundedCornerShape(50),
) {
    Box(modifier.height(height).background(color, shape))
}

/**
 * 头部骨架（作者行 + 计数行 + 面包屑位），高度对照真实头部：
 * 头像 44dp、右侧「关注/私信」两枚 32dp 按钮（行高实际由按钮列决定，约 68dp）。
 */
@Composable
fun DetailHeaderSkeleton(modifier: Modifier = Modifier) {
    HeaderSkeletonContent(skeletonColor(), modifier)
}

@Composable
private fun HeaderSkeletonContent(color: Color, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(color, CircleShape))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Bar(color, 14.dp, Modifier.width(96.dp))
                Bar(color, 11.dp, Modifier.width(64.dp))
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Bar(color, 32.dp, Modifier.width(78.dp), RoundedCornerShape(16.dp))
                Bar(color, 32.dp, Modifier.width(78.dp), RoundedCornerShape(16.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            repeat(3) { index ->
                if (index > 0) Spacer(Modifier.width(12.dp))
                Bar(color, 16.dp, Modifier.width(46.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Bar(color, 22.dp, Modifier.width(56.dp))
            Bar(color, 22.dp, Modifier.width(84.dp))
        }
    }
}

/** 首帧整页骨架（REST 返回前）：标题 + 头部 + 正文条 + 图片块，可滚动 */
@Composable
fun DetailSkeletonPage(modifier: Modifier = Modifier) {
    val color = skeletonColor()
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Bar(color, 24.dp, Modifier.fillMaxWidth(0.94f))
        Bar(color, 24.dp, Modifier.fillMaxWidth(0.6f))
        HeaderSkeletonContent(color)
        Bar(color, 13.dp, Modifier.fillMaxWidth(0.98f))
        Bar(color, 13.dp, Modifier.fillMaxWidth(0.93f))
        Bar(color, 13.dp, Modifier.fillMaxWidth(0.96f))
        Bar(color, 13.dp, Modifier.fillMaxWidth(0.62f))
        Bar(color, 150.dp, Modifier.fillMaxWidth(), RoundedCornerShape(12.dp))
        Bar(color, 13.dp, Modifier.fillMaxWidth(0.97f))
        Bar(color, 13.dp, Modifier.fillMaxWidth(0.9f))
        Bar(color, 13.dp, Modifier.fillMaxWidth(0.45f))
    }
}
