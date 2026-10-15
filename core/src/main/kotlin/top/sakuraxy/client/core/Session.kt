package top.sakuraxy.client.core

import kotlinx.serialization.Serializable
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * 站点会话（登录态）的内存 cookie 存储。
 *
 * 客户端只用用户自己的账号、与网页等效地访问站点，这里仅保存站点下发给自己会话的 cookie。
 * 跨启动持久化由 app 层负责：`snapshot()` 取走、下次 `restore()` 放回（P3 接 DataStore）。
 */
class Session {
    private val cookies = linkedMapOf<String, Cookie>()

    val cookieJar: CookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            for (cookie in cookies) this@Session.cookies[cookie.name] = cookie
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            cookies.values.filter { it.matches(url) }
    }

    fun snapshot(): List<StoredCookie> = cookies.values.map {
        StoredCookie(
            name = it.name,
            value = it.value,
            domain = it.domain,
            path = it.path,
            expiresAt = it.expiresAt,
            secure = it.secure,
            httpOnly = it.httpOnly,
        )
    }

    fun restore(stored: List<StoredCookie>) {
        cookies.clear()
        for (s in stored) {
            if (s.domain.isBlank()) continue
            val builder = Cookie.Builder()
                .name(s.name)
                .value(s.value)
                .domain(s.domain)
                .path(s.path)
            if (s.secure) builder.secure()
            if (s.httpOnly) builder.httpOnly()
            if (s.expiresAt > 0) builder.expiresAt(s.expiresAt)
            val cookie = builder.build()
            cookies[cookie.name] = cookie
        }
    }

    fun clear() {
        cookies.clear()
    }
}

/** 可序列化的 cookie 快照，供 app 层持久化 */
@Serializable
data class StoredCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String = "/",
    val expiresAt: Long = 0L,
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
)
