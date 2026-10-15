package top.sakuraxy.client.core

import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 网络错误文案。
 *
 * 为什么需要：OkHttp 暴露的是英文异常（如 "timeout"），直接显示给用户不友好。
 * 这里把常见 IO 异常翻译成中文提示；未知错误保留原始信息，便于排查。
 */
internal fun Throwable.friendlyMessage(): String = when (this) {
    is SakuHttpException -> when (status) {
        401, 403 -> "没有访问权限（HTTP $status），可能需要登录"
        404 -> "内容不存在或已删除（HTTP 404）"
        in 500..599 -> "站点服务器出错（HTTP $status），请稍后再试"
        else -> "请求失败（HTTP $status）"
    }

    is java.net.SocketTimeoutException -> "连接超时，请检查网络后重试"
    is java.net.UnknownHostException -> "无法连接站点，请检查网络"
    is java.net.ConnectException -> "无法连接站点，请检查网络"
    else -> {
        val text = message.orEmpty()
        when {
            text.contains("timeout", ignoreCase = true) -> "连接超时，请检查网络后重试"
            "Unable to resolve host" in text -> "无法连接站点，请检查网络"
            else -> text.ifBlank { "加载失败，请重试" }
        }
    }
}

/** 给详情页这类多请求页面用：单次网络请求的时长上限（秒） */
internal const val REQUEST_TIMEOUT_HINT_SECONDS = 30L

internal fun OkHttpClient.withLongerTimeout(): OkHttpClient =
    newBuilder()
        .readTimeout(REQUEST_TIMEOUT_HINT_SECONDS, TimeUnit.SECONDS)
        .build()

internal fun IOException.isTimeout(): Boolean =
    this is java.net.SocketTimeoutException || message?.contains("timeout", ignoreCase = true) == true
