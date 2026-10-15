package top.sakuraxy.client.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** WordPress REST 的 {rendered: "..."} 包装 */
@Serializable
data class Rendered(val rendered: String = "")

@Serializable
data class WpPost(
    val id: Long,
    val date: String? = null,
    val link: String? = null,
    val title: Rendered = Rendered(),
    val content: Rendered? = null,
    val excerpt: Rendered? = null,
    @SerialName("featured_media") val featuredMedia: Long = 0L,
    val sticky: Boolean = false,
    val categories: List<Long> = emptyList(),
    val tags: List<Long> = emptyList(),
    /** 请求带 _embed 时的内嵌数据（列表缩略图用） */
    @SerialName("_embedded") val embedded: Embedded? = null,
)

@Serializable
data class Embedded(
    @SerialName("wp:featuredmedia") val featuredMedia: List<WpMedia>? = null,
)

@Serializable
data class WpMedia(
    val id: Long = 0L,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("alt_text") val altText: String? = null,
)

/** WordPress 评论（REST 匿名可读；发表评论属登录后写操作，另行实现） */
@Serializable
data class WpComment(
    val id: Long,
    val post: Long = 0L,
    val parent: Long = 0L,
    val date: String? = null,
    @SerialName("author_name") val authorName: String = "",
    @SerialName("author_avatar_urls") val authorAvatarUrls: Map<String, String> = emptyMap(),
    val content: Rendered = Rendered(),
) {
    /** 取最大尺寸头像（WP 默认提供 24/48/96） */
    val avatarUrl: String?
        get() = authorAvatarUrls["96"] ?: authorAvatarUrls["48"] ?: authorAvatarUrls.values.firstOrNull()
}

/** 分类与标签共用同一形状 */
@Serializable
data class WpTerm(
    val id: Long,
    val name: String = "",
    val slug: String = "",
    val count: Int = 0,
    val parent: Long = 0L,
    val link: String? = null,
)

/**
 * 站点响应的 JSON 解析器。
 * WordPress 返回的字段远多于客户端所需，未知字段一律忽略；
 * 部分字段在样本里可能为空字符串或缺失，用 coerceInputValues 兜底。
 */
val SakuJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}
