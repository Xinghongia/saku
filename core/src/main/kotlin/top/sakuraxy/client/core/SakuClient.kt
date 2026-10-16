package top.sakuraxy.client.core

/**
 * 客户端总入口：持有会话与 HTTP 客户端，向上层暴露各接口。
 * 上层（app）不应直接构造 OkHttpClient，统一从这里取，保证 cookie 会话一致。
 *
 * - [rest]：WordPress REST（结构化数据：正文/分类/评论）
 * - [site]：站点页面解析（主题数据：卡片封面/徽章/统计/排序/分页）
 * - [auth]：登录（admin-ajax 流程）
 * - [users]：用户中心（总览/签到/资料/资产等，登录态）
 * - [author]：作者主页（卡片/评论/粉丝 + 关注）
 * - [messages]：消息中心与私信
 * - [newPost]：投稿（/newposts 草稿/发布）
 */
class SakuClient(val session: Session = Session()) {
    val http = createHttpClient(session)
    val rest = WpApi(http)
    val site = SiteApi(http)
    val auth = AuthApi(http)
    val users = UserCenterApi(http)
    val author = AuthorApi(http)
    val messages = MessageApi(http)
    val newPost = NewPostApi(http)
}
