package top.sakuraxy.client.ui.profile

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.collectLatest
import top.sakuraxy.client.BuildConfig
import top.sakuraxy.client.SakuApp
import top.sakuraxy.client.core.UserOverview
import top.sakuraxy.client.ui.components.CenterLoading
import top.sakuraxy.client.ui.components.ErrorRetry
import top.sakuraxy.client.ui.components.GroupTone
import top.sakuraxy.client.ui.components.LevelBadge
import top.sakuraxy.client.ui.components.MedalChips
import top.sakuraxy.client.ui.components.SakuTopAppBar
import top.sakuraxy.client.ui.components.SettingsGroup
import top.sakuraxy.client.ui.components.SiteOnlyDialog
import top.sakuraxy.client.ui.components.UserAvatar
import top.sakuraxy.client.ui.components.UserStatRow
import top.sakuraxy.client.vm.ProfileViewModel

/**
 * 「我的」页：未登录 = 登录入口；已登录 = 完整用户中心（对齐网站 /user/ 页）。
 *
 * 子功能入口通过回调交给 AppNav 路由（资料编辑/消息中心/资产等面板/作者页/投稿）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    app: SakuApp,
    onOpenLogin: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEdit: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenNewPost: () -> Unit,
    onOpenPanel: (String) -> Unit,
    onOpenAuthor: (Long, String?) -> Unit,
) {
    val viewModel = viewModel { ProfileViewModel(app) }
    val state by viewModel.state.collectAsState()
    val message by viewModel.message.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val siteOnly by viewModel.siteOnlyHint.collectAsState()
    val medalUi by viewModel.medalUi.collectAsState()
    val context = LocalContext.current

    // 回到本页（含首次进入）静默刷新：资料编辑/头像修改返回后立即可见
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }

    LaunchedEffect(viewModel) {
        viewModel.message.collectLatest { text ->
            text?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                viewModel.consumeMessage()
            }
        }
    }
    if (siteOnly) SiteOnlyDialog(onDismiss = viewModel::dismissSiteOnlyHint)
    medalUi?.let { current ->
        MedalWallDialog(
            state = current,
            onDismiss = viewModel::dismissMedals,
            onRetry = viewModel::openMedals,
        )
    }

    Scaffold(
        topBar = {
            SakuTopAppBar(
                title = "我的",
                actions = {
                    // 消息铃铛（登录后才显示，与网页头部一致）
                    if (state is ProfileViewModel.UiState.LoggedIn) {
                        IconButton(onClick = onOpenMessages) {
                            Icon(Icons.Outlined.Notifications, contentDescription = "消息中心")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "设置")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        when (val current = state) {
            ProfileViewModel.UiState.Loading -> CenterLoading(Modifier.padding(padding))
            is ProfileViewModel.UiState.Error ->
                ErrorRetry(current.message, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            ProfileViewModel.UiState.LoggedOut -> LoggedOutContent(onOpenLogin, Modifier.padding(padding))
            is ProfileViewModel.UiState.LoggedIn -> PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                UserCenterContent(
                    state = current,
                    busy = busy,
                    onCheckin = viewModel::checkin,
                    onOpenEdit = onOpenEdit,
                    onOpenNewPost = onOpenNewPost,
                    onOpenPanel = onOpenPanel,
                    onOpenAuthor = onOpenAuthor,
                    onR18Change = viewModel::setR18Blocked,
                    onViewMedals = viewModel::openMedals,
                    onLogout = viewModel::logout,
                    modifier = Modifier,
                )
            }
        }
    }
}

/** 未登录：登录入口卡片（保留原样式） */
@Composable
private fun LoggedOutContent(onOpenLogin: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(30.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("未登录", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "登录后可获得与网页相同的访问权限",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SettingsGroup(title = "账号与功能") {
            item(
                headline = "登录 / 切换账号",
                supporting = "使用站点账号登录",
                icon = Icons.Outlined.Login,
                tone = GroupTone.PRIMARY,
                onClick = onOpenLogin,
            )
        }

        VersionFooter()
    }
}

/** 已登录：用户中心总览 */
@Composable
private fun UserCenterContent(
    state: ProfileViewModel.UiState.LoggedIn,
    busy: Boolean,
    onCheckin: () -> Unit,
    onOpenEdit: () -> Unit,
    onOpenNewPost: () -> Unit,
    onOpenPanel: (String) -> Unit,
    onOpenAuthor: (Long, String?) -> Unit,
    onR18Change: (Boolean) -> Unit,
    onViewMedals: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val overview = state.overview
    var confirmLogout by remember { mutableStateOf(false) }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("退出登录") },
            text = { Text("退出后需要重新输入账号密码登录，确定退出吗？") },
            confirmButton = {
                TextButton(onClick = { confirmLogout = false; onLogout() }) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("取消") }
            },
        )
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProfileHeaderCard(
            overview = overview,
            email = state.email,
            busy = busy,
            onCheckin = onCheckin,
            onOpenEdit = onOpenEdit,
            onViewMedals = onViewMedals,
        )

        UserStatRow(
            stats = overview.stats,
            onStatClick = { stat -> onOpenAuthor(state.userId, stat.tab) },
        )

        VipStatusCard(
            status = overview.vipStatus,
            isVip = overview.isVip,
            onClick = { onOpenPanel("vip") },
        )

        AssetRow(
            balance = overview.balance,
            points = overview.points,
            onClick = { onOpenPanel("balance") },
        )

        SettingsGroup(title = "我的服务") {
            item(
                headline = "我的订单",
                supporting = "已购买与待支付订单",
                icon = Icons.Outlined.ReceiptLong,
                tone = GroupTone.PRIMARY,
                onClick = { onOpenPanel("order") },
            )
            item(
                headline = "我的等级",
                supporting = "等级进度与升级经验",
                icon = Icons.Outlined.MilitaryTech,
                tone = GroupTone.SECONDARY,
                onClick = { onOpenPanel("level") },
            )
            item(
                headline = "官方认证",
                supporting = "身份认证状态",
                icon = Icons.Outlined.VerifiedUser,
                tone = GroupTone.TERTIARY,
                onClick = { onOpenPanel("auth") },
            )
        }

        SettingsGroup(title = "功能设置") {
            item(
                headline = "消息通知",
                supporting = "选择接收哪些类型的消息",
                icon = Icons.Outlined.Notifications,
                tone = GroupTone.PRIMARY,
                onClick = { onOpenPanel("msg") },
            )
            item(
                headline = "个人资料",
                supporting = "头像、封面与资料编辑",
                icon = Icons.Outlined.ManageAccounts,
                tone = GroupTone.SECONDARY,
                onClick = onOpenEdit,
            )
            item(
                headline = "打赏收款",
                supporting = "打赏标题与收款码",
                icon = Icons.Outlined.CardGiftcard,
                tone = GroupTone.TERTIARY,
                onClick = { onOpenPanel("rewards") },
            )
            item(
                headline = "账户安全",
                supporting = "邮箱、密码与社交绑定",
                icon = Icons.Outlined.Security,
                tone = GroupTone.PRIMARY,
                onClick = { onOpenPanel("account") },
            )
            item(
                headline = "发布内容",
                supporting = "投稿文章（与网页发布等效）",
                icon = Icons.Outlined.EditNote,
                tone = GroupTone.SECONDARY,
                onClick = onOpenNewPost,
            )
        }

        SettingsGroup(title = "拓展") {
            switchItem(
                headline = "屏蔽内容",
                supporting = "开启后内容列表将屏蔽指定类型内容",
                icon = Icons.Outlined.VisibilityOff,
                tone = GroupTone.TERTIARY,
                checked = overview.r18Blocked,
                onCheckedChange = onR18Change,
            )
        }

        OutlinedButton(
            onClick = { confirmLogout = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("退出登录")
        }

        VersionFooter()
    }
}

/**
 * 头部卡：封面 / 分界线 / 卡片底部三段。
 * - 名字 + 等级：**贴在封面底部**（分界线上方，头像正右侧）
 * - 徽章「初出茅庐」：**分界线下方**，头像下半身右侧
 * - 邮箱：分界线下方整行宽度完整显示 + 签到按钮（已签到 = 显示已签到且不可点）
 * 头像半悬于分界线（上下各一半）。
 */
@Composable
private fun ProfileHeaderCard(
    overview: UserOverview,
    email: String,
    busy: Boolean,
    onCheckin: () -> Unit,
    onOpenEdit: () -> Unit,
    onViewMedals: () -> Unit,
) {
    val coverHeight = 112.dp
    val avatarSize = 82.dp
    /** 头像右侧内容的统一起点（头像宽 + 间距） */
    val textStart = 16.dp + avatarSize + 12.dp
    val cardColor = MaterialTheme.colorScheme.surfaceContainerLow
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = cardColor,
    ) {
        Column {
            // 封面（含名字/等级/半悬头像）
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(coverHeight),
            ) {
                val coverFallback = MaterialTheme.colorScheme.primaryContainer
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                ) {
                    // 主题色垫底：没设封面或封面加载失败时都不会出现空白块
                    Canvas(Modifier.fillMaxSize()) { drawRect(coverFallback) }
                    if (!overview.coverUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = overview.coverUrl,
                            contentDescription = "个人封面",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                // 名字 + 等级：贴封面底边（分界线上方），头像正右侧
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = textStart, end = 16.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = overview.displayName ?: "已登录用户",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    LevelBadge(overview.level)
                }
                // 头像半悬于封面底边（压在分界线上，最后绘制）
                UserAvatar(
                    url = overview.avatarUrl,
                    size = avatarSize,
                    borderWidth = 3.dp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(y = avatarSize / 2)
                        .padding(start = 16.dp)
                        .clickable(onClick = onOpenEdit),
                )
            }

            // 分界线下：徽章（头像下半身右侧；行高固定为头像溢出高度，保证不压到下一行）
            // 行尾「查看所有」→ 打开与网页同款的「我的徽章」弹窗
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(avatarSize / 2)
                    .padding(start = textStart, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MedalChips(overview.medals, onCover = false)
                Spacer(Modifier.weight(1f))
                Text(
                    "查看所有",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(onClick = onViewMedals)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }

            // 邮箱（整行宽度，完整显示）+ 签到
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = email.ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = onCheckin,
                    enabled = !overview.checkedIn && !busy,
                ) {
                    Text(if (overview.checkedIn) "已签到" else "签到")
                }
            }
        }
    }
}

/** 会员状态卡（点击进会员面板；开通按钮在面板内，只做「去网站」提示） */
@Composable
private fun VipStatusCard(status: String?, isVip: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "当前会员状态",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = status ?: "未开通会员",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isVip) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Text(
                    "开通VIP会员，享受会员专属折扣以及多项特权",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 余额 / 积分两卡（点击进资产面板） */
@Composable
private fun AssetRow(balance: String?, points: String?, onClick: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AssetCard("余额", "¥${balance ?: "0"}", onClick, Modifier.weight(1f))
        AssetCard("积分", points ?: "0", onClick, Modifier.weight(1f))
    }
}

@Composable
private fun AssetCard(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun VersionFooter() {
    Text(
        text = "SakuraXy 客户端 ${BuildConfig.VERSION_NAME} · 数据来自 sakuraxy.top（第三方非官方客户端）",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
    )
}
