package top.sakuraxy.client.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteApiTest {

    private val listHtml by lazy { fixture("list_page_sample.html") }

    // ---------- 列表页（样本 = 2026-10-15 站点真实页面脱敏副本） ----------

    @Test
    fun `列表页解析出全部卡片与分页信息`() {
        val page = SiteParser.parseListPage(listHtml)

        assertEquals(4, page.cards.size)
        assertEquals(1, page.currentPage)
        assertEquals(48, page.maxPage)
        assertEquals("https://www.example.com/av-media/page/2", page.nextPageUrl)
    }

    @Test
    fun `首页卡片解析出封面徽章付费与多级分类`() {
        val card = SiteParser.parseListPage(listHtml).cards[0]

        assertEquals(35711L, card.id)
        assertEquals("https://www.example.com/35711", card.url)
        assertTrue("标题应保留原文：${card.title}", card.title.startsWith("Hit’n’Mix RipX DAW Pro 8.0.3"))
        assertTrue("封面取实际图片", card.coverUrl!!.endsWith("-800x450.jpg"))
        assertEquals("置顶", card.badge)
        assertEquals("付费资源", card.payLabel)
        assertEquals("2", card.payPoints)
        assertEquals(
            listOf("AI 人工智能", "音视频媒体", "音频编辑"),
            card.categories.map { it.name },
        )
        assertEquals(
            listOf("/ai-tools", "/av-media", "/av-media/audio-editing"),
            card.categories.map { it.url.removePrefix("https://www.example.com") },
        )
        assertEquals(listOf("樱 玄夜", "音频编辑", "音频分离"), card.tags)
        assertEquals(0, card.commentCount)
        assertEquals(85, card.viewCount)
        assertEquals(0, card.likeCount)
        assertEquals("钻石会员", card.authorVipBadge)
        // 首页卡片不显示作者名：相对时间 vs 无标题属性区分，名字从头像 alt 兜底
        assertEquals("前天", card.dateText)
        assertEquals("2026-10-13 10:13:34", card.dateTitle)
        assertEquals("是玄夜不是玄月", card.authorName)
    }

    @Test
    fun `分类页卡片解析出作者名与相对时间`() {
        val card = SiteParser.parseListPage(listHtml).cards[2]

        assertEquals("示例读者A", card.authorName)
        assertEquals("21小时前", card.dateText)
        assertEquals("2026-10-14 23:53:43", card.dateTitle)
        assertEquals(1817, card.viewCount)
        assertEquals(8, card.likeCount)
        assertTrue(card.tags.contains("官中"))
        assertNull(card.payLabel)
        assertEquals(0, card.categories.size)
    }

    @Test
    fun `懒加载封面取 data-src，占位图不会被当成封面`() {
        val cards = SiteParser.parseListPage(listHtml).cards

        // card1 是懒加载卡片：src 是占位图、data-src 才是真图
        assertTrue(cards[1].coverUrl!!.endsWith(".jpg"))
        assertTrue(cards.all { it.coverUrl?.contains("thumbnail") != true })
    }

    @Test
    fun `排序项按站点页面实际配置解析`() {
        val options = SiteParser.parseSortOptions(listHtml)

        val keys = options.map { it.key }
        assertTrue(keys.containsAll(listOf("modified", "views", "like", "comment_count")))
        assertEquals("浏览", options.first { it.key == "views" }.label)
    }

    @Test
    fun `内置排序清单覆盖站点全部排序方式`() {
        val keys = SiteParser.knownSortOptions.map { it.key }
        assertEquals(
            listOf(
                "modified", "date", "views", "like", "comment_count",
                "favorite", "zibpay_price", "zibpay_points_price", "sales_volume", "rand",
            ),
            keys,
        )
    }

    @Test
    fun `分类页头部解析出分类名与文章数`() {
        val html = """
            <div class="page-cover-con"><div class="title-h-left">
            <b><i class="fa fa-folder-open"></i>音视频媒体<span class="icon-spot">共571篇</span></b>
            </div></div>
        """.trimIndent()

        val header = SiteParser.parseCategoryHeader(html)

        assertNotNull(header)
        assertEquals("音视频媒体", header!!.name)
        assertEquals("共571篇", header.postCountText)
    }

    @Test
    fun `没有分类头部时如实返回 null`() {
        assertNull(SiteParser.parseCategoryHeader("<html><body></body></html>"))
    }

    // ---------- 列表页地址构造 ----------

    @Test
    fun `列表页地址按 WordPress 规则拼接`() {
        assertEquals(
            "https://www.example.com/",
            SiteParser.listUrl("https://www.example.com", null, null, 1).toString(),
        )
        assertEquals(
            "https://www.example.com/page/3",
            SiteParser.listUrl("https://www.example.com", null, null, 3).toString(),
        )
        assertEquals(
            "https://www.example.com/av-media/page/2?orderby=views",
            SiteParser.listUrl("https://www.example.com", "/av-media/", "views", 2).toString(),
        )
        assertEquals(
            "https://www.example.com/?orderby=rand",
            SiteParser.listUrl("https://www.example.com", null, "rand", 1).toString(),
        )
    }

    // ---------- URL 工具 ----------

    @Test
    fun `从文章链接提取文章 ID`() {
        assertEquals(35711L, SiteParser.postIdFromUrl("https://www.example.com/35711"))
        assertEquals(35711L, SiteParser.postIdFromUrl("https://www.example.com/35711.html"))
        assertEquals(35711L, SiteParser.postIdFromUrl("https://www.example.com/35711/"))
        assertNull(SiteParser.postIdFromUrl("https://www.example.com/av-media/page/2"))
        assertNull(SiteParser.postIdFromUrl("https://pan.quark.cn/s/abc"))
    }

    @Test
    fun `头像 alt 兜底提取作者名`() {
        assertEquals("示例读者A", SiteParser.nameFromAvatarAlt("示例读者A的头像-SakuraXy"))
        assertEquals("樱 玄夜", SiteParser.nameFromAvatarAlt("樱 玄夜的作者的 ta 头像-SakuraXy"))
        assertNull(SiteParser.nameFromAvatarAlt(""))
    }
}
