package top.sakuraxy.client

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.sakuraxy.client.core.QrDecoder

/**
 * 付费图片的二维码识别：只抓取站点渲染给已购用户的图片本身（与展示用的是同一个地址），
 * 在本机离线解码——等价于用户自己拿手机扫码，不经过任何第三方识别服务。
 *
 * 成功返回二维码文本；网络/解码失败一律返回 null，由界面如实提示（不猜、不重试轰炸）。
 */
object PayImageQr {

    /** 图片体积上限（二维码截图 ~16KB，8MB 足够宽松） */
    private const val MAX_BYTES = 8L * 1024 * 1024

    /** 解码尺寸守卫：超大图直接放弃，缩到长边该值以内再喂给解码器 */
    private const val MAX_SIDE = 8192
    private const val MAX_PIXELS = 32_000_000L
    private const val DECODE_MAX_SIDE = 2048

    suspend fun fetchAndDecode(http: OkHttpClient, url: String): String? {
        val bytes = fetch(http, url) ?: return null
        val pixels = withContext(Dispatchers.Default) { decodePixels(bytes) } ?: return null
        return withContext(Dispatchers.Default) {
            QrDecoder.decodeArgb(pixels.first, pixels.second, pixels.third)
        }
    }

    private suspend fun fetch(http: OkHttpClient, url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).get().build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                if (response.body.contentLength() > MAX_BYTES) return@use null
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                val input = response.body.byteStream()
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_BYTES) return@use null
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
        } catch (_: IOException) {
            null
        }
    }

    /** 字节 → ARGB 像素（先读尺寸守卫，再按需降采样解码；失败返回 null） */
    private fun decodePixels(bytes: ByteArray): Triple<IntArray, Int, Int>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null
        if (width > MAX_SIDE || height > MAX_SIDE || width.toLong() * height > MAX_PIXELS) return null

        var sample = 1
        while (maxOf(width, height) / sample > DECODE_MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val decoded = Triple(pixels, bitmap.width, bitmap.height)
        bitmap.recycle()
        return decoded
    }
}
