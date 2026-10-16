package top.sakuraxy.client.core

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup

/**
 * 积分购买收银台弹窗（admin-ajax ?action=pay_cashier_modal&id=POSTID 的 HTML 片段）。
 * 提交字段原样转发（post_id/order_type/price_type + action=points_initiate_pay，无 nonce）。
 */
data class CashierInfo(
    val title: String?,
    /** 价格原文（"2"） */
    val priceText: String?,
    /** 「我的积分」原文（"120"） */
    val myPointsText: String?,
    /** order_type 原文（"2"=积分支付） */
    val orderType: Int?,
    /** 表单隐藏字段（含 action），原样转发 */
    val fields: Map<String, String>,
) {
    val action: String? get() = fields["action"]
}

/** 收银台弹窗解析（纯函数，离线可测） */
object CashierParser {

    fun parse(html: String): CashierInfo? {
        val doc = Jsoup.parse(html, Site.BASE_URL)
        val form = doc.selectFirst("form") ?: return null
        val fields = linkedMapOf<String, String>()
        form.select("input[name]").forEach { input ->
            val name = input.attr("name")
            if (name.isNotBlank()) fields[name] = input.attr("value")
        }
        if (fields["post_id"].isNullOrBlank()) return null

        fun rowValue(label: String): String? = doc.select("div.muted-box").firstOrNull { box ->
            box.selectFirst("span.muted-2-color")?.text()?.trim() == label
        }?.selectFirst("span.em14")?.text()?.trim()?.ifBlank { null }

        return CashierInfo(
            title = doc.selectFirst("div.mb10.order-type-2 span:not(.pay-tag)")?.text()?.trim()?.ifBlank { null },
            priceText = rowValue("价格"),
            myPointsText = rowValue("我的积分"),
            orderType = fields["order_type"]?.toIntOrNull(),
            fields = fields,
        )
    }
}

/**
 * 文章级写操作（2026-10-16 实测）：
 * - 点赞/收藏/评论点赞走主题 `action.php` 通用通道 `{type,key,pid,_wpnonce}`，
 *   响应含 action（add/remove，本次操作后状态）与 cuont（新计数）、msg；
 * - 评分走 admin-ajax `zib_star_rating_submit`（nonce 来自页内 zibStarRating 配置）；
 * - 评论提交走 admin-ajax `submit_comment`（_wpnonce 来自评论表单；表单带
 *   captcha_mode=slider → 每次提交都要滑块，与网页 zib_ajax 行为一致）；
 * - 积分购买：收银台字段原样 POST。
 */
class PostApi(
    private val client: OkHttpClient,
    private val baseUrl: String = Site.BASE_URL,
) {
    /** 点赞（锚点 data-action=like；匿名也有 nonce，可匿名点赞） */
    suspend fun like(postId: Long, nonce: String): AjaxResult = action("like", postId, nonce)

    /** 收藏（未登录站点给 signin-loader，调用方应先做登录检查） */
    suspend fun favorite(postId: Long, nonce: String): AjaxResult = action("favorite", postId, nonce)

    /** 评论点赞（pid=评论 id） */
    suspend fun commentLike(commentId: Long, nonce: String): AjaxResult =
        action("comment_like", commentId, nonce)

    private suspend fun action(type: String, pid: Long, nonce: String): AjaxResult {
        val form = FormBody.Builder()
        actionFields(type, pid, nonce).forEach { (k, v) -> form.add(k, v) }
        val response = client.postForm(Site.ACTION_URL.toHttpUrl(), form.build())
        return parseAjaxResponse(response.status, response.body)
    }

    /**
     * 文章评分（1..5）。响应 Ok 时 json 含 average（新均分）与 count（人数）；
     * 重复评分为站点 Fail（「您已经评过分了」，属信息态）。
     */
    suspend fun rate(postId: Long, rating: Int, nonce: String): AjaxResult {
        require(rating in 1..5) { "评分必须为 1..5" }
        val form = FormBody.Builder()
        rateFields(postId, rating, nonce).forEach { (k, v) -> form.add(k, v) }
        val response = client.postForm(Site.AJAX_URL.toHttpUrl(), form.build())
        return parseAjaxResponse(response.status, response.body)
    }

    /** 收银台弹窗（购买前确认用：价格 / 我的积分 / 提交字段） */
    suspend fun cashier(postId: Long): CashierInfo? {
        val url = Site.AJAX_URL.toHttpUrl().newBuilder()
            .addQueryParameter("action", "pay_cashier_modal")
            .addQueryParameter("id", postId.toString())
            .build()
        return CashierParser.parse(client.getText(url))
    }

    /** 积分购买：收银台隐藏字段原样提交（action=points_initiate_pay，无 nonce） */
    suspend fun buyPoints(info: CashierInfo): AjaxResult {
        val url = Site.AJAX_URL.toHttpUrl()
        val form = FormBody.Builder()
        info.fields.forEach { (k, v) -> form.add(k, v) }
        val response = client.postForm(url, form.build())
        return parseAjaxResponse(response.status, response.body)
    }

    /** 提交评论/回复（parent=0 为顶层；captcha 为滑块数据） */
    suspend fun submitComment(
        postId: Long,
        content: String,
        parent: Long,
        nonce: String,
        captcha: SliderCaptcha.Solution?,
    ): AjaxResult {
        val body = FormBody.Builder()
        commentFields(postId, content, parent, nonce).forEach { (k, v) -> body.add(k, v) }
        captcha?.let { body.addCaptcha(it) }
        val response = client.postForm(Site.AJAX_URL.toHttpUrl(), body.build())
        return parseAjaxResponse(response.status, response.body)
    }
}

// ---- 表单字段（纯函数，单测覆盖） ----

/** action.php 通用字段（点赞/收藏/评论点赞/关注同构） */
internal fun actionFields(type: String, pid: Long, nonce: String): Map<String, String> = linkedMapOf(
    "type" to type,
    "key" to type,
    "pid" to pid.toString(),
    "_wpnonce" to nonce,
)

/** 评分提交字段（页内 zibStarRating 的 nonce，不带滑块） */
internal fun rateFields(postId: Long, rating: Int, nonce: String): Map<String, String> = linkedMapOf(
    "action" to "zib_star_rating_submit",
    "nonce" to nonce,
    "post_id" to postId.toString(),
    "rating" to rating.toString(),
)

/**
 * 评论提交字段：与网页 #commentform 序列化一致的子集
 * （comment / comment_post_ID / comment_parent / captcha_mode / _wpnonce + action）。
 */
internal fun commentFields(
    postId: Long,
    content: String,
    parent: Long,
    nonce: String,
): Map<String, String> = linkedMapOf(
    "comment" to content,
    "comment_post_ID" to postId.toString(),
    "comment_parent" to parent.toString(),
    "captcha_mode" to "slider",
    "_wpnonce" to nonce,
    "action" to "submit_comment",
)
