package top.sakuraxy.client.core

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** 作者主页信息（他人页含关注/私信按钮；自己的页面没有） */
data class AuthorInfo(
    val userId: Long?,
    val displayName: String?,
    val avatarUrl: String?,
    val coverUrl: String?,
    val level: Int?,
    val description: String?,
    /** 文章/评论/收藏/粉丝统计（含 tab 名） */
    val stats: List<UserStat>,
    /** 无关注按钮 = 自己 */
    val isSelf: Boolean,
    val following: Boolean,
    /** 关注数（按钮 count 文本里的数字，可能为空） */
    val followCount: String?,
    /** 页内 window._win.post_action_nonce（关注等 action.php 写操作用，现取现用） */
    val postActionNonce: String?,
    val canChat: Boolean,
)

/** 作者页的评论条目（评论 tab） */
data class AuthorComment(
    val text: String?,
    /** 跳转链接（文章 + #comment-xx） */
    val url: String?,
    /** 相对时间原文（"4天前"） */
    val timeText: String?,
    /** 绝对时间（左侧元素的 title 属性） */
    val time: String?,
    /** 回复的对象（@某人） */
    val replyTo: String?,
    val postTitle: String?,
    val postUrl: String?,
)

/** 粉丝 / 关注列表里的用户卡 */
data class AuthorUserCard(
    val userId: Long?,
    val name: String?,
    val avatarUrl: String?,
    val level: Int?,
    val description: String?,
    val following: Boolean,
)

/** 粉丝 / 关注列表 */
data class AuthorFollowList(
    val followersUrl: String?,
    val followingUrl: String?,
    val users: List<AuthorUserCard>,
)

object AuthorPageParser {

    private val NONCE_RE = Regex("""post_action_nonce\s*:\s*['"]([^'"]+)['"]""")
    private val LEVEL_RE = Regex("""LV(\d+)""")
    private val USER_PATH_RE = Regex("""/author/(\d+)""")

    /** 解析作者页头部（自己/他人页面同一套结构） */
    fun parseAuthorInfo(html: String): AuthorInfo {
        val doc = Jsoup.parse(html, Site.BASE_URL)
        val header = doc.selectFirst(".author-header") ?: doc

        val followBtn = header.selectFirst("a[data-action=follow_user]")
        val userId = followBtn?.attr("data-pid")?.toLongOrNull()
            ?: header.select("img[class*=avatar-id-]").firstOrNull()?.className()
                ?.let { Regex("""avatar-id-(\d+)""").find(it)?.groupValues?.get(1)?.toLongOrNull() }

        val avatarUrl = header.select("img.avatar").firstNotNullOfOrNull { it.lazyUrl() }
        val coverUrl = header.selectFirst("img.user-cover")?.lazyUrl()
        val level = header.selectFirst("img[title^=LV]")?.attr("title")
            ?.let { LEVEL_RE.find(it)?.groupValues?.get(1)?.toIntOrNull() }
        val displayName = header.selectFirst("a.display-name")?.text()?.trim()?.ifBlank { null }

        // 统计（文章/评论/收藏/粉丝）：tab 导航里的 4 个链接 + count
        val stats = doc.select("ul.tab-nav-theme a[href*=tab=]").mapNotNull { a ->
            val href = a.absUrl("href")
            val tab = href.substringAfter("tab=", "").substringBefore("&")
                .ifBlank { a.attr("href").substringAfter("tab=", "") }
            val label = a.clone().apply { select("count").remove() }.text().trim()
                .ifBlank { return@mapNotNull null }
            val count = a.selectFirst("count")?.text()?.trim().orEmpty()
            UserStat(label = label, count = count, tab = tab)
        }.distinctBy { it.tab }

        val following = followBtn?.text()?.contains("已关注") == true
        val followCount = followBtn?.selectFirst("count")?.text()
            ?.let { Regex("""(\d+)""").find(it)?.groupValues?.get(1) }

        return AuthorInfo(
            userId = userId,
            displayName = displayName,
            avatarUrl = avatarUrl,
            coverUrl = coverUrl,
            level = level,
            description = header.selectFirst("div.desc.muted-2-color, div.mt6.desc")?.text()?.trim()?.ifBlank { null },
            stats = stats,
            isSelf = followBtn == null,
            following = following,
            followCount = followCount,
            postActionNonce = NONCE_RE.find(html)?.groupValues?.get(1),
            canChat = header.select("a[data-remote*=private_window_modal]").isNotEmpty(),
        )
    }

    /** 评论 tab 的条目列表（.posts-item.no_margin；排除 ajax 占位骨架） */
    fun parseComments(html: String): List<AuthorComment> {
        val doc = Jsoup.parse(html, Site.BASE_URL)
        return doc.select("div.posts-item.no_margin").mapNotNull { item ->
            // 加载骨架里的占位条目（.placeholder）不是真实评论
            if (item.selectFirst(".placeholder") != null) return@mapNotNull null
            val contentLink = item.selectFirst(".comment-content a") ?: return@mapNotNull null
            val left = item.selectFirst(".author-set-left")
            val context = item.selectFirst(".muted-2-color")
            val replyTo = context?.selectFirst("span")?.text()?.trim()?.removePrefix("@")?.ifBlank { null }
            val postLink = item.select("div.muted-2-color a[href]").lastOrNull()
            AuthorComment(
                text = contentLink?.text()?.trim()?.ifBlank { null },
                url = contentLink?.absUrl("href")?.ifBlank { null },
                timeText = left?.text()?.trim()?.ifBlank { null },
                time = left?.attr("title")?.ifBlank { null },
                replyTo = replyTo,
                postTitle = postLink?.text()?.trim()?.ifBlank { null },
                postUrl = postLink?.absUrl("href")?.ifBlank { null },
            )
        }
    }

    /** 粉丝 / 关注 tab：两个子列表链接 + 当前列表的用户卡 */
    fun parseFollowList(html: String): AuthorFollowList {
        val doc = Jsoup.parse(html, Site.BASE_URL)
        val subNav = doc.select("ul.list-inline a[href*=action=author_follow]")
        fun subUrl(type: String) = subNav.firstOrNull { it.absUrl("href").contains("type=$type") }
            ?.absUrl("href")?.ifBlank { null }

        val users = doc.select("div.author-minicard").map { card ->
            val nameLink = card.selectFirst("a.display-name")
            val userId = nameLink?.absUrl("href")
                ?.let { USER_PATH_RE.find(it)?.groupValues?.get(1)?.toLongOrNull() }
            val level = card.selectFirst("img[title^=LV]")?.attr("title")
                ?.let { LEVEL_RE.find(it)?.groupValues?.get(1)?.toIntOrNull() }
            AuthorUserCard(
                userId = userId,
                name = nameLink?.text()?.trim()?.ifBlank { null },
                avatarUrl = card.selectFirst("img.avatar")?.lazyUrl(),
                level = level,
                description = card.selectFirst("dd")?.text()?.trim()?.ifBlank { null },
                following = card.selectFirst("a[data-action=follow_user]")?.text()
                    ?.contains("已关注") == true,
            )
        }
        return AuthorFollowList(
            followersUrl = subUrl("followed"),
            followingUrl = subUrl("follow"),
            users = users,
        )
    }

    private fun Element.lazyUrl(): String? =
        absUrl("data-src").ifBlank { null } ?: absUrl("src").ifBlank { null }
}
