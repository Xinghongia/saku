package top.sakuraxy.client.vm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.sakuraxy.client.SakuApp
import top.sakuraxy.client.core.AjaxResult
import top.sakuraxy.client.core.AuthInfo
import top.sakuraxy.client.core.BalancePanel
import top.sakuraxy.client.core.FormSelect
import top.sakuraxy.client.core.LevelInfo
import top.sakuraxy.client.core.OrderItem
import top.sakuraxy.client.core.PanelForm
import top.sakuraxy.client.core.RewardsPanel
import top.sakuraxy.client.core.UploadFile
import top.sakuraxy.client.core.VipPanel

/** 站点上传限制：jpg/gif ≤3M（弹窗文案实测）；压缩后仍超限则报错 */
private const val MAX_UPLOAD_BYTES = 3L * 1024 * 1024

/**
 * 把相册选中的图片整理成可上传的文件：
 * - gif 原样（站点允许，不做重编码以免破坏动画）
 * - 其他格式解码后压成 JPEG，超过 3M 时逐级降质，仍超限则抛错
 */
fun readUploadFile(context: Context, uri: Uri): UploadFile {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalArgumentException("无法读取所选图片")
    val mime = context.contentResolver.getType(uri).orEmpty()
    val name = "upload_${System.currentTimeMillis()}"
    if (mime.contains("gif")) {
        if (bytes.size > MAX_UPLOAD_BYTES) throw IllegalArgumentException("GIF 超过 3M，请换一张图片")
        return UploadFile("$name.gif", "image/gif", bytes)
    }

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 2000 || bounds.outHeight / sample > 2000) sample *= 2
    val bitmap = BitmapFactory.decodeByteArray(
        bytes, 0, bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: throw IllegalArgumentException("无法解析所选图片")

    var quality = 88
    var out: ByteArray
    while (true) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        out = stream.toByteArray()
        if (out.size <= MAX_UPLOAD_BYTES) break
        if (quality <= 50) throw IllegalArgumentException("图片超过 3M 且压缩后仍过大，请换一张")
        quality -= 12
    }
    return UploadFile("$name.jpg", "image/jpeg", out)
}

/** 编辑资料：表单字段 + 头像/封面上传 */
class EditProfileViewModel(private val app: SakuApp) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val values: Map<String, String> = emptyMap(),
        val selects: List<FormSelect> = emptyList(),
        val avatarUrl: String? = null,
        val coverUrl: String? = null,
        val saving: Boolean = false,
        val uploadingAvatar: Boolean = false,
        val uploadingCover: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var form: PanelForm? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val parsed = app.client.users.dataForm()
                if (parsed == null) {
                    _state.update { it.copy(loading = false, error = "资料表单加载失败，请重试") }
                    return@launch
                }
                form = parsed
                // 头像/封面预览（失败不影响表单）
                val overview = runCatching { app.client.users.overview() }.getOrNull()
                _state.update {
                    it.copy(
                        loading = false,
                        values = parsed.fields,
                        selects = parsed.selects,
                        avatarUrl = overview?.avatarUrl,
                        coverUrl = overview?.coverUrl,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "加载失败，请重试") }
            }
        }
    }

    fun setValue(name: String, value: String) {
        _state.update { it.copy(values = it.values + (name to value)) }
    }

    /** 保存资料（提交除隐藏字段外的全部可编辑字段） */
    fun save() {
        val current = form ?: return
        val values = _state.value.values
            .filterKeys { it in EDITABLE_FIELDS }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                when (val result = app.client.users.submitForm(current, values)) {
                    is AjaxResult.Ok -> {
                        _message.value = result.msg.ifBlank { "资料已保存" }
                        load()
                    }
                    is AjaxResult.Fail -> _message.value = result.msg
                    is AjaxResult.NeedsCaptcha -> _message.value = result.msg
                    is AjaxResult.NotJson -> _message.value = "保存失败：站点响应异常"
                }
            } catch (e: Exception) {
                _message.value = e.message ?: "保存失败，请重试"
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    fun uploadAvatar(file: UploadFile) = upload(file, avatar = true)
    fun uploadCover(file: UploadFile) = upload(file, avatar = false)

    private fun upload(file: UploadFile, avatar: Boolean) {
        _state.update {
            if (avatar) it.copy(uploadingAvatar = true) else it.copy(uploadingCover = true)
        }
        viewModelScope.launch {
            try {
                val result = if (avatar) app.client.users.uploadAvatar(file)
                else app.client.users.uploadCover(file)
                when (result) {
                    is AjaxResult.Ok -> {
                        _message.value = result.msg.ifBlank { "上传成功" }
                        refreshImages()
                    }
                    is AjaxResult.Fail -> _message.value = result.msg
                    is AjaxResult.NeedsCaptcha -> _message.value = result.msg
                    is AjaxResult.NotJson -> _message.value = "上传失败：站点响应异常"
                }
            } catch (e: Exception) {
                _message.value = e.message ?: "上传失败，请重试"
            } finally {
                _state.update {
                    if (avatar) it.copy(uploadingAvatar = false) else it.copy(uploadingCover = false)
                }
            }
        }
    }

    /** 上传成功后刷新头像/封面预览（新文件地址，Coil 自然拉新图） */
    private fun refreshImages() {
        viewModelScope.launch {
            val overview = runCatching { app.client.users.overview() }.getOrNull() ?: return@launch
            _state.update { it.copy(avatarUrl = overview.avatarUrl, coverUrl = overview.coverUrl) }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    companion object {
        /** 与站点资料表单一致的可编辑字段（隐藏字段与 user_id 不在此列） */
        val EDITABLE_FIELDS = setOf(
            "name", "desc", "privacy", "gender", "address",
            "url_name", "url", "qq", "weixin", "weibo", "github",
        )
        val FIELD_LABELS = listOf(
            "name" to "昵称",
            "desc" to "个人简介",
            "address" to "居住地址",
            "url_name" to "网站名称",
            "url" to "网址",
            "qq" to "QQ",
            "weixin" to "微信",
            "weibo" to "微博",
            "github" to "GitHub",
        )
    }
}

/** 消息通知设置（三个开关） */
class MsgShieldViewModel(private val app: SakuApp) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val posts: Boolean = true,
        val like: Boolean = true,
        val system: Boolean = true,
        val saving: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var form: PanelForm? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val parsed = app.client.users.msgShieldForm()
                if (parsed == null) {
                    _state.update { it.copy(loading = false, error = "设置加载失败，请重试") }
                    return@launch
                }
                form = parsed
                _state.update {
                    it.copy(
                        loading = false,
                        posts = parsed.checkboxes["posts"] ?: true,
                        like = parsed.checkboxes["like"] ?: true,
                        system = parsed.checkboxes["system"] ?: true,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "加载失败，请重试") }
            }
        }
    }

    fun set(posts: Boolean = _state.value.posts, like: Boolean = _state.value.like, system: Boolean = _state.value.system) {
        _state.update { it.copy(posts = posts, like = like, system = system) }
    }

    fun save() {
        val current = form ?: return
        val s = _state.value
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                when (val result = app.client.users.saveMsgShield(current, s.posts, s.like, s.system)) {
                    is AjaxResult.Ok -> {
                        _message.value = result.msg.ifBlank { "已保存" }
                        load()
                    }
                    is AjaxResult.Fail -> _message.value = result.msg
                    is AjaxResult.NeedsCaptcha -> _message.value = result.msg
                    is AjaxResult.NotJson -> _message.value = "保存失败：站点响应异常"
                }
            } catch (e: Exception) {
                _message.value = e.message ?: "保存失败，请重试"
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}

/** 打赏收款：标题 + 微信/支付宝收款码上传 */
class RewardsViewModel(private val app: SakuApp) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val title: String = "",
        val weixinQrUrl: String? = null,
        val alipayQrUrl: String? = null,
        val saving: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var panel: RewardsPanel? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val parsed = app.client.users.rewardsForm()
                if (parsed == null) {
                    _state.update { it.copy(loading = false, error = "表单加载失败，请重试") }
                    return@launch
                }
                panel = parsed
                _state.update {
                    it.copy(
                        loading = false,
                        title = parsed.title,
                        weixinQrUrl = parsed.weixinQrUrl,
                        alipayQrUrl = parsed.alipayQrUrl,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "加载失败，请重试") }
            }
        }
    }

    fun setTitle(value: String) {
        _state.update { it.copy(title = value) }
    }

    /** 保存：标题 +（可选）新上传的收款码（传 null 表示保留原图） */
    fun save(weixin: UploadFile? = null, alipay: UploadFile? = null) {
        val current = panel ?: return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val result = app.client.users.saveRewards(
                    form = current.form,
                    uploadForm = current.upload,
                    title = _state.value.title,
                    weixin = weixin,
                    alipay = alipay,
                )
                when (result) {
                    is AjaxResult.Ok -> {
                        _message.value = result.msg.ifBlank { "已保存" }
                        load()
                    }
                    is AjaxResult.Fail -> _message.value = result.msg
                    is AjaxResult.NeedsCaptcha -> _message.value = result.msg
                    is AjaxResult.NotJson -> _message.value = "保存失败：站点响应异常"
                }
            } catch (e: Exception) {
                _message.value = e.message ?: "保存失败，请重试"
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}

/** 我的等级 */
class LevelViewModel(private val app: SakuApp) : ViewModel() {
    private val _state = MutableStateFlow<Result<LevelInfo>?>(null)
    val state: StateFlow<Result<LevelInfo>?> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = runCatching { app.client.users.level() }
        }
    }
}

/** 我的会员（展示 + 开通按钮只在 UI 提示去网站） */
class VipViewModel(private val app: SakuApp) : ViewModel() {
    private val _state = MutableStateFlow<Result<VipPanel>?>(null)
    val state: StateFlow<Result<VipPanel>?> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = runCatching { app.client.users.vip() }
        }
    }
}

/** 官方认证 */
class AuthViewModel(private val app: SakuApp) : ViewModel() {
    private val _state = MutableStateFlow<Result<AuthInfo>?>(null)
    val state: StateFlow<Result<AuthInfo>?> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = runCatching { app.client.users.auth() }
        }
    }
}

/** 我的资产：余额/积分 + 记录 + 签到 */
class BalanceViewModel(private val app: SakuApp) : ViewModel() {

    private val _state = MutableStateFlow<Result<BalancePanel>?>(null)
    val state: StateFlow<Result<BalancePanel>?> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _checkingIn = MutableStateFlow(false)
    val checkingIn: StateFlow<Boolean> = _checkingIn.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = runCatching { app.client.users.balance() }
        }
    }

    fun checkin() {
        if (_checkingIn.value) return
        viewModelScope.launch {
            _checkingIn.value = true
            try {
                when (val result = app.client.users.checkin()) {
                    is AjaxResult.Ok -> {
                        _message.value = result.msg.ifBlank { "签到成功" }
                        load()
                    }
                    is AjaxResult.Fail -> _message.value = result.msg
                    is AjaxResult.NeedsCaptcha -> _message.value = result.msg
                    is AjaxResult.NotJson -> _message.value = "签到失败：站点响应异常"
                }
            } catch (e: Exception) {
                _message.value = e.message ?: "签到失败，请重试"
            } finally {
                _checkingIn.value = false
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}

/** 我的订单：全部 / 待支付 */
class OrderViewModel(private val app: SakuApp) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val tab: String = "all",
        val orders: List<OrderItem> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val orders = app.client.users.orders(_state.value.tab)
                _state.update { it.copy(loading = false, orders = orders) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "加载失败，请重试") }
            }
        }
    }

    fun setTab(tab: String) {
        if (tab == _state.value.tab) return
        _state.update { it.copy(tab = tab, orders = emptyList()) }
        load()
    }
}
