package top.sakuraxy.client.core

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer

/**
 * 二维码离线解码（纯 JVM，无 Android 依赖；位图以 ARGB IntArray 传入）。
 *
 * 站点「付费图片」型资源把网盘二维码做成图片让用户扫；客户端在本机解出链接，
 * 等价于用户自己拿手机扫码——不经过任何第三方识别服务，也不绕过付费门禁
 * （图片本身就是站点渲染给已购用户的 HTML 的一部分）。
 *
 * 多趟策略依据 2026-10-16 真样本（约 200px、底部带说明文字条「截图保存二维码…」）设计：
 * 原图两种二值化 → 小图放大 → 裁掉底部说明条再试。全部失败返回 null，由调用方如实提示。
 */
object QrDecoder {

    private val HINTS = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.CHARACTER_SET to "UTF-8",
        DecodeHintType.ALSO_INVERTED to true,
    )

    /** 单趟放大的输出像素上限（IntArray 内存守卫：8M px ≈ 32MB） */
    private const val MAX_SCALED_PIXELS = 8_000_000

    /** 解码成功返回文本，失败返回 null（不抛异常） */
    fun decodeArgb(pixels: IntArray, width: Int, height: Int): String? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        // ① 原图 Hybrid（常规截图/照片）
        decodeWith(pixels, width, height, hybrid = true)?.let { return it }
        // ② 原图 GlobalHistogram（小图、纯色背景更稳）
        decodeWith(pixels, width, height, hybrid = false)?.let { return it }
        // ③ 小图放大 3 倍（模块边缘太细时原尺寸读不出定位图案）
        if (minOf(width, height) < 400) {
            scale(pixels, width, height, 3)?.let { (px, w, h) ->
                decodeWith(px, w, h, hybrid = true)?.let { return it }
            }
        }
        // ④ 裁掉底部约 18%（说明文字条）后放大 2 倍再试
        cropTop(pixels, width, height, keepRatio = 0.82)?.let { (px, w, h) ->
            val src = if (minOf(w, h) < 400) scale(px, w, h, 2) else Triple(px, w, h)
            if (src != null) decodeWith(src.first, src.second, src.third, hybrid = true)?.let { return it }
        }
        return null
    }

    private fun decodeWith(pixels: IntArray, width: Int, height: Int, hybrid: Boolean): String? = try {
        val source = RGBLuminanceSource(width, height, pixels)
        val binary = BinaryBitmap(if (hybrid) HybridBinarizer(source) else GlobalHistogramBinarizer(source))
        MultiFormatReader().decode(binary, HINTS).text
    } catch (_: Exception) {
        // NotFoundException / ChecksumException / FormatException：当作这一趟没读出
        null
    }

    /** 最近邻整数倍放大（保住 QR 模块的锐利边界，比插值更适合条码） */
    internal fun scale(pixels: IntArray, width: Int, height: Int, factor: Int): Triple<IntArray, Int, Int>? {
        if (factor <= 1) return null
        val w = width * factor
        val h = height * factor
        if (w.toLong() * h > MAX_SCALED_PIXELS) return null
        val out = IntArray(w * h)
        var outIndex = 0
        for (y in 0 until height) {
            val rowStart = y * width
            repeat(factor) {
                for (x in 0 until width) {
                    val color = pixels[rowStart + x]
                    repeat(factor) { out[outIndex++] = color }
                }
            }
        }
        return Triple(out, w, h)
    }

    /** 只保留顶部 keepRatio 比例的行（去掉底部说明文字条） */
    internal fun cropTop(pixels: IntArray, width: Int, height: Int, keepRatio: Double): Triple<IntArray, Int, Int>? {
        val h = (height * keepRatio).toInt()
        if (h <= 0 || h >= height) return null
        return Triple(pixels.copyOfRange(0, width * h), width, h)
    }
}
