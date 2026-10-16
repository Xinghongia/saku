package top.sakuraxy.client.core

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {

    private val siteUrl = "https://www.example.com/".toHttpUrl()

    private fun cookie(name: String, value: String, domain: String = "www.example.com") =
        Cookie.Builder().name(name).value(value).domain(domain).path("/").build()

    private fun loginCookie(value: String = "abc") =
        cookie("${Session.LOGIN_COOKIE_PREFIX}a1d0a8b6", value)

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

    @Test
    fun `登录 cookie 写入推动 revision 且识别为已登录`() {
        val session = Session()
        assertEquals(0L, session.revision.value)
        session.cookieJar.saveFromResponse(siteUrl, listOf(loginCookie()))
        assertEquals(1L, session.revision.value)
        assertTrue(session.isLoggedIn())
    }

    @Test
    fun `匿名 cookie 变化不推动 revision`() {
        val session = Session()
        session.cookieJar.saveFromResponse(siteUrl, listOf(cookie("PHPSESSID", "abc")))
        assertEquals(0L, session.revision.value)
        assertFalse(session.isLoggedIn())
    }

    @Test
    fun `过期或清空的登录 cookie 视为登出且不再下发`() {
        val session = Session()
        session.cookieJar.saveFromResponse(siteUrl, listOf(loginCookie()))
        val expired = Cookie.Builder()
            .name("${Session.LOGIN_COOKIE_PREFIX}a1d0a8b6")
            .value("x")
            .domain("www.example.com")
            .path("/")
            .expiresAt(System.currentTimeMillis() - 1000)
            .build()
        session.cookieJar.saveFromResponse(siteUrl, listOf(expired))

        assertFalse(session.isLoggedIn())
        assertTrue(session.cookieJar.loadForRequest(siteUrl).none { it.name.startsWith(Session.LOGIN_COOKIE_PREFIX) })
    }

    @Test
    fun `restore 后识别登录态并推动 revision`() {
        val source = Session()
        source.cookieJar.saveFromResponse(siteUrl, listOf(loginCookie()))
        val restored = Session()
        assertEquals(0L, restored.revision.value)
        restored.restore(source.snapshot())
        assertEquals(1L, restored.revision.value)
        assertTrue(restored.isLoggedIn())
    }

    @Test
    fun `clear 推动 revision 且退出登录态`() {
        val session = Session()
        session.cookieJar.saveFromResponse(siteUrl, listOf(loginCookie()))
        val before = session.revision.value
        session.clear()
        assertEquals(before + 1, session.revision.value)
        assertFalse(session.isLoggedIn())
    }
}
