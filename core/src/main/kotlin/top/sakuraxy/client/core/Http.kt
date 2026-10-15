package top.sakuraxy.client.core

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 与网页等效的请求头。
 * UA 沿用 2026-10-15 实测通过的值（research/probe/）；改动前先复核站点对 golink 页面与 REST 的响应。
 */
const val BROWSER_USER_AGENT: String =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36"

fun createHttpClient(session: Session): OkHttpClient =
    OkHttpClient.Builder()
        .cookieJar(session.cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .build()
            )
        }
        .build()

class SakuHttpException(
    val status: Int,
    val url: String,
    val bodySnippet: String,
) : IOException("HTTP $status @ $url: $bodySnippet")

internal data class HttpTextResponse(val status: Int, val body: String)

internal suspend fun OkHttpClient.getText(url: HttpUrl): String = withContext(Dispatchers.IO) {
    newCall(Request.Builder().url(url).get().build()).execute().use { response ->
        val body = response.body.string()
        if (!response.isSuccessful) {
            throw SakuHttpException(response.code, url.toString(), body.snippet())
        }
        body
    }
}

internal suspend fun OkHttpClient.postForm(url: HttpUrl, form: FormBody): HttpTextResponse =
    withContext(Dispatchers.IO) {
        newCall(Request.Builder().url(url).post(form).build()).execute().use { response ->
            HttpTextResponse(response.code, response.body.string())
        }
    }

internal fun String.snippet(max: Int = 200): String =
    replace(Regex("\\s+"), " ").trim().take(max)
