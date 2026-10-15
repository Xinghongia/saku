package top.sakuraxy.client.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ContentParserTest {

    private val post by lazy {
        SakuJson.decodeFromString<WpPost>(fixture("wp_post_sample.json"))
    }

    @Test
    fun `把正文按页面顺序拆成内容块，下载按钮区块不重复展示`() {
        val blocks = ContentParser.parse(post.content!!.rendered)

        assertEquals(6, blocks.size)
        assertEquals(ContentBlock.Paragraph("示例资源简介文本（用于解析测试）。"), blocks[0])
        assertEquals(ContentBlock.Heading("示例标题"), blocks[1])
        assertEquals(
            ContentBlock.Image(
                url = "https://www.example.com/wp-content/uploads/2025/12/img-1024x759.jpg",
                alt = "图片[1]-示例",
            ),
            blocks[2]
        )
        assertEquals(ContentBlock.Paragraph("免责声明：示例加粗文本\n第二行内容"), blocks[3])
        assertEquals(ContentBlock.Separator, blocks[4])
        // 下载按钮区块（wp-block-buttons）被跳过；最后的普通段落照常渲染，链接保留
        val last = blocks[5] as ContentBlock.Paragraph
        assertEquals("相关链接（无下载参数）", last.text)
        assertEquals("https://www.example.com/10879", last.links.single().url)
    }

    @Test
    fun `懒加载图片优先取 data-src，占位图被忽略`() {
        val image = ContentParser.parse(post.content!!.rendered)
            .filterIsInstance<ContentBlock.Image>()
            .single()

        assertEquals("https://www.example.com/wp-content/uploads/2025/12/img-1024x759.jpg", image.url)
    }

    @Test
    fun `只有占位图的图片块不会产生内容块`() {
        val html = """<figure class="wp-block-image"><img src="https://www.example.com/wp-content/themes/zibll/img/thumbnail-lg.svg" /></figure>"""
        assertEquals(0, ContentParser.parse(html).size)
    }

    @Test
    fun `HTML 实体与换行被正确还原`() {
        val blocks = ContentParser.parse("""<p class="wp-block-paragraph">A&#8211;B &amp; C<br>下一行</p>""")
        assertEquals(ContentBlock.Paragraph("A–B & C\n下一行"), blocks.single())
    }

    @Test
    fun `列表渲染为逐条文本`() {
        val blocks = ContentParser.parse(
            """<ul class="wp-block-list"><li>条目一</li><li>条目二</li></ul>"""
        )
        assertEquals(ContentBlock.Paragraph("· 条目一\n· 条目二"), blocks.single())
    }

    @Test
    fun `空正文返回空列表`() {
        assertEquals(0, ContentParser.parse("").size)
        assertEquals(0, ContentParser.parse("   ").size)
    }

    @Test
    fun `正文里的链接保留为可点击范围`() {
        val blocks = ContentParser.parse(
            """<p class="wp-block-paragraph">看看 <a href="https://www.example.com/4707">这篇文章</a> 和 <a href="https://pan.example.com/s/x">网盘</a> 吧</p>"""
        )

        val paragraph = blocks.single() as ContentBlock.Paragraph
        assertEquals("看看 这篇文章 和 网盘 吧", paragraph.text)
        assertEquals(2, paragraph.links.size)
        val first = paragraph.links[0]
        assertEquals("这篇文章", paragraph.text.substring(first.start, first.end))
        assertEquals("https://www.example.com/4707", first.url)
        val second = paragraph.links[1]
        assertEquals("网盘", paragraph.text.substring(second.start, second.end))
    }

    @Test
    fun `链接在加粗与嵌套结构里也能正确定位`() {
        val blocks = ContentParser.parse(
            """<p><strong><a href="https://www.example.com/1">A</a>B</strong>C</p>"""
        )

        val paragraph = blocks.single() as ContentBlock.Paragraph
        assertEquals("ABC", paragraph.text)
        val link = paragraph.links.single()
        assertEquals("A", paragraph.text.substring(link.start, link.end))
    }

    @Test
    fun `标题里的链接同样保留`() {
        val blocks = ContentParser.parse(
            """<h2 class="wp-block-heading" id="h-x"><a href="https://www.example.com/9">跳转</a></h2>"""
        )

        val heading = blocks.single() as ContentBlock.Heading
        assertEquals("跳转", heading.text)
        assertEquals("https://www.example.com/9", heading.links.single().url)
    }

    @Test
    fun `Html toText 解出标题里的实体与标签`() {
        assertEquals("[示例] 测试资源标题 1", Html.toText(post.title.rendered))
        assertEquals("A–B", Html.toText("<strong>A</strong>&#8211;B"))
    }
}
