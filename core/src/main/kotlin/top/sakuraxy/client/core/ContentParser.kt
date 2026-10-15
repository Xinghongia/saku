package top.sakuraxy.client.core

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** 正文里的可点击链接（start/end 是相对所在块的字符偏移） */
data class TextLink(val start: Int, val end: Int, val url: String)

/** 正文的可渲染块 */
sealed interface ContentBlock {
    data class Paragraph(val text: String, val links: List<TextLink> = emptyList()) : ContentBlock
    data class Heading(val text: String, val links: List<TextLink> = emptyList()) : ContentBlock
    data class Image(val url: String, val alt: String) : ContentBlock
    data object Separator : ContentBlock
}

/**
 * 把文章正文 HTML 拆成可原生渲染的内容块。
 *
 * 站点正文来自 WordPress 区块编辑器（wp-block-* 结构），图片用 data-src 懒加载。
 * 下载按钮所在区块（wp-block-buttons）交给 Golink 处理，这里跳过，避免重复展示。
 * 正文里的链接保留为 TextLink（站内文章链接可跳转、外部链接可打开浏览器）。
 */
object ContentParser {

    fun parse(contentHtml: String): List<ContentBlock> {
        if (contentHtml.isBlank()) return emptyList()
        val body = Jsoup.parse(contentHtml).body()
        val blocks = mutableListOf<ContentBlock>()
        appendBlocks(body, blocks)
        return blocks
    }

    private fun appendBlocks(parent: Element, out: MutableList<ContentBlock>) {
        for (el in parent.children()) {
            when (el.tagName().lowercase()) {
                "figure" -> {
                    val image = imageOf(el)
                    if (image != null) out += image else appendBlocks(el, out)
                }

                "img" -> imageOf(el)?.let { out += it }

                "hr" -> out += ContentBlock.Separator

                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    val (text, links) = textAndLinks(el)
                    if (text.isNotBlank()) out += ContentBlock.Heading(text, links)
                }

                "p" -> {
                    val (text, links) = textAndLinks(el)
                    if (text.isNotBlank()) out += ContentBlock.Paragraph(text, links)
                }

                "ul", "ol" -> {
                    val items = el.select("> li").map { textAndLinks(it).first }.filter { it.isNotBlank() }
                    if (items.isNotEmpty()) {
                        out += ContentBlock.Paragraph(items.joinToString("\n") { "· $it" })
                    }
                }

                "div" -> {
                    if (el.hasClass("wp-block-buttons")) continue // 下载按钮区块，另行处理
                    appendBlocks(el, out)
                }

                "blockquote" -> {
                    val (text, links) = textAndLinks(el)
                    if (text.isNotBlank()) out += ContentBlock.Paragraph(text, links)
                }

                "script", "style", "noscript" -> Unit

                else -> {
                    val (text, links) = textAndLinks(el)
                    if (text.isNotBlank()) out += ContentBlock.Paragraph(text, links)
                }
            }
        }
    }

    /** 取图：优先懒加载的 data-src；占位图（thumbnail*.svg）不算真图 */
    private fun imageOf(el: Element): ContentBlock.Image? {
        val img = if (el.tagName() == "img") el else el.selectFirst("img") ?: return null
        val url = img.attr("data-src").takeIf { it.startsWith("http") }
            ?: img.attr("src").takeIf { it.startsWith("http") && !it.contains("thumbnail") }
            ?: return null
        return ContentBlock.Image(url = url, alt = img.attr("alt"))
    }

    /**
     * 提取元素文本与内部链接。
     * 规则与旧版 text() 行为一致：<br> 转换行、实体解码、压缩连续空白、
     * 换行两侧不带空格；链接偏移在归一化后保证与文本对齐。
     */
    private fun textAndLinks(el: Element): Pair<String, List<TextLink>> {
        val sb = StringBuilder()
        val links = mutableListOf<TextLink>()

        fun appendText(raw: String) {
            val collapsed = raw.replace(Regex("[ \\t\\r\\n]+"), " ")
            if (collapsed.isEmpty()) return
            val text = if (sb.isNotEmpty() && sb.last() == '\n') collapsed.trimStart(' ') else collapsed
            sb.append(text)
        }

        fun appendBreak() {
            while (sb.isNotEmpty() && sb.last() == ' ') sb.deleteCharAt(sb.length - 1)
            if (sb.isNotEmpty() && sb.last() == '\n') return
            sb.append('\n')
        }

        fun walk(node: Node) {
            when (node) {
                is TextNode -> appendText(node.wholeText)
                is Element -> when (node.tagName().lowercase()) {
                    "br" -> appendBreak()
                    "script", "style" -> Unit
                    "a" -> {
                        val start = sb.length
                        node.childNodes().forEach(::walk)
                        val href = node.absUrl("href").ifBlank { node.attr("href") }
                        val end = minOf(sb.length, sb.length)
                        if (href.isNotBlank() && end > start) links += TextLink(start, end, href)
                    }

                    else -> node.childNodes().forEach(::walk)
                }

                else -> Unit
            }
        }

        el.childNodes().forEach(::walk)

        // br 收紧时可能删掉了某个链接末尾的空格，把越界的链接端点夹回来
        val clamped = links.mapNotNull { link ->
            val end = minOf(link.end, sb.length)
            if (end > link.start) link.copy(end = end) else null
        }

        // 首尾空白裁剪后平移链接偏移
        val raw = sb.toString()
        val headTrim = raw.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) raw.length else it }
        val trimmed = raw.trim()
        val shifted = clamped.mapNotNull { link ->
            val start = link.start - headTrim
            val end = minOf(link.end - headTrim, trimmed.length)
            if (start in 0 until end) TextLink(start, end, link.url) else null
        }
        return trimmed to shifted
    }
}
