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

    // ---------- 注册 / 找回密码（与登录同一页面 /user-sign 的三个 tab 表单） ----------

    /**
     * 登录页上的注册与找回密码表单（2026-10-16 实测自 signin.html）。
     * 每个表单自带 `_wpnonce` 与 `captcha_mode=slider`（每次提交含发验证码都要带真实滑块数据）；
     * 发送验证码按钮的 action 写在按钮的 `form-action` 属性上。
     */
    data class AuthForms(
        /** 注册表单（action=user_signup；字段 name/email/captch/password2/user_agreement） */
        val signup: PanelForm?,
        /** 注册「发送验证码」按钮的 form-action（signup_captcha） */
        val signupSendAction: String?,
        /** 找回密码表单（action=reset_password；字段 email/captch/password2/repassword/repeat） */
        val reset: PanelForm?,
        /** 找回密码「发送验证码」按钮的 form-action（resetpassword_captcha） */
        val resetSendAction: String?,
    )

    /** 纯解析：从登录页 HTML 找注册与找回密码表单（离线可测） */
    fun parseAuthForms(html: String): AuthForms {
        val doc = Jsoup.parse(html, baseUrl)
        fun formByAction(action: String): Pair<PanelForm?, String?> {
            val form = doc.select("form").firstOrNull {
                it.selectFirst("input[name=action][value=$action]") != null
            } ?: return null to null
            val sendAction = form.selectFirst("button[form-action]")
                ?.attr("form-action")?.takeIf { it.isNotBlank() }
            return UserCenterParser.parseForm(form) to sendAction
        }
        val (signup, signupSend) = formByAction("user_signup")
        val (reset, resetSend) = formByAction("reset_password")
        return AuthForms(signup, signupSend, reset, resetSend)
    }

    /** 拉取 /user-sign 页面并解析注册 / 找回密码表单（nonce 现取现用） */
    suspend fun fetchAuthForms(): AuthForms = parseAuthForms(client.getText("$baseUrl/user-sign".toHttpUrl()))

    /** 注册：发送邮箱验证码（网页 .captchsubmit 的等价物；先由用户完成滑块） */
    suspend fun sendSignupCaptcha(
        form: PanelForm,
        name: String,
        email: String,
        captcha: SliderCaptcha.Solution?,
    ): AjaxResult = postAuthForm(
        form,
        mapOf("name" to name, "email" to email),
        "signup_captcha",
        captcha,
    )

    /**
     * 注册提交（action=user_signup）。注册成功不会自动登录（与网页一致：刷新后仍需登录）；
     * 用户的真实资料由用户在界面上亲自填写，客户端不代填、不批量注册。
     */
    suspend fun signUp(
        form: PanelForm,
        name: String,
        email: String,
        code: String,
        password: String,
        captcha: SliderCaptcha.Solution?,
    ): AjaxResult = postAuthForm(
        form,
        mapOf(
            "name" to name,
            "email" to email,
            "captch" to code,
            "password2" to password,
            // 网页勾选协议后复选框随表单提交（value 默认 on）
            "user_agreement" to "on",
        ),
        "user_signup",
        captcha,
    )

    /** 找回密码：发送邮箱验证码 */
    suspend fun sendResetCaptcha(
        form: PanelForm,
        email: String,
        captcha: SliderCaptcha.Solution?,
    ): AjaxResult = postAuthForm(form, mapOf("email" to email), "resetpassword_captcha", captcha)

    /** 找回密码提交（action=reset_password；repeat=1 已在表单隐藏字段里原样转发） */
    suspend fun resetPassword(
        form: PanelForm,
        email: String,
        code: String,
        password: String,
        confirm: String,
        captcha: SliderCaptcha.Solution?,
    ): AjaxResult = postAuthForm(
        form,
        mapOf(
            "email" to email,
            "captch" to code,
            "password2" to password,
            "repassword" to confirm,
        ),
        "reset_password",
        captcha,
    )

    private suspend fun postAuthForm(
        form: PanelForm,
        values: Map<String, String>,
        action: String,
        captcha: SliderCaptcha.Solution?,
    ): AjaxResult {
        val url = "$baseUrl/wp-admin/admin-ajax.php".toHttpUrl()
        val response = client.postForm(url, buildAjaxFormBody(form, values, action, captcha))
        return parseAjaxResponse(response.status, response.body)
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
        captcha?.let { form.addCaptcha(it) }
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
                    // 失效词判据与全站写操作共享（见 ZibAjax.looksLikeCaptcha）
                    if (msg.looksLikeCaptcha()) SigninResult.NeedsCaptcha(msg)
                    else SigninResult.Failure(msg.ifBlank { "登录失败（error=$error）" })
                else -> SigninResult.Failure("无法识别的登录响应：${body.snippet(120)}")
            }
        }
    }
}
