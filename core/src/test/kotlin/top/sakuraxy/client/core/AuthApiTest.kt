package top.sakuraxy.client.core

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthApiTest {

    private lateinit var server: MockWebServer
    private lateinit var auth: AuthApi

    private val baseUrl: String get() = server.url("/").toString().trimEnd('/')

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        auth = AuthApi(createHttpClient(Session()), baseUrl)
    }

    @After
    fun tearDown() {
        server.close()
    }

    // ---------- 登录页解析（离线，样本来自 .probe 真实页面的脱敏副本） ----------

    @Test
    fun `登录页解析出 user_signin 表单与滑块字段`() {
        val page = auth.parseSigninPage(fixture("signin_page_sample.html"))

        assertTrue(page.hasSigninForm)
        assertEquals("slider", page.captchaMode)
        assertEquals("user_signin", page.formFields["action"])
        assertEquals("slider", page.formFields["captcha_mode"])
        assertEquals("forever", page.formFields["remember"])
    }

    @Test
    fun `注册表单的字段不会被误取`() {
        val page = auth.parseSigninPage(fixture("signin_page_sample.html"))

        // _wpnonce 只出现在注册表单里，说明选中的确实是登录表单
        assertFalse(page.formFields.containsKey("_wpnonce"))
    }

    @Test
    fun `没有登录表单的页面如实返回 hasSigninForm=false`() {
        val page = auth.parseSigninPage("<html><body><p>没有表单</p></body></html>")

        assertFalse(page.hasSigninForm)
        assertEquals(0, page.formFields.size)
    }

    // ---------- 注册 / 找回密码解析（样本：登录页三个 tab 表单的脱敏副本） ----------

    @Test
    fun `登录页解析出注册与找回密码表单及发码按钮`() {
        val forms = auth.parseAuthForms(fixture("auth_page_sample.html"))

        assertEquals("user_signup", forms.signup?.action)
        assertEquals("signup_captcha", forms.signupSendAction)
        assertEquals("slider", forms.signup?.fields?.get("captcha_mode"))
        assertEquals("email", forms.signup?.fields?.get("captcha_type"))
        assertTrue(forms.signup!!.fields.containsKey("_wpnonce"))

        assertEquals("reset_password", forms.reset?.action)
        assertEquals("resetpassword_captcha", forms.resetSendAction)
        assertEquals("1", forms.reset?.fields?.get("repeat"))
        assertTrue(forms.reset!!.fields.containsKey("_wpnonce"))
        // 两个表单的 nonce 互不相同（按原值映射的脱敏副本）
        assertTrue(forms.signup!!.fields["_wpnonce"] != forms.reset!!.fields["_wpnonce"])
    }

    @Test
    fun `没有注册与找回密码表单的页面如实返回空`() {
        val html = "<html><body><form><input name='action' value='user_signin'></form></body></html>"
        val forms = auth.parseAuthForms(html)

        assertNull(forms.signup)
        assertNull(forms.signupSendAction)
        assertNull(forms.reset)
        assertNull(forms.resetSendAction)
    }

    @Test
    fun `signUp 按注册表单字段提交（含协议与滑块）`() = runBlocking {
        val forms = auth.parseAuthForms(fixture("auth_page_sample.html"))
        server.enqueue(MockResponse.Builder().code(200).body("""{"error":0,"msg":"注册成功"}""").build())
        val solution = sampleSolution()

        val result = auth.signUp(forms.signup!!, "示例新用户", "demo@example.com", "123456", "secret66", solution)

        assertEquals("注册成功", (result as AjaxResult.Ok).msg)
        val postRequest = server.takeRequest()
        assertEquals("/wp-admin/admin-ajax.php", postRequest.url.encodedPath)
        val fields = decodedFields(postRequest)
        assertEquals("user_signup", fields["action"])
        assertEquals("示例新用户", fields["name"])
        assertEquals("demo@example.com", fields["email"])
        assertEquals("123456", fields["captch"])
        assertEquals("secret66", fields["password2"])
        assertEquals("on", fields["user_agreement"])
        assertEquals("slider", fields["captcha_mode"])
        assertEquals("34oibiwrp92griym26", fields["captcha[ticket]"])
        assertTrue(fields["_wpnonce"]!!.isNotBlank())
    }

    @Test
    fun `sendSignupCaptcha 与 resetPassword 走各自的 action 与字段`() = runBlocking {
        val forms = auth.parseAuthForms(fixture("auth_page_sample.html"))

        // 发送注册验证码
        server.enqueue(MockResponse.Builder().code(200).body("""{"error":0,"msg":"验证码已发送","remaining_time":60}""").build())
        auth.sendSignupCaptcha(forms.signup!!, "示例新用户", "demo@example.com", sampleSolution())
        val send = decodedFields(server.takeRequest())
        assertEquals("signup_captcha", send["action"])
        assertEquals("demo@example.com", send["email"])
        assertEquals("示例新用户", send["name"])

        // 找回密码提交：repeat=1 来自表单隐藏字段原样转发
        server.enqueue(MockResponse.Builder().code(200).body("""{"error":0,"msg":"密码重置成功"}""").build())
        val result = auth.resetPassword(forms.reset!!, "demo@example.com", "654321", "newpass6", "newpass6", null)
        assertEquals("密码重置成功", (result as AjaxResult.Ok).msg)
        val reset = decodedFields(server.takeRequest())
        assertEquals("reset_password", reset["action"])
        assertEquals("demo@example.com", reset["email"])
        assertEquals("654321", reset["captch"])
        assertEquals("newpass6", reset["password2"])
        assertEquals("newpass6", reset["repassword"])
        assertEquals("1", reset["repeat"])
    }

    @Test
    fun `注册失败时如实返回站点原因`() = runBlocking {
        val forms = auth.parseAuthForms(fixture("auth_page_sample.html"))
        server.enqueue(MockResponse.Builder().code(200).body("""{"error":1,"msg":"该邮箱已被注册"}""").build())

        val result = auth.signUp(forms.signup!!, "示例新用户", "demo@example.com", "123456", "secret66", null)

        assertEquals(AjaxResult.Fail("该邮箱已被注册"), result)
    }

    private fun sampleSolution() = SliderCaptcha.Solution(
        ticket = "34oibiwrp92griym26",
        randstr = "276b5b8a09bab55a317668d325",
        spliced = true,
        trail = listOf(0, 0, -1, 0, 1),
        check = "35854c6d05a74f594037aa816a3c0a0d",
    )

    private fun decodedFields(request: mockwebserver3.RecordedRequest): Map<String, String> =
        java.net.URLDecoder.decode(request.body!!.utf8(), "UTF-8")
            .split("&")
            .associate { it.substringBefore("=") to it.substringAfter("=") }

    // ---------- 登录请求与响应解析（本地 mock 服务器，不外联） ----------

    @Test
    fun `signIn 先取登录页再按实测字段提交表单`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body(fixture("signin_page_sample.html")).build())
        server.enqueue(MockResponse.Builder().code(200).body("""{"error":0,"msg":"登录成功","data":{"id":1}}""").build())

        val result = auth.signIn("tester", "secret")

        assertEquals(AuthApi.SigninResult.Success("登录成功"), result)

        val pageRequest = server.takeRequest()
        assertEquals("/user-sign", pageRequest.url.encodedPath)
        assertEquals("signin", pageRequest.url.queryParameter("tab"))

        val postRequest = server.takeRequest()
        assertEquals("POST", postRequest.method)
        assertEquals("/wp-admin/admin-ajax.php", postRequest.url.encodedPath)
        val fields = postRequest.body!!.utf8()
            .split("&")
            .associate { it.substringBefore("=") to it.substringAfter("=") }
        assertEquals("user_signin", fields["action"])
        assertEquals("tester", fields["username"])
        assertEquals("secret", fields["password"])
        assertEquals("slider", fields["captcha_mode"])
        assertEquals("forever", fields["remember"])
    }

    @Test
    fun `站点要求滑块验证时如实返回 NeedsCaptcha`() {
        val result = AuthApi.parseSigninResponse(200, """{"error":1,"msg":"请先完成滑块验证"}""")

        assertTrue(result is AuthApi.SigninResult.NeedsCaptcha)
    }

    @Test
    fun `环境异常类响应（实测原文）映射为 NeedsCaptcha`() {
        // 2026-10-15 实测：缺少滑块数据 / 拖动未对齐时服务端的真实响应
        val missing = AuthApi.parseSigninResponse(200, """{"error":1,"msg":"环境异常，请刷新后重新提交"}""")
        val misaligned = AuthApi.parseSigninResponse(200, """{"error":1,"msg":"环境异常，人机验证失败"}""")

        assertTrue(missing is AuthApi.SigninResult.NeedsCaptcha)
        assertTrue(misaligned is AuthApi.SigninResult.NeedsCaptcha)
    }

    @Test
    fun `signIn 带滑块数据时按 jQuery 嵌套格式提交 captcha 字段族`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body(fixture("signin_page_sample.html")).build())
        server.enqueue(MockResponse.Builder().code(200).body("""{"error":0,"reload":1,"msg":"成功登录，页面跳转中"}""").build())
        val solution = SliderCaptcha.Solution(
            ticket = "34oibiwrp92griym26",
            randstr = "276b5b8a09bab55a317668d325",
            spliced = true,
            trail = listOf(0, 0, -1, 0, 1),
            check = "35854c6d05a74f594037aa816a3c0a0d",
        )

        val result = auth.signIn("tester", "secret", solution)

        assertEquals(AuthApi.SigninResult.Success("成功登录，页面跳转中"), result)

        server.takeRequest() // 登录页
        val postRequest = server.takeRequest()
        val fields = java.net.URLDecoder.decode(postRequest.body!!.utf8(), "UTF-8")
            .split("&")
            .associate { it.substringBefore("=") to it.substringAfter("=") }
        assertEquals("34oibiwrp92griym26", fields["captcha[ticket]"])
        assertEquals("276b5b8a09bab55a317668d325", fields["captcha[randstr]"])
        assertEquals("true", fields["captcha[spliced]"])
        assertEquals("[0,0,-1,0,1]", fields["captcha[trail]"])
        assertEquals("35854c6d05a74f594037aa816a3c0a0d", fields["captcha[check]"])
    }

    @Test
    fun `登录被拒时返回站点给出的原因`() {
        val result = AuthApi.parseSigninResponse(200, """{"error":1,"msg":"密码错误"}""")

        assertEquals(AuthApi.SigninResult.Failure("密码错误"), result)
    }

    @Test
    fun `非 JSON 响应如实上报为失败`() {
        val result = AuthApi.parseSigninResponse(200, "<html>安全验证页面</html>")

        assertTrue(result is AuthApi.SigninResult.Failure)
        assertTrue((result as AuthApi.SigninResult.Failure).message.contains("不是 JSON"))
    }

    @Test
    fun `HTTP 错误如实上报`() {
        val result = AuthApi.parseSigninResponse(503, "unavailable")

        assertEquals(AuthApi.SigninResult.Failure("服务器返回 HTTP 503"), result)
    }

    @Test
    fun `无法识别的 JSON 结构不会被当成登录成功`() {
        val result = AuthApi.parseSigninResponse(200, """{"something":"else"}""")

        assertTrue(result is AuthApi.SigninResult.Failure)
    }
}
