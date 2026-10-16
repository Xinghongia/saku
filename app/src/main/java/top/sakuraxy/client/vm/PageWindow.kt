package top.sakuraxy.client.vm

/**
 * 列表分页窗口（纯逻辑，可离线单测）。
 *
 * 列表内容 = 一段**连续页码**的卡片累积；本类跟踪这段区间（loadedMin..loadedMax）
 * 与每页首项在列表中的下标（starts），由此决定一次翻页该怎么走：
 * - 目标页已加载 → 直接滚动过去（不重新请求）
 * - 目标页是区间下方的相邻页 → 追加（下一页）
 * - 目标页是区间上方的相邻页 → 前插（上一页）
 * - 跳得很远（不相邻且未加载）→ 重开窗口为该单页（回到顶部）
 */
data class PageWindow(
    /** 页码 → 该页首项在列表中的下标 */
    val starts: Map<Int, Int> = emptyMap(),
    val loadedMin: Int = 1,
    val loadedMax: Int = 1,
    /** 站点给出的总页数（首次加载后才有） */
    val maxPage: Int? = null,
) {

    /** 列表第 index 项属于哪一页（用于把滚动位置换算成"当前第几页"） */
    fun pageAt(index: Int): Int =
        starts.entries.filter { it.value <= index }.maxByOrNull { it.value }?.key ?: loadedMin

    fun isLoaded(page: Int): Boolean = page in loadedMin..loadedMax

    /** 目标页的走法 */
    fun actionFor(page: Int): Action = when {
        isLoaded(page) -> Action.ScrollTo(starts[page] ?: 0)
        page == loadedMax + 1 -> Action.Append
        page == loadedMin - 1 -> Action.Prepend
        else -> Action.Reset
    }

    /** 重开窗口为第 page 页（跳远 / 刷新 / 换排序） */
    fun afterReset(page: Int, siteMaxPage: Int?): PageWindow =
        copy(
            starts = mapOf(page to 0),
            loadedMin = page,
            loadedMax = page,
            maxPage = siteMaxPage ?: maxPage,
        )

    /** 追加第 page 页（其首项落在 firstIndex） */
    fun afterAppend(page: Int, firstIndex: Int, siteMaxPage: Int?): PageWindow =
        copy(
            starts = starts + (page to firstIndex),
            loadedMax = page,
            maxPage = siteMaxPage ?: maxPage,
        )

    /** 前插第 page 页（新内容 addedCount 项加在最前面，既有下标整体后移） */
    fun afterPrepend(page: Int, addedCount: Int): PageWindow =
        copy(
            starts = mapOf(page to 0) + starts.mapValues { it.value + addedCount },
            loadedMin = page,
        )

    sealed interface Action {
        /** 已加载：滚动到该下标即可 */
        data class ScrollTo(val index: Int) : Action

        /** 加载区间下方相邻页并追加 */
        data object Append : Action

        /** 加载区间上方相邻页并前插 */
        data object Prepend : Action

        /** 跳远：重开窗口为该单页 */
        data object Reset : Action
    }
}
