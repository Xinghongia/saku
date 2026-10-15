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
 * 2026-10-15 实测（research/probe/signin.html）：登录表单位于 /user-sign?tab=signin，
 * 提交到 admin-ajax.php，字段含 username / password / remember / captcha_mode / action=user_signin。
 * 滑块验证（captcha_mode=slider）在实测中未强制；客户端不绕过验证，被拦截时如实上报（P4 真机验证）。
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

        /** 站点要求完成滑块/图形验证（客户端暂不支持，如实上报） */
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
     * 注意：站点真实响应结构尚未经过真机验证（见 NEXT.md 开放风险）；
     * 此处按「HTTP 200 + JSON，error=0 为成功」宽松解析，未知结构一律如实上报失败。
     */
    suspend fun signIn(username: String, password: String): SigninResult {
        require(username.isNotBlank() && password.isNotBlank()) { "用户名和密码不能为空" }
        val page = fetchSigninPage()
        val form = FormBody.Builder()
        page.formFields.forEach { (name, value) -> if (name != "action") form.add(name, value) }
        form.add("username", username)
        form.add("password", password)
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

        private fun String.looksLikeCaptcha(): Boolean =
            listOf("验证", "captcha", "滑动", "拼图").any { contains(it, ignoreCase = true) }
    }
}
