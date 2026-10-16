package top.sakuraxy.client.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 文章写操作的表单字段与收银台解析（样本为 2026-10-16 真机抓取并脱敏） */
class PostApiTest {

    // ---- 表单字段（纯函数） ----

    @Test
    fun `action 通道字段：点赞收藏评论点赞同构`() {
        val like = actionFields("like", 35711L, "n0nce00003")
        assertEquals("like", like["type"])
        assertEquals("like", like["key"])
        assertEquals("35711", like["pid"])
        assertEquals("n0nce00003", like["_wpnonce"])

        val fav = actionFields("favorite", 26463L, "n0nce00004")
        assertEquals("favorite", fav["key"])
        assertEquals("26463", fav["pid"])

        val commentLike = actionFields("comment_like", 8674L, "n0nce00000")
        assertEquals("comment_like", commentLike["type"])
        assertEquals("8674", commentLike["pid"])
    }

    @Test
    fun `评分字段：action nonce post_id rating`() {
        val fields = rateFields(7634L, 5, "n0nce00007")
        assertEquals("zib_star_rating_submit", fields["action"])
        assertEquals("n0nce00007", fields["nonce"])
        assertEquals("7634", fields["post_id"])
        assertEquals("5", fields["rating"])
    }

    @Test
    fun `评论字段：必填项齐全且带滑块模式`() {
        val fields = commentFields(7634L, "写得真好", 0L, "n0nce00000")
        assertEquals("submit_comment", fields["action"])
        assertEquals("写得真好", fields["comment"])
        assertEquals("7634", fields["comment_post_ID"])
        assertEquals("0", fields["comment_parent"])
        assertEquals("slider", fields["captcha_mode"])
        assertEquals("n0nce00000", fields["_wpnonce"])

        // 回复楼层：comment_parent 为父评论 id
        val reply = commentFields(7634L, "同感+1", 4771L, "n0nce00000")
        assertEquals("4771", reply["comment_parent"])
    }

    // ---- 收银台 ----

    @Test
    fun `收银台解析：价格 我的积分 与提交字段`() {
        val info = CashierParser.parse(fixture("cashier_sample.html"))
        assertNotNull(info)
        info!!
        assertEquals("2", info.priceText)
        assertEquals("120", info.myPointsText)
        assertEquals(2, info.orderType)
        assertEquals("points_initiate_pay", info.action)
        assertEquals("35711", info.fields["post_id"])
        assertEquals("", info.fields["price_type"])
        // 标题解析到了付费资源名（非「付费资源」标签本身）
        assertTrue(info.title!!.contains("RipX"))
    }

    @Test
    fun `收银台解析：非弹窗内容返回空`() {
        assertNull(CashierParser.parse("<html><body>无表单</body></html>"))
    }
}
