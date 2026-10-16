package top.sakuraxy.client.core

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * WordPress REST 只读接口（/wp-json/wp/v2/…）。
 *
 * 说明（2026-10-15 实测）：posts / categories / tags 匿名可读，返回完整正文。
 * 站点热度排序（views/like/zibpay_price 等）是主题页面能力，REST 不支持，另行评估。
 */
class WpApi(
    private val client: OkHttpClient,
    baseUrl: String = Site.BASE_URL,
) {
    private val restBase: HttpUrl = "$baseUrl/wp-json/wp/v2/".toHttpUrl()

    suspend fun posts(
        page: Int = 1,
        perPage: Int = 20,
        category: Long? = null,
        tag: Long? = null,
        search: String? = null,
        orderby: String? = null,
        order: String? = null,
        embedFeaturedMedia: Boolean = true,
    ): List<WpPost> {
        require(page >= 1) { "page 从 1 开始" }
        require(perPage in 1..100) { "per_page 取值范围 1..100（WordPress 限制）" }
        val url = restBase.newBuilder()
            .addPathSegment("posts")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", perPage.toString())
            .apply {
                category?.let { addQueryParameter("categories", it.toString()) }
                tag?.let { addQueryParameter("tags", it.toString()) }
                search?.takeIf { it.isNotBlank() }?.let { addQueryParameter("search", it) }
                orderby?.let { addQueryParameter("orderby", it) }
                order?.let { addQueryParameter("order", it) }
                if (embedFeaturedMedia) addQueryParameter("_embed", "wp:featuredmedia")
            }
            .build()
        return SakuJson.decodeFromString(client.getText(url))
    }

    suspend fun post(id: Long): WpPost {
        val url = restBase.newBuilder()
            .addPathSegment("posts")
            .addPathSegment(id.toString())
            .build()
        return SakuJson.decodeFromString(client.getText(url))
    }

    /**
     * 独立页面（WordPress page，如站点「使用手册」/manual）。
     * 不存在返回 null；`_fields` 只取渲染所需三字段，减少流量。
     */
    suspend fun pageBySlug(slug: String): WpPost? {
        require(slug.isNotBlank()) { "slug 不能为空" }
        val url = restBase.newBuilder()
            .addPathSegment("pages")
            .addQueryParameter("slug", slug)
            .addQueryParameter("_fields", "id,title,content")
            .build()
        val pages: List<WpPost> = SakuJson.decodeFromString(client.getText(url))
        return pages.firstOrNull()
    }

    suspend fun categories(perPage: Int = 100): List<WpTerm> = terms("categories", perPage)

    suspend fun tags(perPage: Int = 100): List<WpTerm> = terms("tags", perPage)

    /** 文章评论（匿名可读）；order=asc 让对话按时间正序 */
    suspend fun comments(
        postId: Long,
        page: Int = 1,
        perPage: Int = 20,
    ): List<WpComment> {
        require(page >= 1) { "page 从 1 开始" }
        require(perPage in 1..100) { "per_page 取值范围 1..100（WordPress 限制）" }
        val url = restBase.newBuilder()
            .addPathSegment("comments")
            .addQueryParameter("post", postId.toString())
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", perPage.toString())
            .addQueryParameter("order", "asc")
            .addQueryParameter("orderby", "date")
            .build()
        return SakuJson.decodeFromString(client.getText(url))
    }

    private suspend fun terms(segment: String, perPage: Int): List<WpTerm> {
        require(perPage in 1..100) { "per_page 取值范围 1..100（WordPress 限制）" }
        val url = restBase.newBuilder()
            .addPathSegment(segment)
            .addQueryParameter("per_page", perPage.toString())
            .addQueryParameter("orderby", "count")
            .addQueryParameter("order", "desc")
            .build()
        return SakuJson.decodeFromString(client.getText(url))
    }
}
