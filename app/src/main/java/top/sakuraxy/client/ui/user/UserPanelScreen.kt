package top.sakuraxy.client.ui.user

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import top.sakuraxy.client.SakuApp
import top.sakuraxy.client.core.LevelInfo
import top.sakuraxy.client.core.OrderItem
import top.sakuraxy.client.core.PointsRecord
import top.sakuraxy.client.core.UploadFile
import top.sakuraxy.client.ui.AndroidActions
import top.sakuraxy.client.ui.components.CenterLoading
import top.sakuraxy.client.ui.components.EmptyHint
import top.sakuraxy.client.ui.components.ErrorRetry
import top.sakuraxy.client.ui.components.LevelBadge
import top.sakuraxy.client.ui.components.SakuTopAppBar
import top.sakuraxy.client.ui.components.SiteOnlyDialog
import top.sakuraxy.client.vm.AuthViewModel
import top.sakuraxy.client.vm.BalanceViewModel
import top.sakuraxy.client.vm.LevelViewModel
import top.sakuraxy.client.vm.MsgShieldViewModel
import top.sakuraxy.client.vm.OrderViewModel
import top.sakuraxy.client.vm.RewardsViewModel
import top.sakuraxy.client.vm.VipViewModel
import top.sakuraxy.client.vm.readUploadFile

private val panelTitles = mapOf(
    "level" to "我的等级",
    "vip" to "我的会员",
    "auth" to "官方认证",
    "balance" to "我的资产",
    "order" to "我的订单",
    "msg" to "消息通知",
    "rewards" to "打赏收款",
)

/** 用户中心各面板：我的等级 / 会员 / 认证 / 资产 / 订单 / 消息通知设置 / 打赏收款 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserPanelScreen(
    app: SakuApp,
    panel: String,
    onBack: () -> Unit,
    onOpenPost: (Long) -> Unit,
) {
    Scaffold(
        topBar = {
            SakuTopAppBar(
                title = panelTitles[panel] ?: "用户中心",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (panel) {
                "level" -> LevelPanel(app)
                "vip" -> VipPanel(app)
                "auth" -> AuthPanel(app)
                "balance" -> BalancePanel(app)
                "order" -> OrderPanel(app, onOpenPost)
                "msg" -> MsgShieldPanel(app)
                "rewards" -> RewardsPanel(app)
                else -> EmptyHint("未知面板")
            }
        }
    }
}

@Composable
private fun PanelScroll(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

/** 我的等级：当前等级 + 经验进度 + 今日获得 + 等级门槛表 */
@Composable
private fun LevelPanel(app: SakuApp) {
    val viewModel = viewModel { LevelViewModel(app) }
    val state by viewModel.state.collectAsState()
    when (val result = state) {
        null -> CenterLoading()
        else -> result.fold(
            onSuccess = { level -> LevelContent(level, viewModel::load) },
            onFailure = { e -> ErrorRetry(e.message ?: "加载失败", onRetry = viewModel::load) },
        )
    }
}

@Composable
private fun LevelContent(level: LevelInfo, onReload: () -> Unit) {
    PanelScroll {
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LevelBadge(level.level, Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "LV${level.level ?: "-"}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    level.todayGain?.let {
                        Text(
                            "今日 +$it",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                val exp = level.exp?.toFloatOrNull()
                val total = level.expTotal?.toFloatOrNull()
                if (exp != null && total != null && total > 0) {
                    LinearProgressIndicator(
                        progress = { (exp / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text("${level.exp}/${level.expTotal}", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "升级还需 ${level.need ?: "-"} 经验",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (level.thresholds.isNotEmpty()) {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("等级门槛（经验值）", style = MaterialTheme.typography.titleSmall)
                    level.thresholds.forEachIndexed { index, threshold ->
                        val levelNo = index + 1
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LevelBadge(levelNo, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("LV$levelNo", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.weight(1f))
                            Text(
                                threshold.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = onReload, modifier = Modifier.fillMaxWidth()) { Text("刷新") }
    }
}

/** 我的会员：状态 + 黄金/钻石档位（开通按钮只提示去网站，客户端不经手支付） */
@Composable
private fun VipPanel(app: SakuApp) {
    val viewModel = viewModel { VipViewModel(app) }
    val state by viewModel.state.collectAsState()
    var siteOnly by remember { mutableStateOf(false) }
    if (siteOnly) SiteOnlyDialog(onDismiss = { siteOnly = false })

    when (val result = state) {
        null -> CenterLoading()
        else -> result.fold(
            onSuccess = { vip ->
                PanelScroll {
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "当前会员状态",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                vip.status ?: "未开通会员",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (vip.isVip) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                            )
                            Text(
                                "开通VIP会员，享受会员专属折扣以及多项特权",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    vip.tiers.forEach { tier ->
                        Surface(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = if (tier.level == 1) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                        ) {
                            Column(
                                Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(tier.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Text("全站资源折扣购买 · 部分内容免费阅读 · 一对一技术指导", style = MaterialTheme.typography.bodySmall)
                                Button(onClick = { siteOnly = true }) { Text("开通${tier.name}") }
                            }
                        }
                    }
                }
            },
            onFailure = { e -> ErrorRetry(e.message ?: "加载失败", onRetry = viewModel::load) },
        )
    }
}

/** 官方认证：当前是否可申请与所需等级 */
@Composable
private fun AuthPanel(app: SakuApp) {
    val viewModel = viewModel { AuthViewModel(app) }
    val state by viewModel.state.collectAsState()
    when (val result = state) {
        null -> CenterLoading()
        else -> result.fold(
            onSuccess = { auth ->
                PanelScroll {
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(
                            Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (auth.canApply) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = if (auth.canApply) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("申请认证", style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                auth.message ?: if (auth.canApply) "您可以直接申请身份认证" else "暂无认证资格",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            auth.minLevel?.let { level ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("需要等级：", style = MaterialTheme.typography.bodySmall)
                                    LevelBadge(level, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("LV$level", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            },
            onFailure = { e -> ErrorRetry(e.message ?: "加载失败", onRetry = viewModel::load) },
        )
    }
}

/** 我的资产：余额/积分 + 充值提示 + 签到 + 积分记录 */
@Composable
private fun BalancePanel(app: SakuApp) {
    val viewModel = viewModel { BalanceViewModel(app) }
    val state by viewModel.state.collectAsState()
    val message by viewModel.message.collectAsState()
    val checkingIn by viewModel.checkingIn.collectAsState()
    var siteOnly by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.message.collect { text ->
            text?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                viewModel.consumeMessage()
            }
        }
    }
    if (siteOnly) SiteOnlyDialog(onDismiss = { siteOnly = false })

    when (val result = state) {
        null -> CenterLoading()
        else -> result.fold(
            onSuccess = { balance ->
                PanelScroll {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AssetBigCard("余额", "¥${balance.balance ?: "0"}", "待转入 ${balance.pendingIn ?: "0"}", Modifier.weight(1f)) {
                            siteOnly = true
                        }
                        AssetBigCard(
                            label = "积分",
                            value = balance.points ?: "0",
                            sub = if (balance.checkedIn) "今日已签到" else "点击签到",
                            modifier = Modifier.weight(1f),
                        ) {
                            // 已签到不再发起签到（站点会拒绝）；签到明细弹窗未实现，先只显示状态
                            if (!balance.checkedIn) viewModel.checkin()
                        }
                    }
                    if (balance.records.isNotEmpty()) {
                        Surface(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("积分记录", style = MaterialTheme.typography.titleSmall)
                                balance.records.forEach { record -> PointsRecordRow(record) }
                            }
                        }
                    } else {
                        Text(
                            "暂无记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (checkingIn) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("签到中…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            onFailure = { e -> ErrorRetry(e.message ?: "加载失败", onRetry = viewModel::load) },
        )
    }
}

@Composable
private fun AssetBigCard(
    label: String,
    value: String,
    sub: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Text(
                sub ?: if (label == "积分") "点击签到" else "点击充值",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PointsRecordRow(record: PointsRecord) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                record.badge?.let {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                record.detail?.let {
                    Spacer(Modifier.width(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            record.time?.let {
                Text("时间：$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            record.orderNo?.let {
                Text("订单号：$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                record.delta.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (record.delta?.startsWith("-") == true) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            record.after?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 我的订单：全部 / 待支付 + 订单列表（去下载 → 文章详情） */
@Composable
private fun OrderPanel(app: SakuApp, onOpenPost: (Long) -> Unit) {
    val viewModel = viewModel { OrderViewModel(app) }
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.tab == "all",
                onClick = { viewModel.setTab("all") },
                label = { Text("全部") },
            )
            FilterChip(
                selected = state.tab == "wait-pay",
                onClick = { viewModel.setTab("wait-pay") },
                label = { Text("待支付") },
            )
        }
        when {
            state.loading -> CenterLoading()
            state.error != null -> ErrorRetry(state.error!!, onRetry = viewModel::load)
            state.orders.isEmpty() -> EmptyHint("暂无订单")
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.orders.forEach { order ->
                    OrderCard(order) { url ->
                        val postId = Regex("""/(\d+)/?$""").find(url)?.groupValues?.get(1)?.toLongOrNull()
                        if (postId != null) onOpenPost(postId)
                        else AndroidActions.openInBrowser(context, url)
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderCard(order: OrderItem, onDownload: (String) -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                order.sellerName?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.weight(1f))
                order.status?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (it.contains("支付") && !it.contains("未") && !it.contains("待")) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                order.thumbUrl?.let { thumb ->
                    AsyncImage(
                        model = thumb,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        order.title ?: "（无标题）",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        order.payLabel?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        order.price?.let {
                            Text("积分 $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    order.date?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            order.downloadUrl?.let { url ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = { onDownload(url) }) { Text("去下载") }
                }
            }
        }
    }
}

/** 消息通知设置：三个开关 */
@Composable
private fun MsgShieldPanel(app: SakuApp) {
    val viewModel = viewModel { MsgShieldViewModel(app) }
    val state by viewModel.state.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.message.collect { text ->
            text?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                viewModel.consumeMessage()
            }
        }
    }

    when {
        state.loading -> CenterLoading()
        state.error != null -> ErrorRetry(state.error!!, onRetry = viewModel::load)
        else -> PanelScroll {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SwitchRow("文章评论", "接收文章、评论、收藏等相关消息", state.posts) {
                        viewModel.set(posts = it)
                    }
                    SwitchRow("点赞喜欢", "接收点赞、关注等相关消息", state.like) {
                        viewModel.set(like = it)
                    }
                    SwitchRow("系统消息", "接收订单、活动、等系统消息", state.system) {
                        viewModel.set(system = it)
                    }
                }
            }
            Button(
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.saving) "保存中…" else "保存")
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, supporting: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** 打赏收款：标题 + 微信/支付宝收款码 */
@Composable
private fun RewardsPanel(app: SakuApp) {
    val viewModel = viewModel { RewardsViewModel(app) }
    val state by viewModel.state.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current
    var newWeixin by remember { mutableStateOf<UploadFile?>(null) }
    var newAlipay by remember { mutableStateOf<UploadFile?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.message.collect { text ->
            text?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                viewModel.consumeMessage()
            }
        }
    }

    val weixinPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { readUploadFile(context, uri) }
            .onSuccess { newWeixin = it }
            .onFailure { Toast.makeText(context, it.message ?: "图片读取失败", Toast.LENGTH_SHORT).show() }
    }
    val alipayPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { readUploadFile(context, uri) }
            .onSuccess { newAlipay = it }
            .onFailure { Toast.makeText(context, it.message ?: "图片读取失败", Toast.LENGTH_SHORT).show() }
    }

    when {
        state.loading -> CenterLoading()
        state.error != null -> ErrorRetry(state.error!!, onRetry = viewModel::load)
        else -> PanelScroll {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                label = { Text("打赏标题") },
                placeholder = { Text("文章很赞！支持一下吧") },
                modifier = Modifier.fillMaxWidth(),
            )
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    QrPicker("微信收款码", state.weixinQrUrl, newWeixin != null) {
                        weixinPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    QrPicker("支付宝收款码", state.alipayQrUrl, newAlipay != null) {
                        alipayPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
            }
            Button(
                onClick = { viewModel.save(newWeixin, newAlipay) },
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.saving) "保存中…" else "保存")
            }
        }
    }
}

@Composable
private fun QrPicker(label: String, url: String?, picked: Boolean, onPick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            Modifier.size(120.dp).clickable(onClick = onPick),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    model = url,
                    contentDescription = label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
        if (picked) {
            Text("已选择新图片，保存后生效", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
