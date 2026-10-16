package top.sakuraxy.client.core

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultipartTest {

    @Test
    fun `multipart 表单包含字段与文件两部分`() {
        val body = buildMultipartBody(
            fields = mapOf("action" to "user_upload_avatar", "user_id" to "15059"),
            files = listOf(
                "file" to UploadFile("avatar.jpg", "image/jpeg", byteArrayOf(1, 2, 3, 4)),
            ),
        )

        assertEquals("multipart", body.contentType()?.type)
        assertEquals("form-data", body.contentType()?.subtype)
        val buffer = Buffer()
        body.writeTo(buffer)
        val text = buffer.readUtf8()

        assertTrue(text.contains("name=\"action\""))
        assertTrue(text.contains("user_upload_avatar"))
        assertTrue(text.contains("name=\"user_id\""))
        assertTrue(text.contains("name=\"file\"; filename=\"avatar.jpg\""))
        assertTrue(text.contains("Content-Type: image/jpeg"))
    }

    @Test
    fun `多文件使用各自的字段名与 mime`() {
        val body = buildMultipartBody(
            fields = emptyMap(),
            files = listOf(
                "weixin" to UploadFile("wx.png", "image/png", byteArrayOf(9)),
                "alipay" to UploadFile("ali.gif", "image/gif", byteArrayOf(8)),
            ),
        )
        val buffer = Buffer()
        body.writeTo(buffer)
        val text = buffer.readUtf8()

        assertTrue(text.contains("name=\"weixin\"; filename=\"wx.png\""))
        assertTrue(text.contains("Content-Type: image/png"))
        assertTrue(text.contains("name=\"alipay\"; filename=\"ali.gif\""))
        assertTrue(text.contains("Content-Type: image/gif"))
    }
}
