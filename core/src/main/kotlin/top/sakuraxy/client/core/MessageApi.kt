package top.sakuraxy.client.core

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** 消息中心的标签页（未读消息/文章评论/点赞喜欢/私信消息/系统通知） */
data class MsgTab(val key: String, val label: String, val unread: Boolean)

/** 消息中心一条消息（文章评论/点赞喜欢/系统通知等） */
data class MessageItem(
    val id: String?,
    /** 消息正文（可能被截断） */
    val text: String?,
    val avatarUrl: String?,
    val timeText: String?,
    val time: String?,
    val unread: Boolean,
    val url: String?,
)

/** 私信会话列表项 */
data class ChatSummary(
    val userId: Long?,
    val name: String?,
    val avatarUrl: String?,
    val lastText: String?,
    val timeText: String?,
    val time: String?,
    /** 打开会话的 ajax 地址（含 nonce，现取现用） */
    val openUrl: String?,
)

/** 消息中心解析结果（当前激活标签的内容） */
data class MessageCenter(
    val tabs: List<MsgTab>,
    val activeTab: String?,
    val messages: List<MessageItem>,
    val chats: List<ChatSummary>,
    /** 是否有「暂无消息」空态 */
    val empty: Boolean,
)

/** 私信对话里的一条气泡 */
data class ChatMessage(
    /** true = 自己发出（右侧） */
    val mine: Boolean,
    val text: String?,
    val avatarUrl: String?,
    val imageUrl: String?,
    /** 时间分组标题（"2026-10-16 12:46"，只出现在每组第一条） */
    val dateGroup: String?,
    val unread: Boolean,
)

/** 私信窗口（打开会话后的完整状态） */
data class ChatWindow(
    val peerName: String?,
    val peerAvatarUrl: String?,
    val messages: List<ChatMessage>,
    /** 发送表单：send_user/receive_user/action=send_private/send_private_nonce */
    val sendFields: Map<String, String>,
    /** 图片上传（user_upload_image + upload_image_nonce） */
    val imageUploadFields: Map<String, String>,
    /** 黑名单直链（含 nonce，现取现用） */
    val blacklistUrl: String?,
)

/**
 * 消息中心与私信（/message/ 页面 + private_window/private_window_modal 端点）。
 *
 * 2026-10-16 实测：消息中心是服务端渲染整页（?msg_center=xxx 渲染对应标签），
 * 私信窗口由 admin-ajax 返回 HTML 片段（含历史气泡 + 发送表单 nonce），
 * 发送/上传/黑名单均复刻网页自身的请求。
 */
object MessageParser {

    /** 消息中心页面：标签导航 + 当前标签内容 */
    fun parseCenter(html: String): MessageCenter {
        val doc = Jsoup.parse(html, Site.BASE_URL)
        val tabs = doc.select("ul.tab-nav-theme a[data-target^='#msg-tab-']").mapNotNull { a ->
            val key = a.attr("data-target").removePrefix("#msg-tab-").ifBlank { return@mapNotNull null }
            val label = a.clone().apply { select("img, svg, badge, span.badge").remove() }
                .text().trim().ifBlank { key }
            val unread = a.selectFirst("badge, span.badge") != null
            MsgTab(key = key, label = label, unread = unread)
        }.distinctBy { it.key }

        // 激活标签：以左侧导航的 li.active 为准，兜底看内容面板
        val activePane = doc.selectFirst(".tab-content .tab-pane.in.active, .tab-content .tab-pane.active")
        val activeTab = doc.selectFirst("ul.tab-nav-theme li.active a[data-target^='#msg-tab-']")
            ?.attr("data-target")?.removePrefix("#msg-tab-")?.ifBlank { null }
            ?: activePane?.id()?.removePrefix("msg-tab-")

        val messages = mutableListOf<MessageItem>()
        val chats = mutableListOf<ChatSummary>()
        doc.select("div.chat-lists").forEach { chat -> chats += parseChatSummary(chat) }
        doc.select("ul.msg-list").forEach { list -> messages += parseMessageItem(list) }

        // 空态：「暂无消息」提示块（loader 占位不算）
        val empty = doc.select("div.text-center.ajax-item").any { it.text().contains("暂无消息") }

        return MessageCenter(
            tabs = tabs,
            activeTab = activeTab,
            messages = messages.filter { it.text != null || it.url != null },
            chats = chats.filter { it.userId != null || it.name != null },
            empty = empty,
        )
    }

    private fun parseMessageItem(list: Element): MessageItem {
        val imgLink = list.selectFirst("li a.msg-img, li a.avatar-img, li .msg-img")
        val avatar = imgLink?.selectFirst("img") ?: list.selectFirst("li img.avatar")
        val dt = list.selectFirst("dt")
        val dd = list.selectFirst("dd")
        val timeEl = list.selectFirst("[title]")
        return MessageItem(
            id = list.attr("data-id").ifBlank { null },
            text = dt?.text()?.trim()?.ifBlank { null } ?: dd?.text()?.trim()?.ifBlank { null },
            avatarUrl = avatar?.lazyUrl(),
            timeText = dd?.selectFirst("span, i")?.text()?.trim()?.ifBlank { null }
                ?: dd?.text()?.trim()?.ifBlank { null },
            time = timeEl?.attr("title")?.ifBlank { null },
            unread = list.selectFirst("badge, .badge, .unread") != null,
            url = dt?.selectFirst("a[href]")?.absUrl("href")?.ifBlank { null }
                ?: list.attr("data-url").ifBlank { null },
        )
    }

    private fun parseChatSummary(chat: Element): ChatSummary {
        val link = chat.selectFirst("a[data-ajax], a[href]")
        val openUrl = link?.attr("data-ajax")?.ifBlank { null }
            ?: link?.absUrl("href")?.takeIf { it.contains("action=private_window") }
        val img = chat.selectFirst("img.avatar")
        val userId = img?.className()?.let { Regex("""avatar-id-(\d+)""").find(it)?.groupValues?.get(1)?.toLongOrNull() }
            ?: openUrl?.let { Regex("""receive_user=(\d+)""").find(it)?.groupValues?.get(1)?.toLongOrNull() }
        val timeEl = chat.selectFirst("[title]")
        return ChatSummary(
            userId = userId,
            name = chat.selectFirst("dt")?.text()?.trim()?.ifBlank { null },
            avatarUrl = img?.lazyUrl(),
            lastText = chat.selectFirst("dd")?.text()?.trim()?.ifBlank { null },
            timeText = chat.selectFirst("span.pull-right")?.text()?.trim()?.ifBlank { null },
            time = timeEl?.attr("title")?.ifBlank { null },
            openUrl = openUrl,
        )
    }

    /** 私信窗口（private_window / private_window_modal 的响应） */
    fun parseChatWindow(html: String): ChatWindow {
        val doc = Jsoup.parse(html, Site.BASE_URL)

        // 对方信息（弹窗头部的头像与名字）
        val header = doc.selectFirst(".private-window-header")
        val peerAvatar = header?.selectFirst("img.avatar")?.lazyUrl()
        val peerName = header?.selectFirst("div.text-center, span.display-name")
            ?.clone()?.apply { select("img, svg, a.dropdown-toggle, .dropdown").remove() }
            ?.text()?.trim()?.ifBlank { null }

        // 历史气泡（含日期分组）
        val messages = mutableListOf<ChatMessage>()
        doc.select("div.private-window-content .private-item").forEach { item ->
            val dateGroup = item.selectFirst("div.muted-3-color, div.text-center")?.text()?.trim()
                ?.takeIf { it.isNotBlank() }
            item.select("div.private-list").forEach { bubble ->
                val mine = bubble.hasClass("right")
                val content = bubble.selectFirst("span.private-content, .private-content")
                val imageUrl = content?.selectFirst("img")?.let {
                    it.absUrl("data-src").ifBlank { it.absUrl("src") }.ifBlank { null }
                }
                val text = content?.clone()?.apply { select("img, .readed-badge").remove() }
                    ?.text()?.trim()?.ifBlank { null }
                messages += ChatMessage(
                    mine = mine,
                    text = text,
                    avatarUrl = bubble.selectFirst("img.avatar")?.lazyUrl(),
                    imageUrl = imageUrl,
                    dateGroup = dateGroup,
                    unread = content?.selectFirst(".readed-badge") != null,
                )
            }
        }

        // 发送表单字段（含 send_private_nonce）
        val form = doc.selectFirst("form.from-private")
        val sendFields = linkedMapOf<String, String>()
        form?.select("input[type=hidden][name]")?.forEach { sendFields[it.attr("name")] = it.attr("value") }

        // 图片上传字段（data-name/data-value 形式）
        val imageFields = linkedMapOf<String, String>()
        form?.select("input[type=hidden][data-name]")?.forEach {
            imageFields[it.attr("data-name")] = it.attr("data-value")
        }

        val blacklistUrl = doc.selectFirst("a.ajax-blacklist[ajax-href]")?.attr("ajax-href")?.ifBlank { null }

        return ChatWindow(
            peerName = peerName,
            peerAvatarUrl = peerAvatar,
            messages = messages,
            sendFields = sendFields,
            imageUploadFields = imageFields,
            blacklistUrl = blacklistUrl,
        )
    }

    private fun Element.lazyUrl(): String? =
        absUrl("data-src").ifBlank { null } ?: absUrl("src").ifBlank { null }
}

/** 消息中心与私信（登录态） */
class MessageApi(
    private val client: OkHttpClient,
    private val baseUrl: String = Site.BASE_URL,
) {
    /** 消息中心某标签的整页 HTML（news/posts/like/private/system） */
    suspend fun centerHtml(tab: String?): String {
        val builder = "$baseUrl/message/".toHttpUrl().newBuilder()
        if (!tab.isNullOrBlank()) builder.addQueryParameter("msg_center", tab)
        return client.getText(builder.build())
    }

    /** 消息中心（解析后） */
    suspend fun center(tab: String? = null): MessageCenter =
        MessageParser.parseCenter(centerHtml(tab))

    /** 打开私信会话（弹窗端点，未登录/不允许时站点返回错误 HTML） */
    suspend fun openChat(userId: Long): ChatWindow {
        val url = Site.AJAX_URL.toHttpUrl().newBuilder()
            .addQueryParameter("action", "private_window_modal")
            .addQueryParameter("receive_user", userId.toString())
            .build()
        return MessageParser.parseChatWindow(client.getText(url))
    }

    /**
     * 发送私信文本。[window] 为当前会话（提供 send_private_nonce 等字段）。
     * 响应 JSON 的 html 字段为气泡片段；发送成功后建议重新拉取会话。
     */
    suspend fun sendPrivate(window: ChatWindow, text: String): AjaxResult {
        val fields = window.sendFields
        val action = fields["action"] ?: "send_private"
        val form = FormBody.Builder()
        fields.forEach { (k, v) -> if (k != "action") form.add(k, v) }
        form.add("receive", text)
        form.add("action", action)
        val response = client.postForm(Site.AJAX_URL.toHttpUrl(), form.build())
        return parseAjaxResponse(response.status, response.body)
    }

    /** 私信图片上传（返回图片 URL；字段名实测 image_upload + upload_image_nonce） */
    suspend fun uploadChatImage(window: ChatWindow, file: UploadFile): String? {
        val fields = linkedMapOf<String, String>()
        window.imageUploadFields.forEach { (k, v) -> fields[k] = v }
        val body = buildMultipartBody(fields, listOf("image_upload" to file))
        val response = client.postMultipart(Site.AJAX_URL.toHttpUrl(), body)
        val ok = parseAjaxResponse(response.status, response.body) as? AjaxResult.Ok ?: return null
        return ok.json["url"]?.toString()?.trim('"')
            ?: ok.json["data"]?.toString()?.trim('"')
    }

    /** 加入黑名单（站点自带的 ajax-href 直链，现取现用） */
    suspend fun blacklist(window: ChatWindow): AjaxResult {
        val url = window.blacklistUrl ?: return AjaxResult.Fail("缺少黑名单入口")
        val response = client.getTextRaw(url.toHttpUrl())
        return parseAjaxResponse(response.status, response.body)
    }
}
