package top.sakuraxy.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import top.sakuraxy.client.ui.AppNav
import top.sakuraxy.client.ui.theme.SakuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as SakuApp
        // 恢复上次登录的 cookie；失败不影响匿名浏览
        lifecycleScope.launch {
            runCatching { app.client.session.restore(app.sessionStore.load()) }
        }
        setContent {
            SakuTheme {
                AppNav(app)
            }
        }
    }
}
