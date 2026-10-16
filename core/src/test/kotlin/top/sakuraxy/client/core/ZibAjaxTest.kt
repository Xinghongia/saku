package top.sakuraxy.client.core

import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZibAjaxTest {

    @Test
    fun `error 为 0 时解析为成功并保留附加字段`() {
        val result = parseAjaxResponse(
            200,
            """{"error":0,"msg":"签到成功","details_link":"<a>已签到</a>"}""",
        )
        assertTrue(result is AjaxResult.Ok)
        val ok = result as AjaxResult.Ok
        assertEquals("签到成功", ok.msg)
        assertEquals("<a>已签到</a>", ok.json["details_link"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `error 为布尔 false 时也视为成功`() {
        val result = parseAjaxResponse(200, """{"error":false,"is_logged_in":true}""")
        assertTrue(result is AjaxResult.Ok)
    }

    @Test
    fun `error 非 0 时解析为失败并带站点原文`() {
        val result = parseAjaxResponse(200, """{"error":1,"msg":"请先登录"}""")
        assertEquals(AjaxResult.Fail("请先登录"), result)
    }

    @Test
    fun `验证类文案归为需要滑块`() {
        val result = parseAjaxResponse(200, """{"error":1,"msg":"环境异常，请刷新后重新提交"}""")
        assertTrue(result is AjaxResult.NeedsCaptcha)
    }

    @Test
    fun `非 JSON 响应归为 NotJson 并保留片段`() {
        val result = parseAjaxResponse(200, "<html><body>403 Forbidden</body></html>")
        assertTrue(result is AjaxResult.NotJson)
        assertTrue((result as AjaxResult.NotJson).snippet.contains("403"))
    }

    @Test
    fun `缺少 error 字段时视为无法识别`() {
        val result = parseAjaxResponse(200, """{"msg":"hello"}""")
        assertTrue(result is AjaxResult.Fail)
    }

    @Test
    fun `HTTP 非 2xx 直接失败`() {
        val result = parseAjaxResponse(500, """{"error":0}""")
        assertEquals(AjaxResult.Fail("服务器返回 HTTP 500"), result)
    }

    @Test
    fun `滑块数据并入表单：captcha 字段与网页序列化一致`() {
        val solution = SliderCaptcha.Solution(
            ticket = "t1",
            randstr = "r1",
            spliced = true,
            trail = listOf(1, -2),
            check = "c1",
        )
        val body = FormBody.Builder()
            .add("action", "user_bind_email")
            .addCaptcha(solution)
            .build()
        val fields = (0 until body.size).associate { body.name(it) to body.value(it) }
        assertEquals("user_bind_email", fields["action"])
        assertEquals("t1", fields["captcha[ticket]"])
        assertEquals("r1", fields["captcha[randstr]"])
        assertEquals("true", fields["captcha[spliced]"])
        assertEquals("[1,-2]", fields["captcha[trail]"])
        assertEquals("c1", fields["captcha[check]"])
    }
}
