package top.sakuraxy.client.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** 系统交互小工具（打开浏览器 / 复制到剪贴板） */
object AndroidActions {

    fun openInBrowser(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "没有可用的浏览器", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyToClipboard(context: Context, text: String) {
        val manager = context.getSystemService(ClipboardManager::class.java)
        manager?.setPrimaryClip(ClipData.newPlainText("链接", text))
        Toast.makeText(context, "已复制链接", Toast.LENGTH_SHORT).show()
    }

    /** 系统分享面板（发送文本 + 链接给任意已装应用） */
    fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "分享到"))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "没有可用的分享目标", Toast.LENGTH_SHORT).show()
        }
    }
}
