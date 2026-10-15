package top.sakuraxy.client.core

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {

    private val siteUrl = "https://www.example.com/".toHttpUrl()

    private fun cookie(name: String, value: String, domain: String = "www.example.com") =
        Cookie.Builder().name(name).value(value).domain(domain).path("/").build()

    @Test
    fun `保存站点 cookie 后，同站请求带上`() {
        val session = Session()
        session.cookieJar.saveFromResponse(siteUrl, listOf(cookie("PHPSESSID", "abc123")))

        val loaded = session.cookieJar.loadForRequest(siteUrl)
        assertEquals(1, loaded.size)
        assertEquals("PHPSESSID", loaded[0].name)
        assertEquals("abc123", loaded[0].value)
    }

    @Test
    fun `cookie 不会带给不匹配的域名`() {
        val session = Session()
        session.cookieJar.saveFromResponse(siteUrl, listOf(cookie("PHPSESSID", "abc123")))

        val other = session.cookieJar.loadForRequest("https://other.example.com/".toHttpUrl())
        assertTrue(other.isEmpty())
    }

    @Test
    fun `同名 cookie 后写入的覆盖先前的`() {
        val session = Session()
        session.cookieJar.saveFromResponse(siteUrl, listOf(cookie("PHPSESSID", "old")))
        session.cookieJar.saveFromResponse(siteUrl, listOf(cookie("PHPSESSID", "new")))

        val loaded = session.cookieJar.loadForRequest(siteUrl)
        assertEquals(1, loaded.size)
        assertEquals("new", loaded[0].value)
    }

    @Test
    fun `快照与恢复能往返`() {
        val session = Session()
        session.cookieJar.saveFromResponse(
            siteUrl,
            listOf(cookie("PHPSESSID", "abc123"), cookie("other", "xyz"))
        )

        val restored = Session()
        restored.restore(session.snapshot())

        val loaded = restored.cookieJar.loadForRequest(siteUrl).associate { it.name to it.value }
        assertEquals(mapOf("PHPSESSID" to "abc123", "other" to "xyz"), loaded)
    }

    @Test
    fun `清空后不再返回任何 cookie`() {
        val session = Session()
        session.cookieJar.saveFromResponse(siteUrl, listOf(cookie("PHPSESSID", "abc123")))
        session.clear()

        assertTrue(session.cookieJar.loadForRequest(siteUrl).isEmpty())
    }
}
