package top.sakuraxy.client.core

import org.jsoup.Jsoup

/** HTML 文本工具：站点的标题/摘要里带实体与少量标签，展示前先转纯文本 */
object Html {
    fun toText(html: String): String = Jsoup.parse(html).text()
}
