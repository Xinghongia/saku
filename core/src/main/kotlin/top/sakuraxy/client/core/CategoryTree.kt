package top.sakuraxy.client.core

/**
 * 分类树（二级及更深层级由 REST 的 parent 字段还原）。
 */
object CategoryTree {

    data class Node(val term: WpTerm, val children: List<Node>)

    fun build(terms: List<WpTerm>): List<Node> {
        val byParent = terms.groupBy { it.parent }
        fun childrenOf(parentId: Long): List<Node> =
            byParent[parentId].orEmpty()
                .sortedByDescending { it.count }
                .map { Node(it, childrenOf(it.id)) }
        return childrenOf(0L)
    }

    /** 拍平成一维列表（带层级缩进信息），供简单列表 UI 使用 */
    fun flatten(nodes: List<Node>, depth: Int = 0): List<Pair<WpTerm, Int>> =
        nodes.flatMap { listOf(it.term to depth) + flatten(it.children, depth + 1) }
}
