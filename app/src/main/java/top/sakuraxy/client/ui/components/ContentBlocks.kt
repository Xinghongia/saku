package top.sakuraxy.client.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import top.sakuraxy.client.core.ContentBlock
import top.sakuraxy.client.core.TextLink

/**
 * 正文块渲染（文章详情页与使用手册共用）：
 * 段落/标题/图片/分隔线；段落与标题里的链接由 [onLink] 决定去向
 * （调用方：站内文章跳进 App，其余打开浏览器）。
 */
@Composable
fun ContentBlockItem(
    block: ContentBlock,
    linkColor: Color,
    onLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (block) {
        is ContentBlock.Paragraph -> Text(
            text = block.text.toAnnotated(block.links, linkColor, onLink),
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier,
        )

        is ContentBlock.Heading -> Text(
            text = block.text.toAnnotated(block.links, linkColor, onLink),
            style = MaterialTheme.typography.titleMedium,
            modifier = modifier,
        )

        is ContentBlock.Image -> AsyncImage(
            model = block.url,
            contentDescription = block.alt,
            contentScale = ContentScale.FillWidth,
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp)),
        )

        ContentBlock.Separator -> HorizontalDivider(modifier)
    }
}

/** 文本 + 可点击链接（站内文章链接由 onLink 跳转进 App，其余打开浏览器）；链接色随主题 */
fun String.toAnnotated(
    links: List<TextLink>,
    linkColor: Color,
    onLink: (String) -> Unit,
) = buildAnnotatedString {
    append(this@toAnnotated)
    links.forEach { link ->
        addLink(
            LinkAnnotation.Url(
                url = link.url,
                styles = TextLinkStyles(
                    style = androidx.compose.ui.text.SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                    ),
                ),
                linkInteractionListener = { onLink(link.url) },
            ),
            start = link.start,
            end = link.end,
        )
    }
}
