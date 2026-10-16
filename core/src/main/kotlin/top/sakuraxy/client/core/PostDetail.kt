package top.sakuraxy.client.core

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** 文章页作者信息（头部头像行 / 作者卡共用来源：页面 HTML，2026-10-16 实测） */
data class PostHeader(
    /** 作者用户 ID（从 /author/N 链接或 avatar-id-N 读取；匿名态按钮无 data-pid，不依赖它） */
    val authorId: Long?,
    val authorName: String?,
    val authorAvatar: String?,
    /** 作者等级（LV10 → 10） */
    val authorLevel: Int?,
    /** 作者徽章名列表（title 属性） */
    val medals: List<String>,
    /** 发布/更新的绝对时间（title 属性，如「2026年07月10日 21:14发布」） */
    val dateTitle: String?,
    /** 相对时间文案（如「2个月前发布」） */
    val dateText: String?,
    val commentCount: Int?,
    val viewCount: Int?,
    val likeCount: Int?,
    /** 页内 window._win.post_action_nonce（点赞/收藏/评论点赞/关注共用；匿名页也有） */
    val postActionNonce: String?,
    /** 评论表单 #commentform 里的 _wpnonce（提交评论用；与 post_action_nonce 是两回事） */
    val commentFormNonce: String?,
    /** 当前用户是否已关注作者（按钮文案「已关注」；匿名/未关注为 false） */
    val following: Boolean,
)

/** 点赞/收藏按钮状态（锚点 actived class = 当前用户已操作） */
data class PostActions(
    val likeCount: Int?,
    val liked: Boolean,
    val favoriteCount: Int?,
    val favorited: Boolean,
    /** 收藏按钮是否要求登录（匿名态站点给 signin-loader；点赞匿名可点） */
    val favoriteRequiresLogin: Boolean,
)

/** 评分组件（.star-score-content-box；含每个星级的分布） */
data class StarRating(
    /** 平均分原文（"0.0"） */
    val average: String?,
    val votes: Int?,
    /** 5 → 1 星的人数分布 */
    val distribution: List<Pair<Int, Int>>,
    /** 当前用户已评分（站点 JS 才置 true；服务端渲染通常 false，重复评由服务端拒绝） */
    val rated: Boolean,
    /** 提交评分用的 nonce（页内 var zibStarRating 的 nonce） */
    val nonce: String?,
)

/** 已购下载按钮（pay-download 链接，解析后跳网盘） */
data class PayDownload(val label: String, val url: String)

/**
 * 付费资源形态（2026-10-16 实测）：
 * `Download` = 旧的网盘下载按钮型；`Images` = 新型「付费图片」，付费后给的是图片
 * （如云盘二维码截图），站点靠用户自己扫码获得文件。
 * 判定依据在 `#posts-pay` 盒内的图标 class / 图库结构（盒外有同名元素，不能全局找）。
 */
enum class PayKind { Download, Images }

/** 付费图片（已购后由站点渲染；label 为角标如「1/2」） */
data class PayImage(val url: String, val label: String?)

/**
 * 文章付费区（#posts-pay，2026-10-16 实测多种形态）。
 * 注意侧栏另有 pay-widget 同名样式，解析锚定 id=posts-pay。
 */
sealed interface PayBox {
    /** 免费文章：无付费区 */
    data object Free : PayBox

    /** 未购买：积分价 + VIP 价提示 + 已售 + 「立即购买」（收银台弹窗） */
    data class Unpaid(
        /** order-type-N 的 N（2=积分支付） */
        val orderType: Int?,
        val price: String?,
        val unit: String?,
        val soldText: String?,
        /** VIP 价提示原文（如「黄金会员 1」「钻石会员 免费」） */
        val vipHints: List<String>,
        /** 页面上是否有收银台入口（a.cashier-link） */
        val hasCashier: Boolean,
        val kind: PayKind = PayKind.Download,
        /** 图片型的付款前预览（站点自己给的模糊封面；购买前绝不显示原图） */
        val teaserImageUrl: String? = null,
        /** 图片型提示原文，如「共2张图片 请付费后查看」 */
        val hintText: String? = null,
    ) : PayBox

    /** 已购买：订单信息 + 资源（下载按钮或付费图片，二选一） */
    data class Paid(
        val orderType: Int?,
        val orderNo: String?,
        val payTime: String?,
        val payAmount: String?,
        val soldText: String?,
        val downloads: List<PayDownload>,
        val kind: PayKind = PayKind.Download,
        /** 图片型资源：站点渲染给已购用户的图片（网盘二维码截图） */
        val images: List<PayImage> = emptyList(),
    ) : PayBox
}

/** 作者卡里的「TA 的文章」横滑条目 */
data class MiniPost(
    val url: String,
    val postId: Long?,
    val title: String,
    val coverUrl: String?,
    val dateText: String?,
    /** 浏览量原文（可能是「1.1W+」） */
    val views: String?,
)

/** 文章底部作者卡（.user-card.author） */
data class PostAuthorCard(
    val authorId: Long?,
    val name: String?,
    val avatarUrl: String?,
    /** 身份徽章（如「钻石会员」） */
    val vipBadge: String?,
    val level: Int?,
    val medals: List<String>,
    /** 四项统计原文（「112」「9W+」），比文章数/评论数/粉丝数/人气值 */
    val postCount: String?,
    val commentCount: String?,
    val followerCount: String?,
    val popularity: String?,
    val desc: String?,
    val recentPosts: List<MiniPost>,
)

/** 一条评论（页面 HTML 解析；REST 没有点赞数/等级/激活态） */
data class PostComment(
    val id: Long,
    val authorId: Long?,
    val authorName: String?,
    val avatarUrl: String?,
    val authorLevel: Int?,
    /** 评论正文 HTML（渲染时转纯文本） */
    val contentHtml: String,
    /** 绝对时间（title 属性） */
    val timeTitle: String?,
    /** 相对时间（「5个月前」） */
    val timeText: String?,
    val likeCount: Int?,
    val liked: Boolean,
    /** 父评论 id（回复楼层）；顶层为 null */
    val parentId: Long?,
    /** 嵌套层级（顶层 0） */
    val depth: Int,
)

/** 评论列表一页（分页地址形如 /{post}?cpage=N） */
data class PostCommentPage(
    val comments: List<PostComment>,
    val currentPage: Int?,
    /** 下一页地址（无下一页为 null） */
    val nextPageUrl: String?,
)

/** 文章详情页解析结果（一次页面请求的全部可复用数据） */
data class PostDetail(
    val header: PostHeader,
    val actions: PostActions,
    /** 无评分组件（插件未启用）时为 null */
    val rating: StarRating?,
    val payBox: PayBox,
    /** 无作者卡（如匿名页也可能有；解析失败）时为 null */
    val authorCard: PostAuthorCard?,
    /** 首屏评论（含分页信息；无评论区时为 null） */
    val commentPage: PostCommentPage?,
    /** 面包屑分类路径（不含「首页」「正文」） */
    val breadcrumb: List<SiteParser.CardTerm>,
    val tags: List<String>,
)

/**
 * 文章详情页解析（纯函数，离线可测）。
 *
 * 全部选择器依据 2026-10-16 四份真机样本实测：
 * 登录未购（35711）/ 登录已购（26463）/ 匿名（10879）/ 多评论（7634）。
 * 站点为深度定制 zibll，字段尽量可空、失败不抛异常。
 */
object PostDetailParser {

    private val NONCE_RE = Regex("""post_action_nonce\s*:\s*['"]([^'"]+)['"]""")
    private val STAR_NONCE_RE = Regex("""zibStarRating\s*=\s*\{.*?"nonce"\s*:\s*"([^"]+)"""", RegexOption.DOT_MATCHES_ALL)
    private val LEVEL_RE = Regex("""\d+""")
    private val AUTHOR_ID_RE = Regex("""/author/(\d+)""")
    private val COMMENT_ID_RE = Regex("""comment-(\d+)""")
    private val ORDER_TYPE_RE = Regex("""order-type-(\d+)""")

    /** 解析文章页（失败字段缺失而非抛异常） */
    fun parsePostDetail(html: String): PostDetail {
        val doc = Jsoup.parse(html, Site.BASE_URL)
        val article = doc.selectFirst("article.article")
        val breadcrumb = doc.selectFirst("ul.breadcrumb")?.select("li a")?.mapNotNull { a ->
            val text = a.text().trim()
            val href = a.absUrl("href")
            if (text.isBlank() || text == "首页" || text == "正文" || href.isBlank()) null
            else SiteParser.CardTerm(text, href)
        } ?: emptyList()
        val tags = (article ?: doc).select("a[href*=/tag/]").mapNotNull { a ->
            a.text().trim().removePrefix("#").trim().takeIf { it.isNotBlank() }
        }.distinct()
        return PostDetail(
            header = parseHeader(doc),
            actions = parseActions(doc),
            rating = parseRating(doc),
            payBox = parsePayBox(doc),
            authorCard = parseAuthorCard(doc),
            commentPage = parseCommentList(html),
            breadcrumb = breadcrumb,
            tags = tags,
        )
    }

    // ---------- 头部 ----------

    private fun parseHeader(doc: org.jsoup.nodes.Document): PostHeader {
        val header = doc.selectFirst("article .article-header")
        val avatarLink = header?.selectFirst(".article-avatar a[href*=/author/]")
        val authorId = avatarLink?.absUrl("href")
            ?.let { AUTHOR_ID_RE.find(it)?.groupValues?.get(1)?.toLongOrNull() }
            ?.takeIf { it > 0 }
            ?: header?.selectFirst("img[class*=avatar-id-]")?.className()
                ?.let { Regex("""avatar-id-(\d+)""").find(it)?.groupValues?.get(1)?.toLongOrNull() }
                ?.takeIf { it > 0 }

        val nameEl = header?.selectFirst("a.display-name")
        val level = header?.selectFirst("img[alt^=等级-]")?.let { img ->
            LEVEL_RE.find(img.attr("title").ifBlank { img.attr("alt") })?.value?.toIntOrNull()
        }
        val medals = header?.select("img.medal-icon")
            ?.mapNotNull { it.attr("title").ifBlank { null } }?.distinct() ?: emptyList()

        // 时间 title 有「发布」也有「更新」两种（26463 实测为“更新”），两种都兜
        val timeSpan = header?.selectFirst("span[title*=发布]") ?: header?.selectFirst("span[title*=更新]")

        val metas = header?.selectFirst(".post-metas")
        val commentCount = metas?.selectFirst(".meta-comm")?.text()?.trim()?.toIntOrNull()
        val viewCount = metas?.selectFirst(".meta-view")?.text()?.trim()?.toIntOrNull()
        val likeCount = metas?.selectFirst(".meta-like")?.text()?.trim()?.toIntOrNull()

        return PostHeader(
            authorId = authorId,
            authorName = nameEl?.text()?.trim()?.ifBlank { null },
            authorAvatar = header?.selectFirst(".article-avatar img.avatar")?.let { SiteParser.imageUrl(it) },
            authorLevel = level,
            medals = medals,
            dateTitle = timeSpan?.attr("title")?.ifBlank { null },
            dateText = timeSpan?.text()?.trim()?.ifBlank { null },
            commentCount = commentCount,
            viewCount = viewCount,
            likeCount = likeCount,
            postActionNonce = NONCE_RE.find(doc.outerHtml())?.groupValues?.get(1),
            commentFormNonce = doc.selectFirst("#commentform input[name=_wpnonce]")?.attr("value")
                ?.ifBlank { null },
            following = header?.selectFirst("a[data-action=follow_user]")?.text()
                ?.contains("已关注") == true,
        )
    }

    // ---------- 点赞 / 收藏 ----------

    private fun parseActions(doc: org.jsoup.nodes.Document): PostActions {
        val like = doc.selectFirst("a.action-like")
        val fav = doc.selectFirst("a.action-favorite")
        return PostActions(
            likeCount = like?.selectFirst("count")?.text()?.trim()?.toIntOrNull() ?: if (like != null) 0 else null,
            liked = like?.hasClass("actived") == true,
            favoriteCount = fav?.selectFirst("count")?.text()?.trim()?.toIntOrNull() ?: if (fav != null) 0 else null,
            favorited = fav?.hasClass("actived") == true,
            favoriteRequiresLogin = fav?.hasClass("signin-loader") == true,
        )
    }

    // ---------- 评分 ----------

    private fun parseRating(doc: org.jsoup.nodes.Document): StarRating? {
        val box = doc.selectFirst(".star-score-content-box") ?: doc.selectFirst(".star-score-widget")
            ?: return null
        val distribution = box.select(".distribution-item").mapNotNull { item ->
            val star = item.attr("data-rating").toIntOrNull() ?: return@mapNotNull null
            val count = item.selectFirst(".distribution-count span")?.text()?.trim()?.toIntOrNull() ?: 0
            star to count
        }
        return StarRating(
            average = box.selectFirst(".star-score-average")?.attr("data-average")?.ifBlank { null }
                ?: box.selectFirst(".star-score-average")?.text()?.trim()?.ifBlank { null },
            votes = box.selectFirst(".star-score-votes span")?.attr("data-count")?.toIntOrNull()
                ?: box.selectFirst(".star-score-votes span")?.text()?.trim()?.toIntOrNull(),
            distribution = distribution,
            // 交互星标在侧栏 .star-score-widget（内容区的 box 只有统计）；rated 从整体读
            rated = doc.selectFirst(".star-score-stars[data-rated=true], .star-score-widget[data-rated=true]") != null,
            nonce = STAR_NONCE_RE.find(doc.outerHtml())?.groupValues?.get(1),
        )
    }

    // ---------- 付费区 ----------

    private fun parsePayBox(doc: org.jsoup.nodes.Document): PayBox {
        val box = doc.selectFirst("#posts-pay") ?: return PayBox.Free
        val orderType = ORDER_TYPE_RE.find(box.className())?.groupValues?.get(1)?.toIntOrNull()
        val soldText = box.selectFirst("badge.img-badge")?.text()?.trim()?.ifBlank { null }
        // 形态判定：盒内 fa-file-image-o 图标（付费图片）或图库结构；旧下载型是 fa-download。
        // 已购图片型的 #posts-pay 里没有 .pay-tag（那是未购/侧栏的），不能用它判定。
        val kind = if (box.selectFirst("i.fa-file-image-o") != null ||
            box.selectFirst(".imgbox-container") != null
        ) PayKind.Images else PayKind.Download
        return if (box.hasClass("paid-box")) {
            val info = box.selectFirst(".paid-info")
            // 行结构：<div class="flex jsb"><span>订单号</span><span><a…>123</a></span></div>
            // 取值用直接子 span（值 span 内可能还嵌套 span.pay-mark，不能用后代 last）
            fun row(label: String): String? = info?.select("div.flex.jsb")?.firstOrNull {
                it.selectFirst("> span")?.text()?.trim() == label
            }?.select("> span")?.lastOrNull()?.text()?.trim()?.ifBlank { null }
            PayBox.Paid(
                orderType = orderType,
                orderNo = row("订单号"),
                payTime = row("支付时间"),
                payAmount = row("支付金额"),
                soldText = soldText,
                downloads = box.select("a[href*=/pay-download/]").mapNotNull { a ->
                    val url = a.absUrl("href").ifBlank { null } ?: return@mapNotNull null
                    PayDownload(label = a.text().trim().ifBlank { "下载" }, url = url)
                },
                kind = kind,
                images = if (kind == PayKind.Images) parsePayImages(box) else emptyList(),
            )
        } else {
            val priceBox = box.selectFirst(".price-box")
            val vipHints = box.select("span.vip-price").mapNotNull { el ->
                val clone = el.clone().apply { select("svg, i").remove() }
                clone.text().trim().replace(Regex("\\s+"), " ").ifBlank { null }
            }
            PayBox.Unpaid(
                orderType = orderType,
                price = priceBox?.selectFirst("b")?.text()?.trim()?.ifBlank { null },
                unit = priceBox?.select("span")?.lastOrNull()?.text()?.trim()?.ifBlank { null },
                soldText = soldText,
                vipHints = vipHints,
                hasCashier = box.selectFirst("a.cashier-link") != null,
                kind = kind,
                // 预览图 = 站点给的模糊封面（.pay-thumb）；购买前页面上本来就只有它
                teaserImageUrl = box.selectFirst(".pay-thumb img")?.let { SiteParser.imageUrl(it) },
                hintText = box.selectFirst(".pay-thumb .abs-center")?.text()?.trim()
                    ?.replace(Regex("\\s+"), " ")?.ifBlank { null },
            )
        }
    }

    /** 付费图片图库（.imgbox-container 内的轮播项；原图取 data-full-url，缺省回退懒加载地址） */
    private fun parsePayImages(box: Element): List<PayImage> =
        box.select(".imgbox-container .swiper-slide").mapNotNull { slide ->
            val img = slide.selectFirst("img") ?: return@mapNotNull null
            val full = img.attr("data-full-url").takeIf { it.startsWith("http") }
                ?: SiteParser.imageUrl(img) ?: return@mapNotNull null
            PayImage(
                url = full,
                label = slide.selectFirst("badge")?.text()?.trim()?.ifBlank { null },
            )
        }

    // ---------- 作者卡 ----------

    private fun parseAuthorCard(doc: org.jsoup.nodes.Document): PostAuthorCard? {
        val card = doc.selectFirst(".user-card.author") ?: return null
        val authorId = card.selectFirst("a[href*=/author/]")?.absUrl("href")
            ?.let { AUTHOR_ID_RE.find(it)?.groupValues?.get(1)?.toLongOrNull() }
        val level = card.selectFirst("img[alt^=等级-]")?.let { img ->
            LEVEL_RE.find(img.attr("title").ifBlank { img.attr("alt") })?.value?.toIntOrNull()
        }
        val tag = card.selectFirst(".author-tag")
        val recentPosts = card.select(".more-posts .swiper-slide a[href]").mapNotNull { a ->
            val url = a.absUrl("href").ifBlank { null } ?: return@mapNotNull null
            val items = a.select("item")
            MiniPost(
                url = url,
                postId = SiteParser.postIdFromUrl(url),
                title = a.selectFirst(".graphic-text.text-ellipsis")?.text()?.trim().orEmpty(),
                coverUrl = a.selectFirst("img")?.let { SiteParser.imageUrl(it) },
                dateText = items.firstOrNull()?.text()?.trim()?.ifBlank { null },
                views = items.firstOrNull { it.hasClass("pull-right") }?.text()?.trim()?.ifBlank { null },
            )
        }
        return PostAuthorCard(
            authorId = authorId,
            name = card.selectFirst("a.display-name")?.text()?.trim()?.ifBlank { null },
            avatarUrl = card.selectFirst(".user-avatar img.avatar")?.let { SiteParser.imageUrl(it) },
            vipBadge = card.selectFirst("img.avatar-badge")?.attr("title")?.ifBlank { null },
            level = level,
            medals = card.select("img.medal-icon").mapNotNull { it.attr("title").ifBlank { null } }.distinct(),
            postCount = tag?.selectFirst(".tag-posts")?.text()?.trim()?.ifBlank { null },
            commentCount = tag?.selectFirst(".tag-comment")?.text()?.trim()?.ifBlank { null },
            followerCount = tag?.selectFirst(".tag-follow")?.text()?.trim()?.ifBlank { null },
            popularity = tag?.selectFirst(".tag-view")?.text()?.trim()?.ifBlank { null },
            desc = card.selectFirst(".user-desc")?.text()?.trim()?.ifBlank { null },
            recentPosts = recentPosts,
        )
    }

    // ---------- 评论 ----------

    /** 解析评论列表一页（li.comment 结构；含嵌套回复与 cpage 分页） */
    fun parseCommentList(html: String): PostCommentPage? {
        val doc = Jsoup.parse(html, Site.BASE_URL)
        val list = doc.selectFirst(".commentlist") ?: return null
        val comments = list.select("li.comment").mapNotNull { li -> parseComment(li) }

        val pagenav = doc.select(".pagenav").firstOrNull { it.selectFirst("a[href*=cpage]") != null }
        val currentPage = pagenav?.selectFirst("span.page-numbers.current")?.text()?.trim()?.toIntOrNull()
            ?: pagenav?.selectFirst("input.jump-input")?.attr("current")?.toIntOrNull()
        val nextPageUrl = pagenav?.selectFirst("a.next.page-numbers")?.absUrl("href")
            ?.substringBefore("#")?.ifBlank { null }

        return PostCommentPage(comments = comments, currentPage = currentPage, nextPageUrl = nextPageUrl)
    }

    private fun parseComment(li: Element): PostComment? {
        // 骨架占位条目没有 comment-N id / 作者链接：过滤（与作者页粉丝卡同一教训）
        val id = COMMENT_ID_RE.find(li.id())?.groupValues?.get(1)?.toLongOrNull() ?: return null
        val head = li.selectFirst(".comment-header") ?: return null
        val authorLink = head.selectFirst("a[href*=/author/]")
        val authorId = authorLink?.absUrl("href")
            ?.let { AUTHOR_ID_RE.find(it)?.groupValues?.get(1)?.toLongOrNull() }
        val parentId = li.parents().firstOrNull { it.tagName() == "li" && it.hasClass("comment") }
            ?.let { COMMENT_ID_RE.find(it.id())?.groupValues?.get(1)?.toLongOrNull() }
        val depth = li.parents().count { it.tagName() == "li" && it.hasClass("comment") }

        val like = head.selectFirst("a.action-comment-like")
        val meta = li.selectFirst(".comt-meta")
        val time = meta?.selectFirst(".comt-author")
        return PostComment(
            id = id,
            authorId = authorId,
            authorName = head.selectFirst("name a")?.text()?.trim()?.ifBlank { null }
                ?: head.selectFirst("a.text-ellipsis")?.text()?.trim()?.ifBlank { null },
            avatarUrl = head.selectFirst("img.avatar")?.let { SiteParser.imageUrl(it) },
            authorLevel = head.selectFirst("img[alt^=等级-]")?.let { img ->
                LEVEL_RE.find(img.attr("title").ifBlank { img.attr("alt") })?.value?.toIntOrNull()
            },
            contentHtml = li.selectFirst(".comment-content")?.html().orEmpty(),
            timeTitle = time?.attr("title")?.ifBlank { null },
            timeText = time?.text()?.trim()?.ifBlank { null },
            likeCount = like?.selectFirst("count")?.text()?.trim()?.toIntOrNull()
                ?: if (like != null) 0 else null,
            liked = like?.hasClass("actived") == true,
            parentId = parentId,
            depth = depth,
        )
    }
}
