package top.sakuraxy.client.core

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/** 作者主页一次加载的结果（tab 决定哪部分有内容） */
data class AuthorPage(
    val info: AuthorInfo,
    /** 文章/收藏 tab 的卡片列表 */
    val cards: List<SiteParser.PostCard> = emptyList(),
    val listPage: SiteParser.ListPage? = null,
    /** 评论 tab 的评论列表 */
    val comments: List<AuthorComment> = emptyList(),
    /** 粉丝/关注 tab 的用户列表 */
    val followList: AuthorFollowList? = null,
)

/**
 * 作者主页（/author/{id}）：与用户中心同款头部 + 四个标签
 * （文章/评论/收藏/粉丝）。关注操作走主题的 action.php（_wpnonce 现取现用）。
 */
class AuthorApi(
    private val client: OkHttpClient,
    private val baseUrl: String = Site.BASE_URL,
) {
    /**
     * 加载作者主页。[tab] 为 post/comment/favorite/follow（null = 站点默认 post），
     * [page] 为 1 起的分页（站点分页在路径：/author/{id}/page/{n}）。
     */
    suspend fun page(authorId: Long, tab: String? = null, page: Int = 1): AuthorPage {
        val builder = "$baseUrl/author/$authorId".toHttpUrl().newBuilder()
        if (page > 1) builder.addPathSegment("page").addPathSegment(page.toString())
        if (!tab.isNullOrBlank()) builder.addQueryParameter("tab", tab)
        val html = client.getText(builder.build())

        val info = AuthorPageParser.parseAuthorInfo(html)
        return when (tab) {
            "comment" -> AuthorPage(info = info, comments = AuthorPageParser.parseComments(html))
            "follow" -> AuthorPage(info = info, followList = AuthorPageParser.parseFollowList(html))
            else -> {
                val listPage = SiteParser.parseListPage(html)
                AuthorPage(info = info, cards = listPage.cards, listPage = listPage)
            }
        }
    }

    /** 粉丝 / 关注子列表（粉丝 tab 内的两个切换链接，返回 HTML 片段） */
    suspend fun followList(url: String): AuthorFollowList =
        AuthorPageParser.parseFollowList(client.getText(url.toHttpUrl()))

    /**
     * 关注 / 取关（网页 [data-action=follow_user] 的等价物）。
     * 站点响应 JSON 含 action（add/remove）与 cuont（关注数）。
     * [nonce] 来自当前作者页的 post_action_nonce，调用前现取。
     */
    suspend fun follow(authorId: Long, nonce: String): AjaxResult {
        val form = FormBody.Builder()
            .add("type", "follow_user")
            .add("key", "follow_user")
            .add("pid", authorId.toString())
            .add("_wpnonce", nonce)
            .build()
        val response = client.postForm(Site.ACTION_URL.toHttpUrl(), form)
        return parseAjaxResponse(response.status, response.body)
    }
}
