package top.sakuraxy.client.core

/**
 * 站点常量。
 *
 * 本项目只访问这一个站点，且行为与网页等效；不绕过任何付费/积分/权限限制。
 */
object Site {
    const val BASE_URL: String = "https://www.sakuraxy.top"

    /** WordPress REST 入口 */
    const val REST_BASE: String = "$BASE_URL/wp-json/wp/v2"

    /** zibll 主题的 ajax 入口（登录、签到等） */
    const val AJAX_URL: String = "$BASE_URL/wp-admin/admin-ajax.php"
}
