package top.sakuraxy.client.core

import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 滑块验证的离线单测。
 * 算法依据 2026-10-15 二次实测的 zibll 前端代码（slidercaptcha.min.js / main.min.js），
 * 结构断言用「模拟服务端解析」的方式：编码输出必须能被按同样规则解回原值。
 */
class SliderCaptchaTest {

    private lateinit var server: MockWebServer
    private lateinit var client: okhttp3.OkHttpClient

    private val baseUrl: String get() = server.url("/").toString().trimEnd('/')

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = createHttpClient(Session())
    }

    @After
    fun tearDown() {
        server.close()
    }

    // ---------- 编码结构（模拟服务端视角解析回原值） ----------

    /** 模拟服务端：`<e><letters(e)><value><letters(i)><i>` → value */
    private fun decodeEmbeddedValue(encoded: String): Int {
        val e = encoded.substring(0, 2).toInt()
        val i = encoded.substring(encoded.length - 2).toInt()
        val middle = encoded.substring(2, encoded.length - 2)
        assertEquals("前段字母数应为 $e", e, middle.takeWhile { it.isLetter() }.length)
        assertEquals("后段字母数应为 $i", i, middle.takeLastWhile { it.isLetter() }.length)
        return middle.trim { it.isLetter() }.toInt()
    }

    @Test
    fun `randstr 内嵌的题目数字可以被解析回原值`() {
        val random = Random(42)
        repeat(200) {
            val x = random.nextInt(SliderCaptcha.MIN_X, SliderCaptcha.MAX_X + 1)
            assertEquals(x, decodeEmbeddedValue(SliderCaptcha.encodeRandstr(x, random)))
        }
    }

    @Test
    fun `ticket 内嵌的拖动距离可以被解析回原值`() {
        val random = Random(7)
        repeat(200) {
            val distance = random.nextInt(0, SliderCaptcha.MAX_DRAG + 1)
            assertEquals(distance, decodeEmbeddedValue(SliderCaptcha.encodeTicket(distance, random)))
        }
    }

    @Test
    fun `randstr 字段保留 rand_str 的对应片段且前后包裹两个数字`() {
        val random = Random(1)
        val randStr = "f676b5b8a09bab55a317668d3dce9f78"
        repeat(100) {
            val field = SliderCaptcha.encodeRandstrField(randStr, random)
            val o = field.substring(0, 1).toInt()
            val r = field.substring(field.length - 2).toInt()
            assertTrue(o in 1..9)
            assertTrue(r in 15..25)
            assertEquals("字段中段应是 rand_str 的 [$o,$r) 子串", randStr.substring(o, r), field.substring(1, field.length - 2))
        }
    }

    // ---------- 坐标数学（与网页一致） ----------

    @Test
    fun `拖动量换算为滑块位置 d = 220 div 240 a`() {
        assertEquals(0, SliderCaptcha.dragToDistance(0f))
        assertEquals(110, SliderCaptcha.dragToDistance(120f))
        assertEquals(220, SliderCaptcha.dragToDistance(240f))
    }

    @Test
    fun `拖动量超出范围时被夹紧到 240`() {
        assertEquals(220, SliderCaptcha.dragToDistance(999f))
        assertEquals(0, SliderCaptcha.dragToDistance(-50f))
    }

    @Test
    fun `对齐判断容差为 8 像素以内`() {
        assertTrue(SliderCaptcha.isAligned(100, 100))
        assertTrue(SliderCaptcha.isAligned(107, 100))
        assertTrue(SliderCaptcha.isAligned(93, 100))
        assertFalse(SliderCaptcha.isAligned(108, 100))
        assertFalse(SliderCaptcha.isAligned(92, 100))
    }

    @Test
    fun `出题范围与网页一致`() {
        val random = Random(3)
        repeat(300) {
            val c = SliderCaptcha.newLocalChallenge(random)
            assertTrue("x=${c.x}", c.x in SliderCaptcha.MIN_X..SliderCaptcha.MAX_X)
            assertTrue("y=${c.y}", c.y in SliderCaptcha.MIN_Y..SliderCaptcha.MAX_Y)
            assertTrue("img=${c.imageIndex}", c.imageIndex in 0 until SliderCaptcha.IMAGE_COUNT)
        }
    }

    @Test
    fun `solve 依据拖动结果生成提交数据`() {
        val challenge = SliderCaptcha.Challenge(
            x = 150, y = 60, imageIndex = 3,
            token = "32xx", randStr = "0123456789abcdef0123456789abcdef", check = "check123",
        )
        val solution = SliderCaptcha.solve(
            challenge,
            SliderCaptcha.DragResult(distance = 155, trail = listOf(0, 1, 1, 0, -1)),
            Random(9),
        )

        assertTrue("|155-150| = 5 < 8 应判对齐", solution.spliced)
        assertEquals("check123", solution.check)
        assertEquals(listOf(0, 1, 1, 0, -1), solution.trail)
        assertEquals(155, decodeEmbeddedValue(solution.ticket))
        // randstr 字段：首数字 + rand_str 子串 + 尾数字
        val o = solution.randstr.substring(0, 1).toInt()
        val r = solution.randstr.substring(solution.randstr.length - 2).toInt()
        assertEquals(
            challenge.randStr.substring(o, r),
            solution.randstr.substring(1, solution.randstr.length - 2),
        )
    }

    @Test
    fun `拖动未对齐时 spliced 为 false`() {
        val challenge = SliderCaptcha.Challenge(150, 60, 0, "t", "0123456789abcdef0123456789abcdef", "c")
        val solution = SliderCaptcha.solve(challenge, SliderCaptcha.DragResult(200, listOf(0)), Random(1))

        assertFalse(solution.spliced)
    }

    // ---------- 联网换取凭据（本地 mock，不外联） ----------

    @Test
    fun `requestChallenge 携带内嵌题目数字的 randstr 并解析凭据`() = runBlocking {
        // 2026-10-15 实测响应结构（脱敏示例值）
        server.enqueue(
            MockResponse.Builder().code(200).body(
                """{"token":"32ssQhxcSGLXk1yPQVbrDKTxqyP0aWGYTj92GD6lMf0RsVZwwZFuPqrw4k0Xu7fYytCQTvTAqJf39","rand_str":"f676b5b8a09bab55a317668d3dce9f78","check":"35854c6d05a74f594037aa816a3c0a0d","time":1792072825}"""
            ).build()
        )
        val local = SliderCaptcha.Challenge(x = 92, y = 60, imageIndex = 5, token = "", randStr = "", check = "")

        val challenge = SliderCaptcha.requestChallenge(client, local, Random(11), baseUrl)

        assertEquals("32ssQhxcSGLXk1yPQVbrDKTxqyP0aWGYTj92GD6lMf0RsVZwwZFuPqrw4k0Xu7fYytCQTvTAqJf39", challenge.token)
        assertEquals("f676b5b8a09bab55a317668d3dce9f78", challenge.randStr)
        assertEquals("35854c6d05a74f594037aa816a3c0a0d", challenge.check)
        assertEquals(92, challenge.x)
        assertEquals(5, challenge.imageIndex)

        val request = server.takeRequest()
        assertEquals("/wp-content/themes/zibll/action/captcha.php", request.url.encodedPath)
        assertEquals("slider", request.url.queryParameter("type"))
        val sentRandstr = request.url.queryParameter("randstr")!!
        assertEquals("randstr 内嵌的题目数字应为 92", 92, decodeEmbeddedValue(sentRandstr))
    }

    @Test
    fun `凭据字段缺失时如实报错`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body("""{"token":"x"}""").build())
        val local = SliderCaptcha.Challenge(92, 60, 0, "", "", "")

        val error = runCatching { SliderCaptcha.requestChallenge(client, local, Random(1), baseUrl) }
            .exceptionOrNull()

        assertTrue("应抛错而不是编造凭据", error is SakuHttpException)
        assertTrue(error!!.message!!.contains("rand_str"))
    }

    @Test
    fun `非 JSON 响应如实报错`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body("<html>waf</html>").build())
        val local = SliderCaptcha.Challenge(92, 60, 0, "", "", "")

        val error = runCatching { SliderCaptcha.requestChallenge(client, local, Random(1), baseUrl) }
            .exceptionOrNull()

        assertTrue(error is SakuHttpException)
        assertTrue(error!!.message!!.contains("不是 JSON"))
    }
}
