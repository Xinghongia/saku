package top.sakuraxy.client.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 文章详情页解析（样本为 2026-10-16 真机抓取并脱敏）：
 * 登录·未购（35711）/ 登录·已购（26463）/ 匿名（10879）/ 多评论含回复（7634）。
 */
class PostDetailParserTest {

    // ---- 登录 · 未购 ----

    @Test
    fun `未购文章：头部字段与 nonce`() {
        val detail = PostDetailParser.parsePostDetail(fixture("post_paid_sample.html"))
        val h = detail.header
        assertEquals(20000L, h.authorId)
        assertEquals("示例读者A", h.authorName)
        assertEquals(10, h.authorLevel)
        assertEquals(listOf("备受瞩目"), h.medals)
        assertEquals("2026年10月13日 10:13发布", h.dateTitle)
        assertEquals(0, h.commentCount)
        assertEquals(91, h.viewCount)
        assertEquals(0, h.likeCount)
        // post_action_nonce（点赞用）与评论表单 _wpnonce（发评论用）是两个不同的值
        assertEquals("n0nce00003", h.postActionNonce)
        assertEquals("n0nce00000", h.commentFormNonce)
        assertFalse(h.following)
    }

    @Test
    fun `未购文章：面包屑与标签`() {
        val detail = PostDetailParser.parsePostDetail(fixture("post_paid_sample.html"))
        assertTrue(detail.breadcrumb.isNotEmpty())
        assertTrue(detail.breadcrumb.none { it.name == "首页" || it.name == "正文" })
        assertTrue(detail.tags.isNotEmpty())
    }

    @Test
    fun `未购文章：互动与评分状态`() {
        val detail = PostDetailParser.parsePostDetail(fixture("post_paid_sample.html"))
        assertEquals(0, detail.actions.likeCount)
        assertFalse(detail.actions.liked)
        assertEquals(0, detail.actions.favoriteCount)
        assertFalse(detail.actions.favorited)
        assertFalse(detail.actions.favoriteRequiresLogin)

        val rating = detail.rating
        assertNotNull(rating)
        assertEquals("0", rating!!.average)
        assertEquals(0, rating.votes)
        assertEquals(5, rating.distribution.size)
        assertEquals(listOf(5 to 0, 4 to 0, 3 to 0, 2 to 0, 1 to 0), rating.distribution)
        assertEquals("n0nce00006", rating.nonce)
    }

    @Test
    fun `未购文章：付费区积分价 VIP价与收银台`() {
        val detail = PostDetailParser.parsePostDetail(fixture("post_paid_sample.html"))
        val pay = detail.payBox
        assertTrue(pay is PayBox.Unpaid)
        pay as PayBox.Unpaid
        assertEquals(2, pay.orderType)
        assertEquals("2", pay.price)
        assertEquals("积分", pay.unit)
        assertEquals("已售 19", pay.soldText)
        assertTrue(pay.hasCashier)
        assertEquals(2, pay.vipHints.size)
        assertTrue(pay.vipHints.any { it.contains("黄金会员") })
        assertTrue(pay.vipHints.any { it.contains("免费") })
        assertEquals(PayKind.Download, pay.kind)
    }

    @Test
    fun `未购文章：作者卡统计 简介 与 TA的文章`() {
        val detail = PostDetailParser.parsePostDetail(fixture("post_paid_sample.html"))
        val card = detail.authorCard
        assertNotNull(card)
        card!!
        assertEquals(20000L, card.authorId)
        assertEquals("示例读者A", card.name)
        assertEquals("钻石会员", card.vipBadge)
        assertEquals(10, card.level)
        assertEquals(listOf("备受瞩目"), card.medals)
        assertEquals("112", card.postCount)
        assertEquals("144", card.commentCount)
        assertEquals("115", card.followerCount)
        assertEquals("9W+", card.popularity)
        assertTrue(card.desc!!.isNotBlank())
        assertTrue(card.recentPosts.isNotEmpty())
        val first = card.recentPosts.first()
        assertEquals("免责声明", first.title)
        assertEquals("7230", first.views)
        assertEquals(1707L, first.postId)
    }

    @Test
    fun `未购文章：评论区存在但为空`() {
        val detail = PostDetailParser.parsePostDetail(fixture("post_paid_sample.html"))
        val page = detail.commentPage
        assertNotNull(page)
        assertTrue(page!!.comments.isEmpty())
    }

    // ---- 登录 · 已购 ----

    @Test
    fun `已购文章：订单信息与网盘下载按钮`() {
        val detail = PostDetailParser.parsePostDetail(fixture("post_bought_sample.html"))
        val pay = detail.payBox
        assertTrue(pay is PayBox.Paid)
        pay as PayBox.Paid
        assertEquals(2, pay.orderType)
        assertEquals("2600000000000000000", pay.orderNo)
        assertEquals("2026-08-03 13:44:35", pay.payTime)
        assertEquals("1", pay.payAmount)
        assertEquals("已售 16", pay.soldText)
        assertEquals(1, pay.downloads.size)
        assertEquals("夸克网盘", pay.downloads.first().label)
        assertTrue(pay.downloads.first().url.contains("/pay-download/26463"))
        assertEquals(PayKind.Download, pay.kind)
        assertTrue(pay.images.isEmpty())
    }

    // ---- 付费图片型（2026-10-16 实测新形态：36023 已购给二维码图库 / 36018 未购给模糊预览） ----

    @Test
    fun `已购图片资源：图库列表与订单信息`() {
        val detail = PostDetailParser.parsePostDetail(fixture("post_pay_images_sample.html"))
        val pay = detail.payBox
        assertTrue(pay is PayBox.Paid)
        pay as PayBox.Paid
        assertEquals(PayKind.Images, pay.kind)
        assertEquals(5, pay.orderType)
        assertEquals("2600000000000000000", pay.orderNo)
        assertEquals("2026-10-16 16:18:27", pay.payTime)
        assertEquals("1", pay.payAmount)
        assertEquals(2, pay.images.size)
        assertEquals(listOf("1/2", "2/2"), pay.images.map { it.label })
        assertTrue(pay.images[0].url.endsWith("demo-paid-image-1.png"))
        assertTrue(pay.images[1].url.endsWith("demo-paid-image-2.png"))
        // 图片型没有下载按钮（两种资源形态二选一）
        assertTrue(pay.downloads.isEmpty())
    }

    @Test
    fun `未购图片资源：预览图与付费后查看提示`() {
        val detail = PostDetailParser.parsePostDetail(fixture("post_pay_images_unpaid_sample.html"))
        val pay = detail.payBox
        assertTrue(pay is PayBox.Unpaid)
        pay as PayBox.Unpaid
        assertEquals(PayKind.Images, pay.kind)
        assertEquals(5, pay.orderType)
        assertEquals("1", pay.price)
        assertEquals("积分", pay.unit)
        assertTrue(pay.hasCashier)
        assertTrue(pay.teaserImageUrl!!.endsWith("demo-paid-cover.png"))
        assertTrue(pay.hintText!!.contains("请付费后查看"))
    }

    // ---- 匿名 ----

    @Test
    fun `匿名文章：收藏要求登录 点赞可匿名 无付费区`() {
        val detail = PostDetailParser.parsePostDetail(fixture("post_anon_sample.html"))
        assertTrue(detail.actions.favoriteRequiresLogin)
        assertNotNull(detail.actions.likeCount)
        assertEquals(PayBox.Free, detail.payBox)
        assertEquals(20000L, detail.header.authorId)
        assertEquals(10, detail.header.authorLevel)
    }

    // ---- 多评论（含回复与分页） ----

    @Test
    fun `评论列表：首条评论字段与分页地址`() {
        val page = PostDetailParser.parseCommentList(fixture("post_comments_sample.html"))
        assertNotNull(page)
        page!!
        assertEquals(22, page.comments.size)
        assertEquals(1, page.currentPage)
        assertTrue(page.nextPageUrl!!.contains("cpage=2"))

        val first = page.comments.first()
        assertEquals(8674L, first.id)
        assertEquals(20001L, first.authorId)
        assertEquals("示例评论员B", first.authorName)
        assertEquals(1, first.authorLevel)
        assertEquals(0, first.likeCount)
        assertFalse(first.liked)
        assertTrue(first.contentHtml.contains("希望越来越好"))
        assertEquals("2026年04月17日 16:50:52", first.timeTitle)
        assertEquals("5个月前", first.timeText)
        assertNull(first.parentId)
        assertEquals(0, first.depth)
    }

    @Test
    fun `评论列表：回复楼层带父级与层级`() {
        val page = PostDetailParser.parseCommentList(fixture("post_comments_sample.html"))
        val reply = page!!.comments.first { it.id == 4776L }
        assertEquals(4771L, reply.parentId)
        assertEquals(1, reply.depth)
    }

    @Test
    fun `评论列表：点赞 pid 与评论 id 一致（可点赞）`() {
        val page = PostDetailParser.parseCommentList(fixture("post_comments_sample.html"))
        // 每条评论的 likeCount 都解析成功（锚点 data-pid 就是评论 id）
        assertTrue(page!!.comments.all { it.likeCount != null })
    }

    @Test
    fun `评论列表：无评论区返回空`() {
        assertNull(PostDetailParser.parseCommentList("<html><body>无</body></html>"))
    }

    // ---- 多评论文章的互动/评分 ----

    @Test
    fun `多评论文章：点赞数与评分统计`() {
        val detail = PostDetailParser.parsePostDetail(fixture("post_comments_sample.html"))
        assertEquals(32, detail.actions.likeCount)
        assertFalse(detail.actions.liked)
        assertEquals(2, detail.actions.favoriteCount)

        val rating = detail.rating!!
        assertEquals("5", rating.average)
        assertEquals(1, rating.votes)
        assertEquals(listOf(5 to 1, 4 to 0, 3 to 0, 2 to 0, 1 to 0), rating.distribution)
    }
}
