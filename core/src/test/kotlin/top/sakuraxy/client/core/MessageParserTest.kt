package top.sakuraxy.client.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 消息中心与私信解析（样本为 2026-10-16 真机抓取并脱敏） */
class MessageParserTest {

    // ---- 消息中心 ----

    @Test
    fun `消息中心：五个标签与空态`() {
        val center = MessageParser.parseCenter(fixture("message_center_sample.html"))
        assertEquals(
            listOf("news", "posts", "like", "private", "system"),
            center.tabs.map { it.key },
        )
        assertEquals(listOf("未读消息", "文章评论", "点赞喜欢", "私信消息", "系统通知"), center.tabs.map { it.label })
        assertTrue(center.empty)
        assertTrue(center.messages.isEmpty())
    }

    @Test
    fun `私信标签：会话列表条目`() {
        val center = MessageParser.parseCenter(fixture("chat_lists_sample.html"))
        assertEquals(1, center.chats.size)
        val chat = center.chats.first()
        assertEquals(99999999L, chat.userId)
        assertEquals("未知用户", chat.name)
        assertEquals("hi", chat.lastText)
        assertNotNull(chat.time)
        assertTrue(chat.openUrl!!.contains("action=private_window"))
    }

    // ---- 私信窗口 ----

    @Test
    fun `私信窗口：历史气泡与发送表单`() {
        val chat = MessageParser.parseChatWindow(fixture("chat_history_sample.html"))
        assertEquals(1, chat.messages.size)
        val msg = chat.messages.first()
        assertTrue(msg.mine)
        assertEquals("hi", msg.text)
        assertEquals("2026-10-16 12:46", msg.dateGroup)
        assertTrue(msg.unread)
        assertNotNull(msg.avatarUrl)

        // 发送表单（nonce 现取现用）
        assertEquals("send_private", chat.sendFields["action"])
        assertEquals("10086", chat.sendFields["send_user"])
        assertEquals("99999999", chat.sendFields["receive_user"])
        assertEquals("n0nce00000", chat.sendFields["send_private_nonce"])

        // 图片上传字段（data-name/data-value 形式）
        assertEquals("user_upload_image", chat.imageUploadFields["action"])
        assertEquals("n0nce00000", chat.imageUploadFields["upload_image_nonce"])
    }

    @Test
    fun `私信窗口：弹窗版含对方信息与黑名单`() {
        val chat = MessageParser.parseChatWindow(fixture("chat_modal_sample.html"))
        assertEquals("示例作者", chat.peerName)
        assertNotNull(chat.peerAvatarUrl)
        assertNotNull(chat.blacklistUrl)
        assertTrue(chat.blacklistUrl!!.contains("action=private_blacklist"))
        assertEquals("send_private", chat.sendFields["action"])
    }

    @Test
    fun `私信窗口：空历史不崩溃`() {
        val chat = MessageParser.parseChatWindow("<html><body></body></html>")
        assertTrue(chat.messages.isEmpty())
        assertNull(chat.peerName)
        assertNull(chat.blacklistUrl)
    }
}
