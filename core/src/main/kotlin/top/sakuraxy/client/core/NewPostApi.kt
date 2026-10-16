package top.sakuraxy.client.core

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup

/** 投稿可选分类（[depth] > 0 为子分类，UI 按层级缩进展示） */
data class ComposeCategory(val id: String, val name: String, val depth: Int)

/**
 * /newposts 投稿表单（标题 / 正文 / 分类多选 / 标签）。
 * [fields] 保留站点隐藏字段（posts_id 等）随提交原样转发；
 * 草稿保存成功后站点返回新的 posts_id，后续保存/发布携带同一 id（网页同款行为）。
 */
data class ComposeForm(
    val fields: Map<String, String>,
    val categories: List<ComposeCategory>,
    /** 站点要求的验证方式（实测 slider；null = 无验证） */
    val captchaMode: String?,
)

/** 投稿表单解析（纯函数，离线可测） */
object NewPostParser {

    fun parseComposeForm(html: String): ComposeForm? {
        val doc = Jsoup.parse(html, Site.BASE_URL)
        val form = doc.selectFirst("form:has(textarea[name=post_title])") ?: return null

        val fields = linkedMapOf<String, String>()
        form.select("input[type=hidden][name]").forEach { fields[it.attr("name")] = it.attr("value") }

        // 分类勾选项：label > input[name=category[]]，父级 div.ml20 表示子分类缩进一级
        val categories = form.select("input[name=category[]]").map { input ->
            val name = input.parent()?.selectFirst("span")?.text()?.trim().orEmpty()
            val depth = input.parents().takeWhile { !it.hasClass("mini-scrollbar") }
                .count { it.hasClass("ml20") }
            ComposeCategory(id = input.attr("value"), name = name, depth = depth)
        }

        return ComposeForm(
            fields = fields,
            categories = categories,
            captchaMode = form.selectFirst("input[name=captcha_mode]")?.attr("value")?.ifBlank { null },
        )
    }
}

/**
 * 投稿（/newposts）—— 复刻网页 `page-template` 模块 `.new-posts-submit` 的行为：
 * 表单字段 + action=posts_draft|posts_save + 滑块数据（表单带 captcha_mode=slider）。
 * 成功响应 JSON 含 posts_id（草稿 id）/ url（预览）/ open_url（发布后跳转）等附加字段。
 */
class NewPostApi(
    private val client: OkHttpClient,
    private val baseUrl: String = Site.BASE_URL,
) {
    /** 拉取投稿页并解析表单（未登录时站点跳转登录页，解析返回 null） */
    suspend fun composeForm(): ComposeForm? =
        NewPostParser.parseComposeForm(client.getText("$baseUrl/newposts".toHttpUrl()))

    /**
     * 提交投稿。[asDraft] true = 存草稿（posts_draft），false = 提交审核（posts_save）。
     * 站点要求滑块时 [captcha] 必传，否则返回「环境异常」。
     */
    suspend fun submit(
        form: ComposeForm,
        title: String,
        content: String,
        categoryIds: List<String>,
        tags: String,
        asDraft: Boolean,
        captcha: SliderCaptcha.Solution?,
    ): AjaxResult {
        val body = FormBody.Builder()
        form.fields.forEach { (k, v) -> body.add(k, v) } // posts_id / captcha_mode 等
        body.add("post_title", title)
        body.add("post_content", content)
        body.add("tags", tags)
        categoryIds.forEach { body.add("category[]", it) }
        body.add("action", if (asDraft) "posts_draft" else "posts_save")
        captcha?.let { body.addCaptcha(it) }
        val response = client.postForm(Site.AJAX_URL.toHttpUrl(), body.build())
        return parseAjaxResponse(response.status, response.body)
    }
}
