package top.sakuraxy.client.core

import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * 下载按钮解析（golink 链路）。
 *
 * 2026-10-15 实测（research/probe/）：文章正文里的下载按钮形如
 *   https://www.sakuraxy.top/?golink=<base64>&nonce=<hex>
 * 请求该地址后（登录要求与网页一致）返回一个跳转页，页面脚本里 `location.href` 即真实网盘地址。
 * 客户端只复刻这个官方流程：不跳过站点自己的跳转步骤，也不绕过任何权限要求。
 */
object Golink {
    const val PARAM = "golink"

    data class Button(
        /** 按钮文字，如「网盘下载」「PC」 */
        val label: String,
        /** 按钮原始链接（已解析为绝对地址） */
        val href: String,
        /** base64 解码出的目标；仅用于诊断与测试，给用户的跳转仍走站点 golink 流程 */
        val decodedTarget: String?,
        val nonce: String?,
    )

    sealed interface ResolveResult {
        data class Resolved(val url: String) : ResolveResult

        data class Failed(val reason: String) : ResolveResult
    }

    /** 从文章正文 HTML 里按页面顺序提取所有 golink 下载按钮（离线可测） */
    fun extractButtons(contentHtml: String, baseUrl: String = Site.BASE_URL): List<Button> {
        if (contentHtml.isBlank()) return emptyList()
        val base = baseUrl.toHttpUrlOrNull()
        val doc = Jsoup.parse(contentHtml)
        return doc.select("a[href*=$PARAM]").mapNotNull { anchor ->
            val raw = anchor.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val absolute = base?.resolve(raw)?.toString() ?: raw
            val b64 = absolute.queryParameter(PARAM) ?: return@mapNotNull null
            Button(
                label = anchor.text().trim(),
                href = absolute,
                decodedTarget = decodeBase64Target(b64),
                nonce = absolute.queryParameter("nonce"),
            )
        }
    }

    /** base64（含无 padding / URL-safe 变体）解码为 http(s) 链接；失败返回 null */
    fun decodeBase64Target(b64: String): String? = try {
        val normalized = b64.replace('-', '+').replace('_', '/')
        val padded = normalized.padEnd((normalized.length + 3) / 4 * 4, '=')
        String(Base64.getDecoder().decode(padded), Charsets.UTF_8)
            .takeIf { it.startsWith("http://") || it.startsWith("https://") }
    } catch (_: IllegalArgumentException) {
        null
    }

    /** 从跳转页 HTML 提取脚本里的目标地址（离线可测） */
    fun parseJumpTarget(html: String): String? =
        JUMP_PATTERN.find(html)?.groupValues?.get(1)

    /** 访问 golink 链接并解析出真实下载地址；不追加重定向，避免误触外部站点 */
    suspend fun resolve(client: OkHttpClient, href: String): ResolveResult {
        val url = href.toHttpUrlOrNull() ?: return ResolveResult.Failed("下载链接格式无法识别")
        val noFollow = client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        val response = try {
            withContext(Dispatchers.IO) {
                noFollow.newCall(Request.Builder().url(url).get().build()).execute().use {
                    HttpResponse(it.code, it.header("Location"), it.body.string())
                }
            }
        } catch (e: IOException) {
            return ResolveResult.Failed("网络错误：${e.message ?: e.javaClass.simpleName}")
        }
        if (response.status in 300..399) {
            val location = response.location ?: return ResolveResult.Failed("跳转响应缺少目标地址")
            return ResolveResult.Resolved(url.resolve(location)?.toString() ?: location)
        }
        if (response.status !in 200..299) return ResolveResult.Failed("站点返回 HTTP ${response.status}")
        parseJumpTarget(response.body)?.let { return ResolveResult.Resolved(it) }
        return ResolveResult.Failed("未能从跳转页解析出下载地址（页面结构可能已变化）")
    }

    private data class HttpResponse(val status: Int, val location: String?, val body: String)

    private val JUMP_PATTERN = Regex("""location(?:\.href)?\s*=\s*["'](https?://[^"']+)["']""")
}

/**
 * 取 URL 查询参数（值做百分号解码，但保留 '+' 原样）。
 *
 * 为什么不用 OkHttp 的 queryParameter：它把 '+' 解码成空格（实测 okhttp 5.3.2），
 * 而 golink 的 base64 值里可能出现 '+'，被当空格会直接毁掉链接。
 */
internal fun String.queryParameter(name: String): String? {
    val parsed = toHttpUrlOrNull()
    val query = parsed?.encodedQuery
        ?: substringAfter('?', "").substringBefore('#')
    if (query.isEmpty()) return null
    for (part in query.split('&')) {
        if (part.isEmpty()) continue
        val eq = part.indexOf('=')
        if (eq <= 0) continue
        if (part.substring(0, eq) == name) return decodePreservingPlus(part.substring(eq + 1))
    }
    return null
}

private fun decodePreservingPlus(value: String): String =
    try {
        java.net.URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        value
    }
