package top.sakuraxy.client.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * 站点会话（登录态）的内存 cookie 存储。
 *
 * 客户端只用用户自己的账号、与网页等效地访问站点，这里仅保存站点下发给自己会话的 cookie。
 * 跨启动持久化由 app 层负责：`snapshot()` 取走、下次 `restore()` 放回（P3 接 DataStore）。
 *
 * [revision] 在登录态相关 cookie 变化（登录/登出/恢复）时自增，UI 订阅它实现自动刷新；
 * 普通匿名 cookie（如 PHPSESSID）变化不会触发，避免无意义刷新。
 */
class Session {
    private val cookies = linkedMapOf<String, Cookie>()
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    val cookieJar: CookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val now = System.currentTimeMillis()
            var loginTouched = false
            for (cookie in cookies) {
                if (cookie.name.startsWith(LOGIN_COOKIE_PREFIX)) loginTouched = true
                // 值为空或已过期 = 站点登出/清理，直接移除（OkHttp 约定：过期 cookie 由 CookieJar 负责丢弃）
                if (cookie.value.isBlank() || cookie.isExpiredAt(now)) {
                    this@Session.cookies.remove(cookie.name)
                } else {
                    this@Session.cookies[cookie.name] = cookie
                }
            }
            if (loginTouched) bump()
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            return cookies.values.filter { it.matches(url) && !it.isExpiredAt(now) }
        }
    }

    /** 是否持有有效的登录 cookie（wordpress_logged_in_*） */
    fun isLoggedIn(): Boolean {
        val now = System.currentTimeMillis()
        return cookies.values.any { it.name.startsWith(LOGIN_COOKIE_PREFIX) && !it.isExpiredAt(now) }
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
        bump()
    }

    fun clear() {
        cookies.clear()
        bump()
    }

    private fun bump() {
        _revision.value += 1
    }

    companion object {
        /** WordPress 登录 cookie 前缀（带站点 hash 后缀） */
        const val LOGIN_COOKIE_PREFIX: String = "wordpress_logged_in_"
    }
}

/** 会话 cookie（expiresAt 为 Long.MAX_VALUE / 0 / 负数）视为未过期；只有明确的过去时间才算过期 */
private fun Cookie.isExpiredAt(now: Long): Boolean =
    expiresAt > 0 && expiresAt != Long.MAX_VALUE && expiresAt <= now

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
