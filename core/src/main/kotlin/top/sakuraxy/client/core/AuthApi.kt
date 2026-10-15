package top.sakuraxy.client.core

import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup

/**
 * 登录（zibll 主题的 admin-ajax 流程）。
 *
 * 2026-10-15 实测（research/probe/signin.html + curl 全流程复现）：
 * 登录表单位于 /user-sign?tab=signin，提交到 admin-ajax.php，
 * 字段含 username / password / remember / captcha_mode / action=user_signin。
 *
 * 第二次实测确认站点启用了滑块验证（captcha_mode=slider）：
 * 不带滑块数据提交会得到「环境异常，请刷新后重新提交」，拖动偏离缺口会被拒
 * 「环境异常，人机验证失败」；带完整真实拖动数据可正常登录。
 * 滑块数据由 [SliderCaptcha] 依网页算法生成，数据全部来自用户真实拖动，不伪造。
 */
class AuthApi(
    private val client: OkHttpClient,
    private val baseUrl: String = Site.BASE_URL,
) {
    data class SigninPage(
        val hasSigninForm: Boolean,
        val captchaMode: String?,
        /** 表单里的隐藏字段与已勾选复选框（原样转发，站点新增字段时自动携带） */
        val formFields: Map<String, String>,
    )

    sealed interface SigninResult {
        data class Success(val message: String) : SigninResult

        /** 站点要求完成滑块验证（数据缺失/过期/拖动未对齐）；用 [SliderCaptcha] 重新走一遍 */
        data class NeedsCaptcha(val message: String) : SigninResult

        data class Failure(val message: String) : SigninResult
    }

    /** 纯解析：从登录页 HTML 找 user_signin 表单（离线可测） */
    fun parseSigninPage(html: String): SigninPage {
        val doc = Jsoup.parse(html)
        val form = doc.select("form").firstOrNull { f ->
            f.selectFirst("input[name=action][value=user_signin]") != null
        } ?: return SigninPage(hasSigninForm = false, captchaMode = null, formFields = emptyMap())
        // 隐藏字段 + 已勾选的复选框（如 remember），与原页面表单序列化行为一致
        val fields = linkedMapOf<String, String>()
        form.select("input[type=hidden][name]").forEach { fields[it.attr("name")] = it.attr("value") }
        form.select("input[type=checkbox][name]").forEach {
            if (it.hasAttr("checked")) fields[it.attr("name")] = it.attr("value")
        }
        return SigninPage(hasSigninForm = true, captchaMode = fields["captcha_mode"], formFields = fields)
    }

    suspend fun fetchSigninPage(): SigninPage {
        val url = "$baseUrl/user-sign".toHttpUrl().newBuilder()
            .addQueryParameter("tab", "signin")
            .build()
        return parseSigninPage(client.getText(url))
    }

    /**
     * 模拟站点登录表单提交。成功后 cookie 由 Session 自动保存。
     *
     * @param captcha 滑块验证数据（[SliderCaptcha.solve] 生成）；站点要求滑块时必须提供，
     *   否则服务端返回「环境异常」。对应网页把 window.captcha 对象并入表单数据的行为。
     */
    suspend fun signIn(
        username: String,
        password: String,
        captcha: SliderCaptcha.Solution? = null,
    ): SigninResult {
        require(username.isNotBlank() && password.isNotBlank()) { "用户名和密码不能为空" }
        val page = fetchSigninPage()
        val form = FormBody.Builder()
        page.formFields.forEach { (name, value) -> if (name != "action") form.add(name, value) }
        form.add("username", username)
        form.add("password", password)
        captcha?.let {
            // jQuery 序列化嵌套对象：captcha[ticket]=… 与网页提交格式一致
            form.add("captcha[ticket]", it.ticket)
            form.add("captcha[randstr]", it.randstr)
            form.add("captcha[spliced]", it.spliced.toString())
            // 与 JS JSON.stringify([1,2]) 相同的紧凑数组格式
            form.add("captcha[trail]", it.trail.joinToString(",", "[", "]"))
            form.add("captcha[check]", it.check)
        }
        form.add("action", "user_signin")
        val url = "$baseUrl/wp-admin/admin-ajax.php".toHttpUrl()
        val response = client.postForm(url, form.build())
        return parseSigninResponse(response.status, response.body)
    }

    companion object {
        internal fun parseSigninResponse(status: Int, body: String): SigninResult {
            if (status !in 200..299) return SigninResult.Failure("服务器返回 HTTP $status")
            val json = try {
                SakuJson.parseToJsonElement(body).jsonObject
            } catch (_: Exception) {
                return SigninResult.Failure("响应不是 JSON（可能被安全验证拦截）：${body.snippet(120)}")
            }
            val error = json["error"]?.jsonPrimitive?.intOrNull
            val msg = json["msg"]?.jsonPrimitive?.contentOrNull
                ?: json["message"]?.jsonPrimitive?.contentOrNull
                ?: ""
            return when {
                error == 0 -> SigninResult.Success(msg.ifBlank { "登录成功" })
                error != null ->
                    if (msg.looksLikeCaptcha()) SigninResult.NeedsCaptcha(msg)
                    else SigninResult.Failure(msg.ifBlank { "登录失败（error=$error）" })
                else -> SigninResult.Failure("无法识别的登录响应：${body.snippet(120)}")
            }
        }

        /**
         * 服务端把「环境异常，请刷新后重新提交」（缺滑块数据）与
         * 「环境异常，人机验证失败」（拖动未对齐）都归入需要重新走滑块的类别。
         */
        private fun String.looksLikeCaptcha(): Boolean =
            listOf("验证", "captcha", "滑动", "拼图", "环境异常").any { contains(it, ignoreCase = true) }
    }
}
