package top.sakuraxy.client.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.jsoup.Jsoup

/** 用户中心统计项（文章/评论/收藏/粉丝），[tab] 为作者页对应标签 */
data class UserStat(val label: String, val count: String, val tab: String)

/**
 * 用户中心总览（GET /user/ 页面）。
 *
 * 站点为深度定制 zibll（dahai/ sakuraxy 插件），类名不完全标准；
 * 全部字段可空，解析失败返回 null/空列表而不是抛异常（UI 按缺省展示）。
 */
data class UserOverview(
    val displayName: String?,
    val avatarUrl: String?,
    val coverUrl: String?,
    /** 等级数字（LV1 → 1） */
    val level: Int?,
    /** 徽章名称列表（title 属性） */
    val medals: List<String>,
    val stats: List<UserStat>,
    /** 余额显示文本（如 "0"） */
    val balance: String?,
    /** 积分显示文本（如 "116"） */
    val points: String?,
    /** 会员状态原文（"未开通会员" / "黄金会员" 等） */
    val vipStatus: String?,
    val isVip: Boolean,
    /** 今日是否已签到（按钮文案含「已签到」） */
    val checkedIn: Boolean,
    /** 拓展「屏蔽内容」是否处于屏蔽态 */
    val r18Blocked: Boolean,
    /** 页内 window._win.post_action_nonce（点赞/关注等 action.php 写操作用） */
    val postActionNonce: String?,
)

/** 当前登录用户（admin-ajax get_current_user 的明文资料；邮箱在页面 HTML 里被 CDN 混淆，这里取明文） */
data class CurrentUser(
    val id: Long,
    val login: String,
    val displayName: String,
    val email: String,
)

/** 下拉选项 */
data class FormOption(val value: String, val label: String, val selected: Boolean)

/** 下拉字段；[selectedValue] 为当前选中值（站点未标 selected 时浏览器默认取第一项，这里保持一致） */
data class FormSelect(val name: String, val options: List<FormOption>) {
    val selectedValue: String?
        get() = options.firstOrNull { it.selected }?.value ?: options.firstOrNull()?.value
}

/**
 * 用户中心面板里的一个表单（资料编辑 / 消息通知 / 打赏收款 / 上传弹窗共用）。
 * 隐藏字段（_wpnonce/user_id/action 等）原样保留随提交转发，站点新增字段时自动携带。
 */
data class PanelForm(
    val action: String?,
    /** 隐藏与文本字段的当前值（不含复选框/下拉） */
    val fields: Map<String, String>,
    /** 复选框当前勾选态 */
    val checkboxes: Map<String, Boolean>,
    val selects: List<FormSelect>,
) {
    fun selectValue(name: String): String? = selects.firstOrNull { it.name == name }?.selectedValue
}

/** 上传弹窗解析结果：action + 隐藏字段 + 文件字段名（无 data-tag 时为 "file"） */
data class UploadForm(
    val action: String?,
    val fields: Map<String, String>,
    val fileFieldNames: List<String>,
)

/** 我的等级面板 */
data class LevelInfo(
    val level: Int?,
    /** 当前经验（"192"） */
    val exp: String?,
    /** 升级所需总经验（"1000"） */
    val expTotal: String?,
    /** 升级还需（"808"） */
    val need: String?,
    /** 今日获得（"2"） */
    val todayGain: String?,
    /** 各等级经验阈值（LV1..LVn） */
    val thresholds: List<Int>,
)

/** 积分/余额变动记录 */
data class PointsRecord(
    val badge: String?,
    val detail: String?,
    val orderNo: String?,
    val time: String?,
    /** 变化量原文（"+4" / "-1"） */
    val delta: String?,
    /** 变动后余额原文（"积分：116"） */
    val after: String?,
)

/** 我的资产面板 */
data class BalancePanel(
    val balance: String?,
    val pendingIn: String?,
    val points: String?,
    val records: List<PointsRecord>,
    /** 今日是否已签到（积分卡头部链接：签到领积分 / 今日已签到） */
    val checkedIn: Boolean = false,
)

/** 会员档位（黄金/钻石） */
data class VipTier(val level: Int, val name: String)

/** 我的会员面板 */
data class VipPanel(val status: String?, val isVip: Boolean, val tiers: List<VipTier>)

/** 官方认证面板 */
data class AuthInfo(
    val canApply: Boolean,
    val message: String?,
    /** 申请所需最低等级（如 "LV5" 中的 5） */
    val minLevel: Int?,
)

/** 社交账号绑定项 */
data class SocialBind(val name: String, val bound: Boolean, val bindUrl: String?)

/** 账户安全面板 */
data class AccountInfo(
    val socials: List<SocialBind>,
    /** 修改邮箱弹窗入口是否存在 */
    val canEditEmail: Boolean,
    /** 修改密码弹窗入口是否存在 */
    val canEditPassword: Boolean,
)

/**
 * 账户安全弹窗（admin-ajax `?tab=…&action=user_set_modal`）里的四个流程表单。
 * 2026-10-16 实测：两种 tab 返回同一份内容，含「修改邮箱（两步）/ 修改密码 / 重置密码」。
 * 每个流程的提交都要求滑块验证（表单内 captcha_mode=slider，与网页一致）。
 */
data class AccountModals(
    /** 验证步骤提示语里显示的当前邮箱 */
    val currentEmail: String?,
    /** 步骤 1：验证当前邮箱（action=verify_user），[verifySendAction]=verify_user_captcha 发送验证码 */
    val verifyForm: PanelForm?,
    val verifySendAction: String?,
    /** 步骤 2：绑定新邮箱（action=user_bind_email），[bindSendAction]=bind_email_captcha 发送验证码 */
    val bindForm: PanelForm?,
    val bindSendAction: String?,
    /** 修改密码（action=user_change_password） */
    val passwordForm: PanelForm?,
    /** 重置密码（忘记原密码，action=reset_password），[resetSendAction]=resetpassword_captcha */
    val resetForm: PanelForm?,
    val resetSendAction: String?,
)

/** 订单条目 */
data class OrderItem(
    val orderId: String?,
    val sellerName: String?,
    val status: String?,
    val thumbUrl: String?,
    val title: String?,
    val payLabel: String?,
    val date: String?,
    val price: String?,
    val downloadUrl: String?,
)

/** 我的徽章弹窗中的一个徽章（[earned]=用户是否已获得，未获得在网页上灰显） */
data class MedalInfo(val name: String, val iconUrl: String?, val earned: Boolean)

/** 徽章分类（成长 / 荣誉 / 成就 / 限定） */
data class MedalCategory(val name: String, val medals: List<MedalInfo>)

/** 我的徽章弹窗（admin-ajax user_medal_info_modal）：当前佩戴 + 全部分类与获得状态 */
data class MedalModal(
    val avatarUrl: String?,
    /** 当前佩戴的徽章名（头部小图标 title="当前佩戴：X"，可能为空） */
    val currentWorn: String?,
    val categories: List<MedalCategory>,
)

/** 打赏收款面板 */
data class RewardsPanel(
    val form: PanelForm,
    val upload: UploadForm,
    val title: String,
    val weixinQrUrl: String?,
    val alipayQrUrl: String?,
)

object UserCenterParser {

    private val NONCE_RE = Regex("""post_action_nonce\s*:\s*['"]([^'"]+)['"]""")
    private val LEVEL_RE = Regex("""\d+""")

    /** 解析用户中心总览页（纯函数，离线可测） */
    fun parseOverview(html: String): UserOverview {
        val doc = Jsoup.parse(html, Site.BASE_URL)

        val displayName = doc.selectFirst("span.display-name")?.text()?.trim()?.ifBlank { null }

        // 头像/封面：懒加载把真实地址放在 data-src，兜底 src
        val avatarUrl = doc.select("img.avatar").firstNotNullOfOrNull { it.lazyUrl() }
        val coverUrl = doc.selectFirst("img.user-cover")?.lazyUrl()

        val level = doc.selectFirst("img[alt^=等级-]")?.let { img ->
            LEVEL_RE.find(img.attr("title").ifBlank { img.attr("alt") })?.value?.toIntOrNull()
        }
        val medals = doc.select("img.medal-icon")
            .mapNotNull { it.attr("title").ifBlank { null } }
            .distinct()

        val stats = doc.select("a.user-statistics-item").mapNotNull { item ->
            val count = item.selectFirst("div.em14")?.text()?.trim() ?: return@mapNotNull null
            val label = item.selectFirst("div.em09")?.text()?.trim().orEmpty()
            val tab = item.absUrl("href").substringAfter("tab=", "")
            UserStat(label = label, count = count, tab = tab)
        }

        var balance: String? = null
        var points: String? = null
        doc.select("a[href*=user/balance]").forEach { card ->
            val value = card.selectFirst("span.font-bold")?.text()?.trim()
            val text = card.text()
            if (balance == null && text.contains("余额")) balance = value
            if (points == null && text.contains("积分")) points = value
        }

        val vipStatus = doc.select("div.em09").firstOrNull { it.text().trim() == "当前会员状态" }
            ?.parent()?.selectFirst("div.em16")?.text()?.trim()?.ifBlank { null }
        val isVip = vipStatus != null && !vipStatus.contains("未开通")

        // 签到入口两种形态（2026-10-16 实测）：
        // 未签到 = a.initiate-checkin（正文文案「签到」，ed-text 属性才含「已签到」）；
        // 已签到 = a.checkin-details-link（文案「已签到」，点击打开签到明细弹窗）
        val checkinAnchor = doc.selectFirst("a.initiate-checkin")
        val checkinDetails = doc.selectFirst("a.checkin-details-link")
        val checkedIn = checkinDetails != null ||
            checkinAnchor?.text()?.contains("已签到") == true

        val r18Active = doc.selectFirst("a.sxy-uce-r18-opt.sxy-uce-r18-active")
        val r18Blocked = r18Active != null &&
            (r18Active.absUrl("href").endsWith("sxy_uce_r18=1") || r18Active.text().contains("屏蔽"))

        return UserOverview(
            displayName = displayName,
            avatarUrl = avatarUrl,
            coverUrl = coverUrl,
            level = level,
            medals = medals,
            stats = stats,
            balance = balance,
            points = points,
            vipStatus = vipStatus,
            isVip = isVip,
            checkedIn = checkedIn,
            r18Blocked = r18Blocked,
            postActionNonce = NONCE_RE.find(html)?.groupValues?.get(1),
        )
    }

    /** 解析 get_current_user 响应；未登录/异常返回 null（纯函数，离线可测） */
    fun parseCurrentUser(json: JsonObject): CurrentUser? {
        val loggedIn = json["is_logged_in"]?.jsonPrimitive?.booleanOrNull ?: false
        val id = json["id"]?.jsonPrimitive?.longOrNull ?: return null
        if (!loggedIn || id <= 0) return null
        val data = json["user_data"] as? JsonObject
        return CurrentUser(
            id = id,
            login = data?.get("user_login")?.jsonPrimitive?.contentOrNull.orEmpty(),
            displayName = data?.get("display_name")?.jsonPrimitive?.contentOrNull.orEmpty(),
            email = data?.get("user_email")?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }

    /**
     * 解析某个面板里的表单（#sectionId 下的第一个带 input[name=action] 的 form）。
     * 面板未加载（占位骨架）时返回 null。
     */
    fun parsePanelForm(html: String, sectionId: String): PanelForm? {
        val doc = Jsoup.parse(html, Site.BASE_URL)
        val section = doc.selectFirst("#$sectionId") ?: return null
        val form = section.select("form").firstOrNull { it.selectFirst("input[name=action]") != null }
            ?: return null
        return parseForm(form)
    }

    /** 解析上传弹窗（头像/封面/收款码）：action + 隐藏字段 + 文件字段名 */
    fun parseUploadForm(html: String): UploadForm {
        val doc = Jsoup.parse(html, Site.BASE_URL)
        val form = doc.selectFirst("form") ?: return UploadForm(null, emptyMap(), listOf("file"))
        val fields = linkedMapOf<String, String>()
        form.select("input[type=hidden][name]").forEach { fields[it.attr("name")] = it.attr("value") }
        val fileFields = form.select("input[type=file][zibupload]").map { input ->
            input.attr("data-tag").ifBlank { "file" }
        }.ifEmpty { listOf("file") }
        return UploadForm(
            action = fields["action"],
            fields = fields,
            fileFieldNames = fileFields,
        )
    }

    /** 表单元素 → [PanelForm]（账户安全/注册/找回密码等共用；同包内可见） */
    internal fun parseForm(form: org.jsoup.nodes.Element): PanelForm {
        val fields = linkedMapOf<String, String>()
        val checkboxes = linkedMapOf<String, Boolean>()
        form.select("input[name]").forEach { input ->
            when (input.attr("type").lowercase()) {
                "checkbox" -> checkboxes[input.attr("name")] = input.hasAttr("checked")
                "file", "submit", "button" -> Unit
                else -> fields[input.attr("name")] = input.attr("value")
            }
        }
        val selects = form.select("select[name]").map { select ->
            FormSelect(
                name = select.attr("name"),
                options = select.select("option").map { option ->
                    FormOption(
                        value = option.attr("value"),
                        label = option.text().trim(),
                        selected = option.hasAttr("selected"),
                    )
                },
            )
        }
        return PanelForm(
            action = fields["action"],
            fields = fields,
            checkboxes = checkboxes,
            selects = selects,
        )
    }

    /** 我的等级面板 */
    fun parseLevel(html: String): LevelInfo {
        val doc = Jsoup.parse(html, Site.BASE_URL)
        val section = doc.selectFirst("#user-tab-level") ?: doc
        val cardText = section.text()

        val level = section.selectFirst("img[title^=LV]")?.attr("title")
            ?.removePrefix("LV")?.toIntOrNull()
        val expPair = Regex("""(\d+)\s*/\s*(\d+)""").find(cardText)
        val need = Regex("""升级还需\s*(\d+)""").find(cardText)?.groupValues?.get(1)
        val todayGain = Regex("""今日\s*\+(\d+)""").find(cardText)?.groupValues?.get(1)
        val thresholds = section.select("div.pillar-item div.value")
            .mapNotNull { it.text().trim().toIntOrNull() }

        return LevelInfo(
            level = level,
            exp = expPair?.groupValues?.get(1),
            expTotal = expPair?.groupValues?.get(2),
            need = need,
            todayGain = todayGain,
            thresholds = thresholds,
        )
    }

    /** 我的资产面板：余额/积分 + 积分记录（签到奖励、积分支付等条目） */
    fun parseBalance(html: String): BalancePanel {
        val doc = Jsoup.parse(html, Site.BASE_URL)
        val section = doc.selectFirst("#user-tab-balance") ?: doc
        val text = section.text()

        // 资产卡结构解析：卡片（.zib-widget）里首个 div.mb6 的文案是标签（余额/积分），
        // 数值在卡片内的 span.font-bold。
        // 注意不能只靠整页文本正则「积分\s*数字」：签到后签到链接文案由「签到领积分」变为
        // 「今日已签到」，会失去唯一可匹配的位置而抓不到值（2026-10-16 实测 bug：资产页积分显示 0）；
        // 记录条目的「积分：120」则是全角冒号，也不符合该正则。
        val cards = section.select("div.zib-widget")
        fun cardValue(label: String): String? = cards
            .firstOrNull { it.selectFirst("div.mb6")?.text()?.trim() == label }
            ?.selectFirst("span.font-bold")?.text()?.trim()?.ifBlank { null }

        val balance = cardValue("余额")
            ?: Regex("""余额\s*￥?\s*([\d.]+)""").find(text)?.groupValues?.get(1)
        val pendingIn = Regex("""待转入\s*([\d.]+)""").find(text)?.groupValues?.get(1)
        val points = cardValue("积分")
            ?: Regex("""积分\s*([\d.]+)""").find(text)?.groupValues?.get(1)

        val records = section.select("#record_tab_points > div").mapNotNull { row ->
            val delta = row.selectFirst("b")?.text()?.trim() ?: return@mapNotNull null
            val firstCol = row.selectFirst("div.muted-2-color") ?: row
            val badge = firstCol.selectFirst("span.badg")?.text()?.trim()
            val detail = firstCol.selectFirst("div.mb6")?.text()
                ?.replace(badge.orEmpty(), "")?.trim()?.ifBlank { null }
            val time = Regex("""时间：([\d\-: ]+)""").find(firstCol.text())?.groupValues?.get(1)?.trim()
            val orderNo = Regex("""订单号：(\S+)""").find(firstCol.text())?.groupValues?.get(1)
            val after = row.select("div.em09.muted-2-color").map { it.text().trim() }
                .firstOrNull { it.startsWith("积分") }
            PointsRecord(
                badge = badge,
                detail = detail,
                orderNo = orderNo,
                time = time,
                delta = delta,
                after = after,
            )
        }

        return BalancePanel(
            balance = balance,
            pendingIn = pendingIn,
            points = points,
            records = records,
            // 积分卡头部：已签到 = a.checkin-details-link（今日已签到）；未签到 = a.initiate-checkin（签到领积分）
            checkedIn = section.selectFirst("a.checkin-details-link") != null,
        )
    }

    /** 我的会员面板：当前状态 + 黄金/钻石档位 */
    fun parseVip(html: String): VipPanel {
        val doc = Jsoup.parse(html, Site.BASE_URL)
        val section = doc.selectFirst("#user-tab-vip") ?: doc
        val text = section.text()
        val status = Regex("""当前会员状态\s*(\S+)""").find(text)?.groupValues?.get(1)
            ?: Regex("""(未开通会员)""").find(text)?.groupValues?.get(1)
        val tiers = section.select("a.pay-vip[vip-level]").mapNotNull { a ->
            val level = a.attr("vip-level").toIntOrNull() ?: return@mapNotNull null
            val name = a.closest(".pay-vip-info")?.selectFirst("img[title]")?.attr("title")
                ?.ifBlank { null } ?: "VIP$level"
            VipTier(level = level, name = name)
        }.distinctBy { it.level }
        return VipPanel(
            status = status,
            isVip = status != null && !status.contains("未开通"),
            tiers = tiers,
        )
    }

    /** 官方认证面板 */
    fun parseAuth(html: String): AuthInfo {
        val doc = Jsoup.parse(html, Site.BASE_URL)
        val section = doc.selectFirst("#user-tab-auth") ?: doc
        val text = section.text()
        val minLevel = section.selectFirst("img[title^=LV]")?.attr("title")
            ?.removePrefix("LV")?.toIntOrNull()
        val canApply = !text.contains("不能申请")
        val message = Regex("""(由于[^。]*。?)""").find(text)?.groupValues?.get(1)?.trim()
        return AuthInfo(canApply = canApply, message = message, minLevel = minLevel)
    }

    /** 账户安全面板：邮箱/密码入口 + 社交绑定列表 */
    fun parseAccount(html: String): AccountInfo {
        val doc = Jsoup.parse(html, Site.BASE_URL)
        val section = doc.selectFirst("#user-tab-account") ?: doc
        val socials = section.select("a[href*=oauth]").mapNotNull { a ->
            val name = a.closest("div.flex.ac.jsb")
                ?.selectFirst("div.type-logo")
                ?.select("span")
                ?.map { it.text().trim() }
                ?.lastOrNull { it.isNotBlank() }
                ?: return@mapNotNull null
            SocialBind(
                name = name,
                bound = a.text().trim() != "绑定",
                bindUrl = a.absUrl("href"),
            )
        }.distinctBy { it.name }
        val canEditEmail = section.select("a[data-remote*=tab=email]").isNotEmpty()
        val canEditPassword = section.select("a[data-remote*=tab=change_password]").isNotEmpty()
        return AccountInfo(socials = socials, canEditEmail = canEditEmail, canEditPassword = canEditPassword)
    }

    /** 账户安全弹窗：四个流程表单 + 各自的「发送验证码」按钮动作 */
    fun parseAccountModals(html: String): AccountModals? {
        val doc = Jsoup.parse(html, Site.BASE_URL)
        fun paneForm(paneId: String): Pair<PanelForm?, String?> {
            val pane = doc.selectFirst("#$paneId") ?: return null to null
            val form = pane.select("form").firstOrNull { it.selectFirst("input[name=action]") != null }
            val sendAction = pane.selectFirst("button[form-action]")?.attr("form-action")?.ifBlank { null }
            return (form?.let { parseForm(it) }) to sendAction
        }

        val verifyPane = doc.selectFirst("#tab-bind-email-verify")
        if (verifyPane == null && doc.selectFirst("#tab-change-password") == null) return null

        val (verifyForm, verifySend) = paneForm("tab-bind-email-verify")
        val (bindForm, bindSend) = paneForm("tab-bind-email-bind")
        val (passwordForm, _) = paneForm("tab-change-password")
        val (resetForm, resetSend) = paneForm("tab-reset-password")

        return AccountModals(
            currentEmail = verifyPane?.selectFirst("span.badg")?.text()?.trim()?.ifBlank { null },
            verifyForm = verifyForm,
            verifySendAction = verifySend,
            bindForm = bindForm,
            bindSendAction = bindSend,
            passwordForm = passwordForm,
            resetForm = resetForm,
            resetSendAction = resetSend,
        )
    }

    /**
     * 我的徽章弹窗（admin-ajax action=user_medal_info_modal 的 HTML 片段，2026-10-16 实测）：
     * div.medal-cat-box 分类 → div.medal-card（is-has=已获得 / no-has=未获得）+ 徽章名 + 图标。
     */
    fun parseMedalModal(html: String): MedalModal? {
        val doc = Jsoup.parse(html, Site.BASE_URL)
        val boxes = doc.select("div.medal-cat-box")
        if (boxes.isEmpty()) return null
        val categories = boxes.mapNotNull { box ->
            val catName = box.selectFirst("b.medal-cat-name")?.text()?.trim()?.ifBlank { null }
                ?: return@mapNotNull null
            val medals = box.select("div.medal-card").mapNotNull { card ->
                val name = card.selectFirst("div.muted-color")?.text()?.trim()?.ifBlank { null }
                    ?: return@mapNotNull null
                MedalInfo(
                    name = name,
                    iconUrl = card.selectFirst("img.medal-icon")?.lazyUrl(),
                    earned = card.hasClass("is-has"),
                )
            }
            MedalCategory(catName, medals)
        }
        if (categories.isEmpty()) return null
        return MedalModal(
            avatarUrl = doc.select("img.avatar").firstNotNullOfOrNull { it.lazyUrl() },
            // 头部小图标 title="当前佩戴：初出茅庐"；无佩戴徽章时站点不放该图标
            currentWorn = doc.selectFirst("img.medal-icon[title]")
                ?.attr("title")?.substringAfter("佩戴", "")?.trimStart('：', ':', ' ')
                ?.ifBlank { null },
            categories = categories,
        )
    }

    /** 我的订单列表（admin-ajax action=user_pay_order 的 HTML 片段） */
    fun parseOrders(html: String): List<OrderItem> {
        val doc = Jsoup.parse(html, Site.BASE_URL)
        return doc.select(".order-item").map { item ->
            val title = item.selectFirst(".text-ellipsis")?.text()?.trim()
            val date = item.select("div.muted-color.em09, div.em09.muted-color")
                .map { it.text().trim() }
                .firstOrNull { Regex("""\d{4}-\d{2}-\d{2}""").containsMatchIn(it) }
            OrderItem(
                orderId = item.selectFirst("[data-order-id]")?.attr("data-order-id")?.ifBlank { null },
                sellerName = item.selectFirst("a.order-item-author")?.text()?.trim()?.ifBlank { null },
                status = item.selectFirst("div.flex0 > span")?.text()?.trim()?.ifBlank { null },
                thumbUrl = item.selectFirst(".order-thumb img")?.let {
                    it.absUrl("data-src").ifBlank { it.absUrl("src") }.ifBlank { null }
                },
                title = title,
                payLabel = item.selectFirst("span.pay-tag")?.text()?.trim()?.ifBlank { null },
                date = date,
                price = item.selectFirst(".unit-price b")?.text()?.trim()?.ifBlank { null },
                downloadUrl = item.selectFirst(".order-footer a")?.absUrl("href")?.ifBlank { null },
            )
        }
    }

    /** 打赏收款面板：表单 + 上传位 + 已上传的收款码 */
    fun parseRewards(html: String): RewardsPanel? {
        val doc = Jsoup.parse(html, Site.BASE_URL)
        val section = doc.selectFirst("#user-tab-rewards") ?: return null
        val panelForm = parsePanelForm(section.outerHtml(), "user-tab-rewards") ?: return null
        val upload = parseUploadForm(section.outerHtml())
        fun qr(tag: String): String? =
            section.selectFirst(".preview.$tag img")?.let { img ->
                img.absUrl("data-src").ifBlank { img.absUrl("src") }
            }?.takeIf { it.isNotBlank() && !it.contains("upload-add") }
        return RewardsPanel(
            form = panelForm,
            upload = upload,
            title = panelForm.fields["rewards_title"].orEmpty(),
            weixinQrUrl = qr("weixin"),
            alipayQrUrl = qr("alipay"),
        )
    }

    /** 懒加载图片的真实地址：data-src 优先（协议相对地址按站点域名补全），兜底 src */
    private fun org.jsoup.nodes.Element.lazyUrl(): String? =
        absUrl("data-src").ifBlank { null } ?: absUrl("src").ifBlank { null }
}
