package top.sakuraxy.client.vm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 分页窗口纯逻辑：页码-下标映射与翻页动作判定 */
class PageWindowTest {

    @Test
    fun `滚动位置换算当前页`() {
        // 已加载第 2..4 页，每页 20 项：2→0, 3→20, 4→40
        val w = PageWindow(starts = mapOf(2 to 0, 3 to 20, 4 to 40), loadedMin = 2, loadedMax = 4, maxPage = 48)

        assertEquals(2, w.pageAt(0))
        assertEquals(2, w.pageAt(19))
        assertEquals(3, w.pageAt(20))
        assertEquals(3, w.pageAt(39))
        assertEquals(4, w.pageAt(40))
        assertEquals(4, w.pageAt(59))
    }

    @Test
    fun `窗口空时按下界兜底`() {
        assertEquals(1, PageWindow().pageAt(0))
    }

    @Test
    fun `翻页动作：已加载滚动 相邻追加前插 跳远重开`() {
        val w = PageWindow(starts = mapOf(2 to 0, 3 to 20), loadedMin = 2, loadedMax = 3, maxPage = 48)

        assertEquals(PageWindow.Action.ScrollTo(0), w.actionFor(2))
        assertEquals(PageWindow.Action.ScrollTo(20), w.actionFor(3))
        assertEquals(PageWindow.Action.Append, w.actionFor(4))
        assertEquals(PageWindow.Action.Prepend, w.actionFor(1))
        assertEquals(PageWindow.Action.Reset, w.actionFor(30))
    }

    @Test
    fun `重开窗口：单页起点与总页数更新`() {
        val w = PageWindow(starts = mapOf(1 to 0, 2 to 20), loadedMin = 1, loadedMax = 2)
            .afterReset(30, 48)

        assertEquals(mapOf(30 to 0), w.starts)
        assertEquals(30, w.loadedMin)
        assertEquals(30, w.loadedMax)
        assertEquals(48, w.maxPage)
        assertTrue(w.isLoaded(30))
        assertFalse(w.isLoaded(1))
    }

    @Test
    fun `追加：记录新页起点并推进下界`() {
        val w = PageWindow(starts = mapOf(30 to 0), loadedMin = 30, loadedMax = 30, maxPage = 48)
            .afterAppend(31, 20, 48)

        assertEquals(20, w.starts[31])
        assertEquals(31, w.loadedMax)
        assertEquals(30, w.loadedMin)
        assertEquals(31, w.pageAt(20))
    }

    @Test
    fun `前插：既有页码下标整体后移，新页起点为 0`() {
        val w = PageWindow(starts = mapOf(30 to 0, 31 to 20), loadedMin = 30, loadedMax = 31, maxPage = 48)
            .afterPrepend(29, 20)

        assertEquals(0, w.starts[29])
        assertEquals(20, w.starts[30])
        assertEquals(40, w.starts[31])
        assertEquals(29, w.loadedMin)
        assertEquals(29, w.pageAt(0))
        assertEquals(30, w.pageAt(20))
        assertEquals(31, w.pageAt(40))
    }

    @Test
    fun `总页数缺失时保留旧值`() {
        val w = PageWindow(maxPage = 48).afterAppend(2, 20, null)
        assertEquals(48, w.maxPage)
    }
}
