package top.sakuraxy.client.core

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WpApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: WpApi

    private val baseUrl: String get() = server.url("/").toString().trimEnd('/')

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = WpApi(createHttpClient(Session()), baseUrl)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun json(body: String, code: Int = 200) =
        MockResponse.Builder().code(code).body(body).build()

    @Test
    fun `posts 请求携带分页、分类与搜索参数并能解析响应`() = runBlocking {
        server.enqueue(json(fixture("wp_posts_sample.json")))

        val posts = api.posts(page = 2, perPage = 5, category = 24, search = "测试")

        assertEquals(2, posts.size)
        assertEquals("[示例] 测试资源标题 1", posts[0].title.rendered)
        assertEquals(10000L, posts[0].id)
        assertEquals(listOf(24L), posts[0].categories)

        val request = server.takeRequest()
        assertEquals("/wp-json/wp/v2/posts", request.url.encodedPath)
        assertEquals("2", request.url.queryParameter("page"))
        assertEquals("5", request.url.queryParameter("per_page"))
        assertEquals("24", request.url.queryParameter("categories"))
        assertEquals("测试", request.url.queryParameter("search"))
        assertEquals("wp:featuredmedia", request.url.queryParameter("_embed"))
    }

    @Test
    fun `列表响应里的内嵌封面能被解析`() = runBlocking {
        server.enqueue(json(fixture("wp_posts_sample.json")))

        val posts = api.posts()

        val cover = posts[0].embedded?.featuredMedia?.firstOrNull()?.sourceUrl
        assertEquals("https://www.example.com/wp-content/uploads/2025/12/cover-1024x759.jpg", cover)
        // 第二篇没有封面（样本里 featured_media=0，也没有 _embedded）
        assertEquals(null, posts[1].embedded)
    }

    @Test
    fun `可以关闭内嵌封面减少响应体积`() = runBlocking {
        server.enqueue(json(fixture("wp_posts_sample.json")))

        api.posts(embedFeaturedMedia = false)

        assertEquals(null, server.takeRequest().url.queryParameter("_embed"))
    }

    @Test
    fun `posts 不传可选参数时不附加多余查询项`() = runBlocking {
        server.enqueue(json(fixture("wp_posts_sample.json")))

        api.posts()

        val request = server.takeRequest()
        assertEquals("1", request.url.queryParameter("page"))
        assertEquals("20", request.url.queryParameter("per_page"))
        assertEquals(null, request.url.queryParameter("categories"))
        assertEquals(null, request.url.queryParameter("search"))
    }

    @Test
    fun `post 按 id 拉取单篇并解析正文`() = runBlocking {
        server.enqueue(json(fixture("wp_post_sample.json")))

        val post = api.post(10000)

        assertEquals(10000L, post.id)
        assertTrue(post.content!!.rendered.contains("golink="))
        // featured_media 字段映射（样本固定值 12345）
        assertEquals(12345L, post.featuredMedia)

        assertEquals("/wp-json/wp/v2/posts/10000", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `pageBySlug 按 slug 拉取独立页面，不存在返回 null`() = runBlocking {
        server.enqueue(
            json(
                """[{"id":29834,"title":{"rendered":"网站使用手册"},"content":{"rendered":"<p>正文</p>"}}]""",
            ),
        )

        val page = api.pageBySlug("manual")

        assertEquals(29834L, page?.id)
        assertEquals("网站使用手册", page?.title?.rendered)

        val request = server.takeRequest()
        assertEquals("/wp-json/wp/v2/pages", request.url.encodedPath)
        assertEquals("manual", request.url.queryParameter("slug"))
        assertEquals("id,title,content", request.url.queryParameter("_fields"))

        server.enqueue(json("[]"))
        assertNull(api.pageBySlug("no-such-page"))
    }

    @Test
    fun `categories 按文章数倒序请求并解析`() = runBlocking {
        server.enqueue(json(fixture("wp_categories_sample.json")))

        val categories = api.categories()

        assertEquals(3, categories.size)
        assertTrue(categories.all { it.name.isNotBlank() })

        val request = server.takeRequest()
        assertEquals("/wp-json/wp/v2/categories", request.url.encodedPath)
        assertEquals("count", request.url.queryParameter("orderby"))
        assertEquals("desc", request.url.queryParameter("order"))
    }

    @Test
    fun `非 2xx 响应抛出带状态码与摘录的异常`() = runBlocking {
        server.enqueue(json("""{"code":"rest_post_invalid_id","message":"Invalid post ID."}""", code = 404))

        val error = try {
            api.post(999999)
            null
        } catch (e: SakuHttpException) {
            e
        }

        assertEquals(404, error!!.status)
        assertTrue(error.bodySnippet.contains("Invalid post ID"))
    }

    @Test
    fun `非法分页参数在本地直接拒绝`() {
        try {
            runBlocking { api.posts(page = 0) }
            throw AssertionError("应当抛出 IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // 预期
        }
        try {
            runBlocking { api.posts(perPage = 101) }
            throw AssertionError("应当抛出 IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // 预期
        }
    }
}
