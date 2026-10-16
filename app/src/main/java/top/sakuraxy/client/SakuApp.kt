package top.sakuraxy.client

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import top.sakuraxy.client.core.SakuClient

class SakuApp : Application(), SingletonImageLoader.Factory {

    /** 全局唯一的站点客户端（会话 cookie 都挂在它身上） */
    lateinit var client: SakuClient
        private set

    lateinit var sessionStore: SessionStore
        private set

    lateinit var settingsStore: SettingsStore
        private set

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
