package top.sakuraxy.client.core

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserCenterParserTest {

    private fun overviewFixture() = UserCenterParser.parseOverview(fixture("user_center_overview_sample.html"))

    @Test
    fun `总览解析：身份与图片`() {
        val overview = overviewFixture()
        assertEquals("示例用户", overview.displayName)
        assertEquals("https://www.example.com/uploads/demo-avatar-150x150.jpg", overview.avatarUrl)
        assertEquals("https://www.example.com/uploads/demo-cover.jpg", overview.coverUrl)
        assertEquals(1, overview.level)
        assertEquals(listOf("初出茅庐"), overview.medals)
    }

    @Test
    fun `总览解析：统计行四项带作者页 tab`() {
        val stats = overviewFixture().stats
        assertEquals(4, stats.size)
        assertEquals(listOf("文章", "评论", "收藏", "粉丝"), stats.map { it.label })
        assertEquals(listOf("0", "1", "2", "3"), stats.map { it.count })
        assertEquals(listOf("post", "comment", "favorite", "follow"), stats.map { it.tab })
    }

    @Test
    fun `总览解析：余额积分与会员状态`() {
        val overview = overviewFixture()
        assertEquals("0", overview.balance)
        assertEquals("116", overview.points)
        assertEquals("未开通会员", overview.vipStatus)
        assertFalse(overview.isVip)
    }

    @Test
    fun `总览解析：签到态 拓展开关态 与 post_action_nonce`() {
        val overview = overviewFixture()
        assertFalse(overview.checkedIn)
        assertFalse(overview.r18Blocked)
        assertEquals("0123456789", overview.postActionNonce)
    }

    @Test
    fun `总览解析：已签到态（签到入口变为 checkin-details-link）`() {
        val overview = UserCenterParser.parseOverview(fixture("user_center_overview_checkedin_sample.html"))
        assertTrue(overview.checkedIn)
        // 已签到态其它字段照常
        assertEquals("示例用户", overview.displayName)
        assertEquals("120", overview.points)
    }

    @Test
    fun `总览解析：拓展开关为屏蔽态`() {
        val html = fixture("user_center_overview_sample.html")
            .replace(
                "r18-opt sxy-uce-r18-active\" href=\"https://www.example.com/user/?sxy_uce_r18=0",
                "r18-opt\" href=\"https://www.example.com/user/?sxy_uce_r18=0",
            )
            .replace(
                "r18-opt\" href=\"https://www.example.com/user/?sxy_uce_r18=1",
                "r18-opt sxy-uce-r18-active\" href=\"https://www.example.com/user/?sxy_uce_r18=1",
            )
        val overview = UserCenterParser.parseOverview(html)
        assertTrue(overview.r18Blocked)
    }

    @Test
    fun `总览解析：空页不崩溃且字段为空`() {
        val overview = UserCenterParser.parseOverview("<html><body></body></html>")
        assertNull(overview.displayName)
        assertNull(overview.avatarUrl)
        assertNull(overview.level)
        assertTrue(overview.medals.isEmpty())
        assertTrue(overview.stats.isEmpty())
        assertNull(overview.balance)
        assertFalse(overview.checkedIn)
    }

    @Test
    fun `currentUser 解析：登录响应取明文资料`() {
        val json = SakuJson.parseToJsonElement(fixture("current_user_sample.json")).jsonObject
        val user = UserCenterParser.parseCurrentUser(json)
        assertEquals(10086L, user?.id)
        assertEquals("示例用户", user?.login)
        assertEquals("示例用户", user?.displayName)
        assertEquals("user@example.com", user?.email)
    }

    @Test
    fun `currentUser 解析：未登录或异常返回空`() {
        val notLogged = SakuJson.parseToJsonElement("""{"id":0,"is_logged_in":false,"error":false}""").jsonObject
        assertNull(UserCenterParser.parseCurrentUser(notLogged))
        val empty = SakuJson.parseToJsonElement("""{}""").jsonObject
        assertNull(UserCenterParser.parseCurrentUser(empty))
    }
}
