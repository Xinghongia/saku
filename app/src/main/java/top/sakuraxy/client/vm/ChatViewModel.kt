package top.sakuraxy.client.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.sakuraxy.client.SakuApp
import top.sakuraxy.client.core.AjaxResult
import top.sakuraxy.client.core.ChatMessage
import top.sakuraxy.client.core.ChatWindow
import top.sakuraxy.client.core.UploadFile

/**
 * 私信会话（聊天页）：历史消息 + 发送文本/图片 + 黑名单。
 * 站点的私信窗口是 HTML 片段（含历史与本轮 nonce），每次发送成功后重新拉取窗口。
 */
class ChatViewModel(
    private val app: SakuApp,
    private val userId: Long,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val peerName: String? = null,
        val peerAvatarUrl: String? = null,
        val messages: List<ChatMessage> = emptyList(),
        val sending: Boolean = false,
        val canSend: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 最近一次打开的窗口（发送表单 nonce 的载体，现取现用） */
    private var window: ChatWindow? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val chat = app.client.messages.openChat(userId)
                window = chat
                _state.update {
                    it.copy(
                        loading = false,
                        peerName = chat.peerName,
                        peerAvatarUrl = chat.peerAvatarUrl,
                        messages = chat.messages,
                        canSend = chat.sendFields.containsKey("send_private_nonce"),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "加载失败，请重试") }
            }
        }
    }

    fun send(text: String) {
        val chat = window ?: return
        if (text.isBlank() || _state.value.sending) return
        viewModelScope.launch {
            _state.update { it.copy(sending = true) }
            try {
                when (val result = app.client.messages.sendPrivate(chat, text.trim())) {
                    is AjaxResult.Ok -> {
                        _message.value = result.msg.ifBlank { "已发送" }
                        load()
                    }
                    is AjaxResult.Fail -> _message.value = result.msg
                    is AjaxResult.NeedsCaptcha -> _message.value = result.msg
                    is AjaxResult.NotJson -> _message.value = "发送失败：站点响应异常"
                }
            } catch (e: Exception) {
                _message.value = e.message ?: "发送失败，请重试"
            } finally {
                _state.update { it.copy(sending = false) }
            }
        }
    }

    /** 发送图片：先上传取 URL，再把 URL 作为消息内容发送（与网页行为一致） */
    fun sendImage(file: UploadFile) {
        val chat = window ?: return
        if (_state.value.sending) return
        viewModelScope.launch {
            _state.update { it.copy(sending = true) }
            try {
                val url = app.client.messages.uploadChatImage(chat, file)
                if (url.isNullOrBlank()) {
                    _message.value = "图片上传失败"
                } else {
                    when (val result = app.client.messages.sendPrivate(chat, url)) {
                        is AjaxResult.Ok -> {
                            _message.value = "已发送"
                            load()
                        }
                        is AjaxResult.Fail -> _message.value = result.msg
                        else -> _message.value = "发送失败，请重试"
                    }
                }
            } catch (e: Exception) {
                _message.value = e.message ?: "发送失败，请重试"
            } finally {
                _state.update { it.copy(sending = false) }
            }
        }
    }

    /** 把对方加入黑名单（站点侧生效，之后双方不再能私信） */
    fun blacklist() {
        val chat = window ?: return
        viewModelScope.launch {
            try {
                when (val result = app.client.messages.blacklist(chat)) {
                    is AjaxResult.Ok -> _message.value = result.msg.ifBlank { "已加入黑名单" }
                    is AjaxResult.Fail -> _message.value = result.msg
                    else -> _message.value = "操作失败，请重试"
                }
            } catch (e: Exception) {
                _message.value = e.message ?: "操作失败，请重试"
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
