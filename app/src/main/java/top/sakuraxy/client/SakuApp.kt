package top.sakuraxy.client

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import top.sakuraxy.client.core.SakuClient

class SakuApp : Application(), SingletonImageLoader.Factory {

    /** 全局唯一的站点客户端（会话 cookie 都挂在它身上） */
    lateinit var client: SakuClient
        private set

    lateinit var sessionStore: SessionStore
        private set

    lateinit var settingsStore: SettingsStore
        private set

    /**
     * 内容变更信号：详情页等处的写操作（收藏/关注/点赞/评论/积分购买）成功后自增；
     * 跨页面展示的用户数据（如「我的」页统计与资产）订阅它静默刷新——
     * 返回「我的」页时数字已是新值，不必再进二级页面才看到。
     */
    val contentRevision = MutableStateFlow(0L)

    fun notifyContentChanged() {
        contentRevision.update { it + 1 }
    }

    override fun onCreate() {
        super.onCreate()
        client = SakuClient()
        sessionStore = SessionStore(this)
        settingsStore = SettingsStore(this)
    }

    /** 图片也走同一个 HTTP 客户端：UA 与登录会话保持一致；SVG 解码用于站点徽章等矢量素材 */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { client.http }))
                add(SvgDecoder.Factory())
            }
            .crossfade(true)
            .build()
}
