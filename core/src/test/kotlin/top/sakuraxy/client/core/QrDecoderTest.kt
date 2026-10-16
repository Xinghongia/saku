package top.sakuraxy.client.core

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.util.EnumMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 二维码离线解码（离线合成样本；真实站点样本的验证见 QrProbeScratch，仅本机运行）。
 * 用例形态对齐站点「付费图片」真样本：约 200px、底部带说明文字条。
 */
class QrDecoderTest {

    private val link = "https://pan.quark.cn/s/7683b75ed9b2?entry=webother"

    private fun encode(text: String, size: Int, margin: Int = 2): Triple<IntArray, Int, Int> {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
        hints[EncodeHintType.MARGIN] = margin
        hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        val matrix: BitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val px = IntArray(matrix.width * matrix.height)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                px[y * matrix.width + x] = if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        return Triple(px, matrix.width, matrix.height)
    }

    @Test
    fun `编解码回环：标准二维码`() {
        val (px, w, h) = encode(link, 300)
        assertEquals(link, QrDecoder.decodeArgb(px, w, h))
    }

    @Test
    fun `底部带说明文字条仍能解出（真样本形态）`() {
        val (qr, w, h) = encode(link, 200)
        val caption = 40
        val out = IntArray(w * (h + caption)) { 0xFFFFFFFF.toInt() }
        qr.copyInto(out, 0, 0, w * h)
        // 模拟底部「截图保存二维码…」文字：深浅交替的像素块
        for (y in h until h + caption) {
            for (x in 0 until w) {
                if ((x / 3 + y) % 2 == 0) out[y * w + x] = 0xFF333333.toInt()
            }
        }
        assertEquals(link, QrDecoder.decodeArgb(out, w, h + caption))
    }

    @Test
    fun `小尺寸二维码经放大分支解出`() {
        val (px, w, h) = encode(link, 96)
        assertTrue(minOf(w, h) < 400)
        assertEquals(link, QrDecoder.decodeArgb(px, w, h))
    }

    @Test
    fun `反色二维码也能解出`() {
        val (px, w, h) = encode(link, 260)
        val inverted = IntArray(px.size) { px[it] xor 0x00FFFFFF }
        assertEquals(link, QrDecoder.decodeArgb(inverted, w, h))
    }

    @Test
    fun `噪声图返回 null 而不是乱猜`() {
        val w = 200
        val h = 200
        var seed = 123456789L
        val px = IntArray(w * h) {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            if ((seed ushr 33).toInt() and 1 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        assertNull(QrDecoder.decodeArgb(px, w, h))
    }

    @Test
    fun `尺寸非法或像素不足时安全返回 null`() {
        assertNull(QrDecoder.decodeArgb(IntArray(4), 0, 0))
        assertNull(QrDecoder.decodeArgb(IntArray(3), 10, 10))
    }

    @Test
    fun `放大与裁剪工具：尺寸与像素映射正确`() {
        val (scaled, sw, sh) = QrDecoder.scale(intArrayOf(1, 2, 3, 4), 2, 2, 2)!!
        assertEquals(4, sw)
        assertEquals(4, sh)
        assertEquals(1, scaled[0])
        assertEquals(2, scaled[2])
        assertEquals(3, scaled[4 * 2])
        assertEquals(4, scaled[4 * 2 + 2])

        val (cropped, cw, ch) = QrDecoder.cropTop(IntArray(10 * 10) { it }, 10, 10, 0.8)!!
        assertEquals(10, cw)
        assertEquals(8, ch)
        assertEquals(0, cropped[0])
        assertEquals(79, cropped[79])
    }
}
