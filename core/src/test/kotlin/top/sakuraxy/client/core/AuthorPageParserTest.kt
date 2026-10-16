package top.sakuraxy.client.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 作者主页解析（样本为 2026-10-16 真机抓取并脱敏） */
class AuthorPageParserTest {

    @Test
    fun `他人作者页：头部信息与关注私信按钮`() {
        val info = AuthorPageParser.parseAuthorInfo(fixture("author_view_sample.html"))
        assertEquals("示例作者", info.displayName)
        assertEquals(10, info.level)
        assertEquals(1002L, info.userId)
        assertNotNull(info.avatarUrl)
        assertNotNull(info.coverUrl)
        assertFalse(info.isSelf)
        assertFalse(info.following)
        assertTrue(info.canChat)
        assertEquals("n0nce00000", info.postActionNonce)
    }

    @Test
    fun `自己的作者页：没有关注按钮`() {
        val info = AuthorPageParser.parseAuthorInfo(fixture("author_self_sample.html"))
        assertEquals("示例用户", info.displayName)
        assertTrue(info.isSelf)
        assertFalse(info.canChat)
    }

    @Test
    fun `作者页统计：四个标签带数量`() {
        val info = AuthorPageParser.parseAuthorInfo(fixture("author_view_sample.html"))
        val tabs = info.stats.map { it.tab }
        assertTrue(tabs.containsAll(listOf("post", "comment", "favorite", "follow")))
        val post = info.stats.first { it.tab == "post" }
        assertTrue(post.count.isNotBlank())
    }

    @Test
    fun `作者页卡片列表与分页`() {
        val html = fixture("author_view_sample.html")
        val listPage = SiteParser.parseListPage(html)
        assertEquals(3, listPage.cards.size)
        assertNotNull(listPage.cards.first().title)
        assertEquals(1, listPage.currentPage)
        assertEquals(193, listPage.maxPage)
    }

    @Test
    fun `评论标签：内容 时间 回复对象与所属文章`() {
        val comments = AuthorPageParser.parseComments(fixture("author_comment_sample.html"))
        assertEquals(3, comments.size)
        val first = comments[0]
        assertEquals("链接已替换", first.text)
        assertEquals("2026-05-29 22:13:35", first.time)
        assertEquals("4个月前", first.timeText)
        assertEquals("妮可桑-1.0-老司机必备软件", first.postTitle)
        assertTrue(first.url!!.contains("#comment-9096"))
        assertNull(first.replyTo)
        // 第二条带回复对象
        assertEquals("ShangGuan0616", comments[1].replyTo)
    }

    @Test
    fun `粉丝标签：子列表链接与用户卡`() {
        val list = AuthorPageParser.parseFollowList(fixture("author_follow_sample.html"))
        assertTrue(list.followersUrl!!.contains("type=followed"))
        assertTrue(list.followingUrl!!.contains("type=follow"))
        assertEquals(3, list.users.size)
        val first = list.users.first()
        assertEquals("示例读者A", first.name)
        assertNotNull(first.userId)
        assertEquals(10, first.level)
        assertFalse(first.following)
        assertNotNull(first.avatarUrl)
    }
}
