package top.sakuraxy.client.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 用户中心各面板解析（样本为 2026-10-16 真机抓取并脱敏） */
class UserPanelParserTest {

    // ---- 资料编辑 ----

    @Test
    fun `资料表单解析：文本字段 下拉 与隐藏 nonce`() {
        val form = UserCenterParser.parsePanelForm(fixture("user_center_data_sample.html"), "user-tab-data")
        assertNotNull(form)
        assertEquals("user_edit_datas", form!!.action)
        assertEquals("示例用户", form.fields["name"])
        assertEquals("10086", form.fields["user_id"])
        assertTrue(form.fields.containsKey("_wpnonce"))
        // 下拉：隐私与性别
        val privacy = form.selects.first { it.name == "privacy" }
        assertEquals(3, privacy.options.size)
        assertNotNull(privacy.selectedValue)
        val gender = form.selects.first { it.name == "gender" }
        assertTrue(gender.options.any { it.value == "男" })
    }

    @Test
    fun `资料表单解析：缺失面板返回空`() {
        assertNull(UserCenterParser.parsePanelForm("<html></html>", "user-tab-data"))
    }

    // ---- 消息通知设置 ----

    @Test
    fun `消息通知表单：三个开关默认勾选且带 nonce`() {
        val form = UserCenterParser.parsePanelForm(fixture("user_center_msg_sample.html"), "user-tab-msg")
        assertNotNull(form)
        assertEquals("message_shield", form!!.action)
        assertEquals(setOf("posts", "like", "system"), form.checkboxes.keys)
        assertTrue(form.checkboxes.values.all { it })
        assertTrue(form.fields.containsKey("_wpnonce"))
        assertEquals("10086", form.fields["user_id"])
    }

    // ---- 打赏收款 ----

    @Test
    fun `打赏表单：标题字段与两个上传位`() {
        val html = fixture("user_center_rewards_sample.html")
        val form = UserCenterParser.parsePanelForm(html, "user-tab-rewards")
        assertNotNull(form)
        assertEquals("user_set_rewards", form!!.action)
        assertTrue(form.fields.containsKey("upload_rewards_nonce"))
        val upload = UserCenterParser.parseUploadForm(html)
        assertEquals(listOf("weixin", "alipay"), upload.fileFieldNames)
    }

    // ---- 头像 / 封面弹窗 ----

    @Test
    fun `上传弹窗解析：头像与封面`() {
        val avatar = UserCenterParser.parseUploadForm(fixture("avatar_modal_sample.html"))
        assertEquals("user_upload_avatar", avatar.action)
        assertEquals(listOf("file"), avatar.fileFieldNames)
        assertTrue(avatar.fields.containsKey("upload_avatar_nonce"))
        assertEquals("10086", avatar.fields["user_id"])

        val cover = UserCenterParser.parseUploadForm(fixture("cover_modal_sample.html"))
        assertEquals("user_upload_cover", cover.action)
        assertTrue(cover.fields.containsKey("upload_cover_nonce"))
    }

    // ---- 等级 / 资产 / 会员 / 认证 / 账户安全 ----

    @Test
    fun `等级面板：当前等级 经验进度 与阈值表`() {
        val level = UserCenterParser.parseLevel(fixture("user_center_level_sample.html"))
        assertEquals(1, level.level)
        assertEquals("192", level.exp)
        assertEquals("1000", level.expTotal)
        assertEquals("808", level.need)
        assertEquals("2", level.todayGain)
        assertEquals(listOf(0, 1000, 3000, 6000, 10000, 15000, 21000, 28000, 36000, 45000), level.thresholds)
    }

    @Test
    fun `资产面板：余额 积分 与积分记录`() {
        val balance = UserCenterParser.parseBalance(fixture("user_center_balance_sample.html"))
        assertEquals("0", balance.balance)
        assertEquals("116", balance.points)
        assertTrue(balance.records.isNotEmpty())
        val first = balance.records.first()
        assertEquals("签到奖励", first.badge)
        assertEquals("+4", first.delta)
        assertEquals("2026-10-15 19:10", first.time)
        assertEquals("积分：116", first.after)
        // 积分支付条目带订单号与说明
        val paid = balance.records.first { it.badge == "积分支付" }
        assertNotNull(paid.orderNo)
        assertEquals("付费资源", paid.detail)
        assertTrue(paid.delta!!.startsWith("-"))
    }

    @Test
    fun `会员面板：未开通 + 黄金钻石两档`() {
        val vip = UserCenterParser.parseVip(fixture("user_center_vip_sample.html"))
        assertFalse(vip.isVip)
        assertEquals(listOf(1, 2), vip.tiers.map { it.level })
        assertEquals(listOf("黄金会员", "钻石会员"), vip.tiers.map { it.name })
    }

    @Test
    fun `认证面板：等级不足提示与门槛`() {
        val auth = UserCenterParser.parseAuth(fixture("user_center_auth_sample.html"))
        assertFalse(auth.canApply)
        assertEquals(5, auth.minLevel)
        assertTrue(auth.message!!.contains("等级过低"))
    }

    @Test
    fun `账户安全面板：社交绑定与入口`() {
        val account = UserCenterParser.parseAccount(fixture("user_center_account_sample.html"))
        assertTrue(account.canEditEmail)
        assertTrue(account.canEditPassword)
        assertTrue(account.socials.isNotEmpty())
        val github = account.socials.first { it.name == "GitHub" }
        assertFalse(github.bound)
        assertTrue(github.bindUrl!!.contains("/oauth/github"))
    }

    // ---- 账户安全弹窗（改邮箱两步 / 改密码 / 重置密码） ----

    @Test
    fun `账户安全弹窗：四个流程表单与发送验证码动作`() {
        val modals = UserCenterParser.parseAccountModals(fixture("account_modal_sample.html"))
        assertNotNull(modals)
        modals!!
        assertEquals("demo@example.com", modals.currentEmail)

        // 步骤 1：验证当前邮箱（发码按钮 verify_user_captcha，提交走 verify_user）
        val verify = modals.verifyForm!!
        assertEquals("verify_user", verify.action)
        assertEquals("verify_user_captcha", modals.verifySendAction)
        assertEquals("slider", verify.fields["captcha_mode"])
        assertEquals("email", verify.fields["captcha_type"])
        assertTrue(verify.fields.containsKey("_wpnonce"))

        // 步骤 2：绑定新邮箱（含用户协议勾选，默认已勾）
        val bind = modals.bindForm!!
        assertEquals("user_bind_email", bind.action)
        assertEquals("bind_email_captcha", modals.bindSendAction)
        assertEquals(true, bind.checkboxes["user_agreement"])
        assertTrue(bind.fields.containsKey("_wpnonce"))

        // 修改密码
        val password = modals.passwordForm!!
        assertEquals("user_change_password", password.action)
        assertEquals("slider", password.fields["captcha_mode"])

        // 重置密码（忘记原密码）
        val reset = modals.resetForm!!
        assertEquals("reset_password", reset.action)
        assertEquals("resetpassword_captcha", modals.resetSendAction)
        assertEquals("1", reset.fields["repeat"])
    }

    @Test
    fun `账户安全弹窗：缺失内容返回空`() {
        assertNull(UserCenterParser.parseAccountModals("<html></html>"))
    }

    // ---- 订单 ----

    @Test
    fun `订单列表：卖家 状态 标题 积分价 与下载链接`() {
        val orders = UserCenterParser.parseOrders(fixture("order_list_sample.html"))
        assertTrue(orders.size >= 2)
        val first = orders.first()
        assertEquals("示例作者", first.sellerName)
        assertEquals("已支付", first.status)
        assertEquals("示例付费资源", first.title)
        assertEquals("付费资源", first.payLabel)
        assertEquals("1", first.price)
        assertNotNull(first.orderId)
        assertEquals("2026-08-03 13:44:35", first.date)
        assertTrue(first.downloadUrl!!.startsWith("https://www.example.com/"))
    }
}
