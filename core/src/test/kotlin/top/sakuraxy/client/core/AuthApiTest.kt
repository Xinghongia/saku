package top.sakuraxy.client.core

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
