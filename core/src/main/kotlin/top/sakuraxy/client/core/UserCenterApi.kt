package top.sakuraxy.client.core

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * 用户中心（zibll /user/ 页面 + 登录后相关 admin-ajax 动作）。
 *
 * 2026-10-16 实测：用户中心是服务端渲染的整页 HTML（?user_center=xxx 渲染对应面板），
 * 签到/资料保存/上传等写操作走 admin-ajax；全部请求都只复刻网页自身的行为，
 * nonce 一律「现取现用」（先 GET 页面/弹窗，再立即携带提交），不做跨屏缓存。
 */
class UserCenterApi(
    private val client: OkHttpClient,
    private val baseUrl: String = Site.BASE_URL,
) {
    /** 用户中心总览（头像/等级/统计/余额/会员态/签到态/拓展开关态） */
    suspend fun overview(): UserOverview =
        UserCenterParser.parseOverview(client.getText("$baseUrl/user/".toHttpUrl()))

    /** 某个面板的整页 HTML（user_center=data|msg|level|vip|auth|balance|order|rewards|account） */
    suspend fun panel(name: String): String {
        val url = "$baseUrl/user/".toHttpUrl().newBuilder()
            .addQueryParameter("user_center", name)
            .build()
        return client.getText(url)
    }

    /**
     * 登录态校验 + 明文用户资料。
     * 页面上登录后每 1 秒轮询一次的同一个端点（main.min.js 实测），未登录返回 null。
     */
    suspend fun currentUser(): CurrentUser? {
        val form = FormBody.Builder().add("action", "get_current_user").build()
        val response = client.postForm(Site.AJAX_URL.toHttpUrl(), form)
        val json = (parseAjaxResponse(response.status, response.body) as? AjaxResult.Ok)?.json
            ?: return null
        return UserCenterParser.parseCurrentUser(json)
    }

    /** 每日签到（网页 .initiate-checkin 的 form-action；成功响应含 details_link 按钮态） */
    suspend fun checkin(): AjaxResult {
        val form = FormBody.Builder().add("action", "user_checkin").build()
        val response = client.postForm(Site.AJAX_URL.toHttpUrl(), form)
        return parseAjaxResponse(response.status, response.body)
    }

    /**
     * 拓展「屏蔽内容」开关（站点定制：GET ?sxy_uce_r18=1|0）。
     * 返回切换后的总览页解析结果，供调用方确认状态已生效。
     */
    suspend fun setR18(blocked: Boolean): UserOverview {
        val url = "$baseUrl/user/".toHttpUrl().newBuilder()
            .addQueryParameter("sxy_uce_r18", if (blocked) "1" else "0")
            .build()
        return UserCenterParser.parseOverview(client.getText(url))
    }

    // ---- 面板表单（资料编辑 / 消息通知 / 打赏收款） ----

    /** 个人资料编辑表单（#user-tab-data） */
    suspend fun dataForm(): PanelForm? =
        UserCenterParser.parsePanelForm(panel("data"), "user-tab-data")

    /** 消息通知设置表单（#user-tab-msg） */
    suspend fun msgShieldForm(): PanelForm? =
        UserCenterParser.parsePanelForm(panel("msg"), "user-tab-msg")

    /** 打赏收款面板：表单 + 上传字段名（weixin/alipay）+ 已上传收款码 */
    suspend fun rewardsForm(): RewardsPanel? = UserCenterParser.parseRewards(panel("rewards"))

    /**
     * 提交面板表单（网页 zibajax=submit 的等价物）：
     * 隐藏字段原样转发 + 下拉取选中值 + [values] 覆盖用户编辑的字段。
     * [actionOverride] 覆盖表单 action（如「发送验证码」按钮的 form-action）；
     * [captcha] 为滑块验证数据（带 captcha_mode 的表单每次提交都要带，网页同款行为）。
     */
    suspend fun submitForm(
        form: PanelForm,
        values: Map<String, String>,
        actionOverride: String? = null,
        captcha: SliderCaptcha.Solution? = null,
    ): AjaxResult {
        val action = actionOverride ?: form.action ?: return AjaxResult.Fail("表单缺少 action 字段")
        val merged = linkedMapOf<String, String>()
        form.fields.forEach { (k, v) -> if (k != "action") merged[k] = v }
        form.selects.forEach { select -> select.selectedValue?.let { merged[select.name] = it } }
        merged.putAll(values)
        merged["action"] = action
        val body = FormBody.Builder().apply { merged.forEach { (k, v) -> add(k, v) } }
        captcha?.let { body.addCaptcha(it) }
        val response = client.postForm(Site.AJAX_URL.toHttpUrl(), body.build())
        return parseAjaxResponse(response.status, response.body)
    }

    /** 消息通知开关（网页表单三个 checkbox：勾选=on，未勾选=不发送） */
    suspend fun saveMsgShield(
        form: PanelForm,
        posts: Boolean,
        like: Boolean,
        system: Boolean,
    ): AjaxResult {
        val values = buildMap {
            if (posts) put("posts", "on")
            if (like) put("like", "on")
            if (system) put("system", "on")
        }
        val action = form.action ?: return AjaxResult.Fail("表单缺少 action 字段")
        val merged = linkedMapOf<String, String>()
        form.fields.forEach { (k, v) -> if (k != "action") merged[k] = v }
        merged.putAll(values)
        merged["action"] = action
        val body = FormBody.Builder().apply { merged.forEach { (k, v) -> add(k, v) } }.build()
        val response = client.postForm(Site.AJAX_URL.toHttpUrl(), body)
        return parseAjaxResponse(response.status, response.body)
    }

    // ---- 头像 / 封面 / 收款码上传 ----

    /** 修改头像：GET 弹窗取 nonce → multipart 提交（字段名实测为 "file"） */
    suspend fun uploadAvatar(file: UploadFile): AjaxResult = uploadViaModal("user_avatar_set_modal", file)

    /** 修改封面 */
    suspend fun uploadCover(file: UploadFile): AjaxResult = uploadViaModal("user_cover_set_modal", file)

    private suspend fun uploadViaModal(modalAction: String, file: UploadFile): AjaxResult {
        val modalUrl = Site.AJAX_URL.toHttpUrl().newBuilder()
            .addQueryParameter("action", modalAction)
            .build()
        val modal = UserCenterParser.parseUploadForm(client.getText(modalUrl))
        return uploadMultipart(
            fields = modal.fields,
            action = modal.action,
            files = listOf(modal.fileFieldNames.first() to file),
        )
    }

    /** 打赏收款：标题 + 可选（微信/支付宝）收款码 */
    suspend fun saveRewards(
        form: PanelForm,
        uploadForm: UploadForm,
        title: String,
        weixin: UploadFile?,
        alipay: UploadFile?,
    ): AjaxResult {
        val values = linkedMapOf("rewards_title" to title)
        form.fields.forEach { (k, v) -> if (k != "action") values[k] = v }
        val files = buildList {
            weixin?.let { add("weixin" to it) }
            alipay?.let { add("alipay" to it) }
        }
        return uploadMultipart(
            fields = values,
            action = form.action ?: uploadForm.action,
            files = files,
        )
    }

    private suspend fun uploadMultipart(
        fields: Map<String, String>,
        action: String?,
        files: List<Pair<String, UploadFile>>,
    ): AjaxResult {
        val allFields = linkedMapOf<String, String>()
        fields.forEach { (k, v) -> if (k != "action") allFields[k] = v }
        action?.let { allFields["action"] = it }
        val body = buildMultipartBody(allFields, files)
        val response = client.postMultipart(Site.AJAX_URL.toHttpUrl(), body)
        return parseAjaxResponse(response.status, response.body)
    }

    // ---- 各展示面板 ----

    suspend fun level(): LevelInfo = UserCenterParser.parseLevel(panel("level"))

    suspend fun balance(): BalancePanel = UserCenterParser.parseBalance(panel("balance"))

    suspend fun vip(): VipPanel = UserCenterParser.parseVip(panel("vip"))

    suspend fun auth(): AuthInfo = UserCenterParser.parseAuth(panel("auth"))

    suspend fun account(): AccountInfo = UserCenterParser.parseAccount(panel("account"))

    /** 账户安全弹窗（admin-ajax ?tab=email|change_password&action=user_set_modal；两种 tab 内容相同） */
    suspend fun accountModals(tab: String = "email"): AccountModals? {
        val url = Site.AJAX_URL.toHttpUrl().newBuilder()
            .addQueryParameter("tab", tab)
            .addQueryParameter("action", "user_set_modal")
            .build()
        return UserCenterParser.parseAccountModals(client.getText(url))
    }

    /** 我的徽章弹窗（action=user_medal_info_modal&id={用户}；分类 + 全部徽章 + 获得/佩戴状态） */
    suspend fun medalModal(userId: Long): MedalModal? {
        val url = Site.AJAX_URL.toHttpUrl().newBuilder()
            .addQueryParameter("action", "user_medal_info_modal")
            .addQueryParameter("id", userId.toString())
            .build()
        return UserCenterParser.parseMedalModal(client.getText(url))
    }

    /** 我的订单（tab=all|wait-pay） */
    suspend fun orders(tab: String): List<OrderItem> {
        val url = Site.AJAX_URL.toHttpUrl().newBuilder()
            .addQueryParameter("action", "user_pay_order")
            .addQueryParameter("tab", tab)
            .build()
        val form = FormBody.Builder().build()
        val response = client.postForm(url, form)
        return UserCenterParser.parseOrders(response.body)
    }
}
