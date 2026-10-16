package top.sakuraxy.client.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 投稿表单解析（样本为 2026-10-16 真机抓取并脱敏） */
class NewPostParserTest {

    @Test
    fun `投稿表单：隐藏字段 分类层级 与滑块模式`() {
        val form = NewPostParser.parseComposeForm(fixture("newpost_form_sample.html"))
        assertNotNull(form)
        // 隐藏字段：posts_id=0（新投稿）、captcha_mode=slider
        assertEquals("0", form!!.fields["posts_id"])
        assertEquals("slider", form.captchaMode)
        assertTrue(form.categories.size >= 20)
        // 顶层分类
        val first = form.categories.first()
        assertEquals("3595", first.id)
        assertEquals("AI 人工智能", first.name)
        assertEquals(0, first.depth)
        // 子分类缩进一级（PDF文档 在 办公 之下）
        val pdf = form.categories.first { it.name == "PDF文档" }
        assertTrue(pdf.depth > 0)
    }

    @Test
    fun `投稿表单：缺失返回空`() {
        assertNull(NewPostParser.parseComposeForm("<html><body>未登录</body></html>"))
    }
}
