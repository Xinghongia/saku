package top.sakuraxy.client.core

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 站点页面（HTML）解析层。
 *
 * 为什么需要它：WordPress REST 给不到主题特有的数据——
 * 列表卡片的封面兜底（未设特色图时用正文首图）、徽章、多级分类、标签、
 * 作者、评论/浏览/点赞数、排序、分页，这些只存在于页面 HTML 里。
 * 解析规则全部依据 2026-10-15 实测的页面结构（research/probe/），样本已脱敏进单测。
 */
object SiteParser {

    // ---------- 数据模型 ----------

    data class CardTerm(val name: String, val url: String)

    data class PostCard(
        /** 文章永久链接（绝对地址） */
        val url: String,
        /** 从链接里提取的文章 ID（/12345 形式）；取不到为 null */
        val id: Long?,
        val title: String,
        val coverUrl: String?,
        /** 缩略图角标，如「好用」 */
        val badge: String?,
        /** 付费徽章文字，如「付费资源」 */
        val payLabel: String?,
        /** 付费徽章的积分数，如「2」 */
        val payPoints: String?,
        /** 卡片上列出的分类（含多级） */
        val categories: List<CardTerm>,
        /** 卡片上列出的标签（不含 # 前缀） */
        val tags: List<String>,
        val authorName: String?,
        val authorAvatar: String?,
        /** 作者身份徽章，如「钻石会员」 */
        val authorVipBadge: String?,
        /** 相对时间文案，如「前天」「21小时前」 */
        val dateText: String?,
        /** 绝对时间（来自 title 属性），如「2026-10-13 10:13:34」 */
        val dateTitle: String?,
        val commentCount: Int?,
        val viewCount: Int?,
        val likeCount: Int?,
    )

    data class ListPage(
        val cards: List<PostCard>,
        val currentPage: Int?,
        val maxPage: Int?,
        val nextPageUrl: String?,
    )

    data class SortOption(val key: String, val label: String)

    data class CategoryHeader(val name: String, val postCountText: String?)

    // ---------- 列表页 ----------

    /** 站点支持的排序项（2026-10-15 实测自页面排序菜单） */
    val knownSortOptions: List<SortOption> = listOf(
        SortOption("modified", "更新"),
        SortOption("date", "发布"),
        SortOption("views", "浏览"),
        SortOption("like", "点赞"),
        SortOption("comment_count", "评论"),
        SortOption("favorite", "收藏"),
        SortOption("zibpay_price", "售价"),
        SortOption("zibpay_points_price", "积分"),
        SortOption("sales_volume", "销量"),
        SortOption("rand", "随机"),
    )

    fun parseListPage(html: String): ListPage {
        val doc = Jsoup.parse(html)
        val cards = doc.select("posts.posts-item").mapNotNull(::parseCard)
        val pagenav = doc.selectFirst(".pagenav")
        val current = pagenav?.selectFirst("span.page-numbers.current")?.text()?.trim()?.toIntOrNull()
            ?: pagenav?.selectFirst("input.jump-input")?.attr("current")?.toIntOrNull()
        val max = pagenav?.selectFirst("input.jump-input")?.attr("max")?.toIntOrNull()
        val next = pagenav?.selectFirst("a.next.page-numbers")?.absUrl("href")?.takeIf { it.isNotBlank() }
        return ListPage(cards, current, max, next)
    }

    /** 从页面排序菜单解析出可用排序项（按站点当前配置，而非写死） */
    fun parseSortOptions(html: String): List<SortOption> {
        val doc = Jsoup.parse(html)
        return doc.select("a[href*=orderby]").mapNotNull { a ->
            val key = a.absUrl("href").toHttpUrlOrNull()?.queryParameter("orderby")
                ?: a.attr("href").queryParameter("orderby")
                ?: return@mapNotNull null
            val label = a.text().trim()
            if (key.isBlank() || label.isBlank()) null else SortOption(key, label)
        }.distinctBy { it.key }
    }

    /** 分类页头部：分类名与文章数（如「音视频媒体」「共571篇」） */
    fun parseCategoryHeader(html: String): CategoryHeader? {
        val doc = Jsoup.parse(html)
        val b = doc.selectFirst(".page-cover-con .title-h-left b") ?: return null
        val count = b.selectFirst(".icon-spot")?.text()?.trim()
        val name = b.clone().apply { select(".icon-spot").remove() }.text().trim()
        return CategoryHeader(name = name, postCountText = count)
    }

    // ---------- 单张卡片 ----------

    private fun parseCard(card: Element): PostCard? {
        val link = card.selectFirst(".item-heading a") ?: return null
        val url = link.absUrl("href").takeIf { it.isNotBlank() } ?: return null

        val thumb = card.selectFirst(".item-thumbnail img")
        val cover = thumb?.let { imageUrl(it) }
        val badge = card.selectFirst(".item-thumbnail badge")?.text()?.trim()?.takeIf { it.isNotBlank() }

        val tagArea = card.selectFirst(".item-tags")
        val pay = tagArea?.selectFirst("a.meta-pay")
        // 付费徽章结构：付费资源<span><svg/></span>2 —— 数字是锚点内的直接文本节点
        val payTexts = pay?.childNodes()
            ?.filterIsInstance<org.jsoup.nodes.TextNode>()
            ?.map { it.wholeText.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        val payLabel = payTexts.firstOrNull()
        val payPoints = payTexts.drop(1).firstOrNull()
            ?: pay?.selectFirst("span")?.text()?.trim()?.takeIf { it.isNotBlank() }

        val categories = tagArea?.select("a.but[href]")?.mapNotNull { a ->
            if (a.hasClass("meta-pay")) return@mapNotNull null
            val href = a.absUrl("href")
            if (href.isBlank() || href.contains("/tag/")) return@mapNotNull null
            val name = a.clone().apply { select("i, svg").remove() }.text().trim()
            if (name.isBlank()) null else CardTerm(name, href)
        } ?: emptyList()

        val tags = tagArea?.select("a[href*=/tag/]")?.mapNotNull { a ->
            a.text().trim().removePrefix("#").trim().takeIf { it.isNotBlank() }
        } ?: emptyList()

        val meta = card.selectFirst(".meta-author")
        val nameSpan = meta?.select("span.ml6")?.firstOrNull { !hasTitleAttr(it) && !looksLikeTime(it.text()) }
        val timeSpan = meta?.selectFirst("span.icon-circle")
            ?: meta?.select("span.ml6")?.firstOrNull { hasTitleAttr(it) || looksLikeTime(it.text()) }
        val vipBadge = meta?.selectFirst("img.avatar-badge")?.attr("title")?.takeIf { it.isNotBlank() }
        val avatar = meta?.selectFirst("img.lazyload")?.let { imageUrl(it) }
        val authorName = nameSpan?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: avatar?.let { nameFromAvatarAlt(meta?.selectFirst("img")?.attr("alt").orEmpty()) }

        return PostCard(
            url = url,
            id = postIdFromUrl(url),
            title = link.text().trim(),
            coverUrl = cover,
            badge = badge,
            payLabel = payLabel,
            payPoints = payPoints,
            categories = categories,
            tags = tags,
            authorName = authorName,
            authorAvatar = avatar,
            authorVipBadge = vipBadge,
            dateText = timeSpan?.text()?.trim()?.takeIf { it.isNotBlank() },
            dateTitle = timeSpan?.attr("title")?.takeIf { it.isNotBlank() },
            commentCount = intOf(card.selectFirst(".meta-comm")),
            viewCount = intOf(card.selectFirst(".meta-view")),
            likeCount = intOf(card.selectFirst(".meta-like")),
        )
    }

    /** 取图：优先懒加载 data-src；占位图（thumbnail*.svg / avatar-default）不算真图（文章页解析共用） */
    internal fun imageUrl(img: Element): String? {
        val candidates = listOf(img.attr("data-src"), img.attr("src"))
        for (raw in candidates) {
            if (raw.isBlank()) continue
            if (raw.contains("thumbnail") && raw.endsWith(".svg")) continue
            val normalized = when {
                raw.startsWith("//") -> "https:$raw"
                raw.startsWith("http") -> raw
                else -> continue
            }
            // gravatar 系头像的查询串里带 d=（默认头像地址，站点设的 avatar-default.png）：
            // 保留它才能在用户没设头像时拿到站点默认头像；其余图片去掉 query 交给图片库处理
            return if (normalized.contains("geekzu.org") || normalized.contains("gravatar.com")) {
                normalized
            } else {
                normalized.substringBefore("?")
            }
        }
        return null
    }

    /** 作者头像 alt 形如「树上的小狗的头像-SakuraXy」，兜底提取名字 */
    internal fun nameFromAvatarAlt(alt: String): String? {
        if (alt.isBlank()) return null
        val name = alt.substringBefore("的头像")
            .substringBefore("的作者的 ta 头像")
            .removeSuffix("-SakuraXy")
            .trim()
        return name.takeIf { it.isNotBlank() && it != alt }
    }

    /** 文章永久链接形如 /12345 或 /12345.html（锚定到站点根路径，避免误判 /page/2 之类） */
    fun postIdFromUrl(url: String): Long? =
        Regex("^https?://[^/]+/(\\d+)(?:\\.html)?/?$").find(url)?.groupValues?.get(1)?.toLongOrNull()

    private fun intOf(el: Element?): Int? =
        el?.text()?.trim()?.replace(Regex("[^0-9]"), "")?.toIntOrNull()

    private fun hasTitleAttr(el: Element): Boolean = el.hasAttr("title")

    private fun looksLikeTime(text: String): Boolean {
        val t = text.trim()
        return t.contains("前") || t == "刚刚" || t.matches(Regex("\\d{4}-\\d{2}-\\d{2}.*"))
    }

    /** 站点搜索页地址：首页搜索 /?s=…，翻页 /page/N/?s=…，可选限定分类 cat=ID */
    fun searchUrl(baseUrl: String, query: String, page: Int = 1, categoryId: Long? = null): HttpUrl {
        val builder = baseUrl.toHttpUrl().newBuilder()
        if (page > 1) {
            builder.addPathSegment("page")
            builder.addPathSegment(page.toString())
        }
        builder.addQueryParameter("s", query)
        categoryId?.let { builder.addQueryParameter("cat", it.toString()) }
        return builder.build()
    }

    /**
     * 分类页路径或完整链接 → 列表页地址。
     * 传完整链接（分类的 term.link，可能是中文被百分号编码的路径）时直接在其上追加分页/排序，
     * 避免二次编码；传相对路径（如 "av-media"）时按路径拼接。
     */
    fun listUrl(baseUrl: String, categoryUrlOrPath: String?, sort: String?, page: Int): HttpUrl {
        val raw = categoryUrlOrPath?.trim()?.takeIf { it.isNotEmpty() }
        val builder = when {
            raw == null -> baseUrl.toHttpUrl().newBuilder()
            raw.startsWith("http") -> raw.toHttpUrl().newBuilder()
            else -> {
                val b = baseUrl.toHttpUrl().newBuilder()
                raw.trim('/').split('/').forEach { b.addPathSegment(it) }
                b
            }
        }
        if (page > 1) {
            builder.addPathSegment("page")
            builder.addPathSegment(page.toString())
        }
        sort?.takeIf { it.isNotBlank() }?.let { builder.addQueryParameter("orderby", it) }
        return builder.build()
    }
}

/** 页面级接口：拉取并解析站点 HTML */
class SiteApi(
    private val client: OkHttpClient,
    private val baseUrl: String = Site.BASE_URL,
) {
    suspend fun listPage(
        categoryUrlOrPath: String? = null,
        sort: String? = null,
        page: Int = 1,
    ): SiteParser.ListPage {
        require(page >= 1) { "page 从 1 开始" }
        val url = SiteParser.listUrl(baseUrl, categoryUrlOrPath, sort, page)
        return SiteParser.parseListPage(client.getText(url))
    }

    suspend fun searchPage(
        query: String,
        page: Int = 1,
        categoryId: Long? = null,
    ): SiteParser.ListPage {
        require(query.isNotBlank()) { "搜索关键词不能为空" }
        require(page >= 1) { "page 从 1 开始" }
        val url = SiteParser.searchUrl(baseUrl, query, page, categoryId)
        return SiteParser.parseListPage(client.getText(url))
    }

    /** 分类页头部（分类名 + 篇数） */
    suspend fun categoryHeader(categoryUrlOrPath: String): SiteParser.CategoryHeader? {
        val url = SiteParser.listUrl(baseUrl, categoryUrlOrPath, null, 1)
        return SiteParser.parseCategoryHeader(client.getText(url))
    }

    /** 文章详情页完整解析（头部/互动/评分/付费区/作者卡/首屏评论） */
    suspend fun postDetail(link: String): PostDetail {
        val url = link.toHttpUrlOrNull() ?: "$baseUrl/".toHttpUrl()
        return PostDetailParser.parsePostDetail(client.getText(url))
    }

    /** 评论分页（?cpage=N；N=1 即首屏）。返回解析后的评论页或 null */
    suspend fun commentsPage(link: String, page: Int): PostCommentPage? {
        require(page >= 1) { "page 从 1 开始" }
        val builder = (link.toHttpUrlOrNull() ?: "$baseUrl/".toHttpUrl()).newBuilder()
        if (page > 1) builder.addQueryParameter("cpage", page.toString())
        return PostDetailParser.parseCommentList(client.getText(builder.build()))
    }
}
