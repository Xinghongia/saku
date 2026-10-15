package top.sakuraxy.client.core

import java.util.Base64
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GolinkTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    // ---------- 正文提取（离线，样本来自 .probe 真实响应的脱敏副本） ----------

    @Test
    fun `从文章正文提取下载按钮，保持顺序且过滤无参数链接`() {
        val post = SakuJson.decodeFromString<WpPost>(fixture("wp_post_sample.json"))
        val buttons = Golink.extractButtons(post.content!!.rendered)

        assertEquals(2, buttons.size)
        assertEquals("网盘下载", buttons[0].label)
        assertEquals("备用源", buttons[1].label)
        // 第三个 <a> 没有 golink 参数，不应被提取
        assertTrue(buttons.none { it.label.contains("相关链接") })
    }

    @Test
    fun `相对 href 会被解析为绝对地址`() {
        val post = SakuJson.decodeFromString<WpPost>(fixture("wp_post_sample.json"))
        val buttons = Golink.extractButtons(post.content!!.rendered, baseUrl = "https://www.example.com")

        // 第二个按钮在样本里是相对链接 /?golink=...
        assertEquals("备用源", buttons[1].label)
        assertTrue(buttons[1].href.startsWith("https://www.example.com/?golink="))
    }

    @Test
    fun `base64 目标与 nonce 均能正确解析`() {
        val post = SakuJson.decodeFromString<WpPost>(fixture("wp_post_sample.json"))
        val buttons = Golink.extractButtons(post.content!!.rendered)

        assertEquals("https://pan.example.com/s/TEST1234?pwd=TEST", buttons[0].decodedTarget)
        assertEquals("https://play.example.com/store/apps/details?id=test.app", buttons[1].decodedTarget)
        assertEquals("deadbeef00", buttons[0].nonce)
        assertEquals("deadbeef00", buttons[1].nonce)
    }

    @Test
    fun `空正文返回空列表`() {
        assertEquals(0, Golink.extractButtons("").size)
        assertEquals(0, Golink.extractButtons("   ").size)
    }

    // ---------- base64 变体 ----------

    @Test
    fun `url-safe 与无 padding 的 base64 也能解码`() {
        val plain = Base64.getEncoder().encodeToString("https://pan.example.com/a?x=1".toByteArray())
        val noPadding = plain.trimEnd('=')
        val urlSafe = plain.replace('+', '-').replace('/', '_')

        val expected = "https://pan.example.com/a?x=1"
        assertEquals(expected, Golink.decodeBase64Target(plain))
        assertEquals(expected, Golink.decodeBase64Target(noPadding))
        assertEquals(expected, Golink.decodeBase64Target(urlSafe))
    }

    @Test
    fun `非 base64 或非链接内容返回 null`() {
        assertNull(Golink.decodeBase64Target("!!!not-base64!!!"))
        assertNull(Golink.decodeBase64Target(Base64.getEncoder().encodeToString("hello".toByteArray())))
    }

    @Test
    fun `查询参数里的加号保持原样，不会被当作空格`() {
        // base64 可能含 '+'；OkHttp 的 queryParameter 会把 '+' 解码成空格，这里必须避免
        assertEquals("ab+cd", "https://www.example.com/?golink=ab+cd&nonce=n".queryParameter("golink"))
        assertEquals("c+d", "https://www.example.com/?golink=c%2Bd".queryParameter("golink"))
        assertEquals("a b", "https://www.example.com/?golink=a%20b".queryParameter("golink"))
        assertNull("https://www.example.com/?other=1".queryParameter("golink"))
    }

    // ---------- 跳转页解析 ----------

    @Test
    fun `从跳转页提取真实下载地址，且不误取 noscript 兜底地址`() {
        val target = Golink.parseJumpTarget(fixture("golink_jump_sample.html"))
        assertEquals("https://pan.example.com/s/TEST1234?pwd=TEST", target)
    }

    @Test
    fun `没有跳转脚本的页面返回 null`() {
        assertNull(Golink.parseJumpTarget("<html><body><p>nothing</p></body></html>"))
    }

    // ---------- 完整解析链路（本地 mock 服务器，不外联） ----------

    @Test
    fun `resolve 请求 golink 链接并解析出跳转页目标`() = runBlocking {
        server.enqueue(
            MockResponse.Builder().code(200).body(fixture("golink_jump_sample.html")).build()
        )
        val client = createHttpClient(Session())
        val href = server.url("/?golink=aHR0cHM6Ly9wYW4uZXhhbXBsZS5jb20v&nonce=deadbeef00").toString()

        val result = Golink.resolve(client, href)

        assertEquals(Golink.ResolveResult.Resolved("https://pan.example.com/s/TEST1234?pwd=TEST"), result)
        val request = server.takeRequest()
        assertEquals("/?golink=aHR0cHM6Ly9wYW4uZXhhbXBsZS5jb20v&nonce=deadbeef00", request.url.encodedPath + "?" + request.url.encodedQuery)
        // 请求头与网页一致
        assertEquals(BROWSER_USER_AGENT, request.headers["User-Agent"])
    }

    @Test
    fun `resolve 支持直接 302 跳转的响应，并解析相对 Location`() = runBlocking {
        server.enqueue(
            MockResponse.Builder().code(302).addHeader("Location", "/s/TEST1234").build()
        )
        val client = createHttpClient(Session())
        val href = server.url("/?golink=abc&nonce=deadbeef00").toString()

        val result = Golink.resolve(client, href)

        val expected = server.url("/s/TEST1234").toString()
        assertEquals(Golink.ResolveResult.Resolved(expected), result)
    }

    @Test
    fun `resolve 对无法解析的页面如实返回失败`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body("<html>无跳转脚本</html>").build())
        val client = createHttpClient(Session())

        val result = Golink.resolve(client, server.url("/?golink=abc").toString())

        assertTrue(result is Golink.ResolveResult.Failed)
    }

    @Test
    fun `resolve 对错误状态码如实返回失败`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(503).body("unavailable").build())
        val client = createHttpClient(Session())

        val result = Golink.resolve(client, server.url("/?golink=abc").toString())

        assertTrue(result is Golink.ResolveResult.Failed)
        assertNotNull((result as Golink.ResolveResult.Failed).reason)
    }
}
