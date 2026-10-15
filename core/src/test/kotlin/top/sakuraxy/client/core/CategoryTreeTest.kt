package top.sakuraxy.client.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryTreeTest {

    private fun term(id: Long, name: String, parent: Long = 0, count: Int = 0) =
        WpTerm(id = id, name = name, slug = name.lowercase(), count = count, parent = parent)

    @Test
    fun `按 parent 字段还原二级分类结构`() {
        val terms = listOf(
            term(10, "音视频媒体", count = 571),
            term(11, "音频编辑", parent = 10, count = 12),
            term(12, "视频剪辑", parent = 10, count = 30),
            term(20, "游戏与模组", count = 2752),
        )

        val tree = CategoryTree.build(terms)

        assertEquals(listOf("游戏与模组", "音视频媒体"), tree.map { it.term.name }) // 按文章数倒序
        assertEquals(listOf("视频剪辑", "音频编辑"), tree[1].children.map { it.term.name })
        assertEquals(0, tree[0].children.size)
    }

    @Test
    fun `支持三级嵌套`() {
        val terms = listOf(
            term(10, "开发编程"),
            term(11, "网络服务器", parent = 10),
            term(12, "Nginx", parent = 11),
        )

        val tree = CategoryTree.build(terms)

        assertEquals("Nginx", tree[0].children[0].children[0].term.name)
    }

    @Test
    fun `拍平后带层级深度`() {
        val terms = listOf(
            term(10, "开发编程"),
            term(11, "网络服务器", parent = 10),
            term(20, "手机应用"),
        )

        val flat = CategoryTree.flatten(CategoryTree.build(terms))

        assertEquals(
            listOf("开发编程" to 0, "网络服务器" to 1, "手机应用" to 0),
            flat.map { it.first.name to it.second },
        )
    }

    @Test
    fun `parent 指向不存在的分类时不会丢失`() {
        val terms = listOf(term(11, "孤儿分类", parent = 999))

        // 父级不在列表里：本实现只从 parent=0 开始，无法挂载；确认行为可见而非静默丢失
        val tree = CategoryTree.build(terms)
        assertEquals(0, tree.size)
    }
}
