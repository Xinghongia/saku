package top.sakuraxy.client.ui

import android.net.Uri
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import top.sakuraxy.client.SakuApp
import top.sakuraxy.client.core.SiteParser
import top.sakuraxy.client.ui.auth.LoginScreen
import top.sakuraxy.client.ui.auth.ResetPasswordScreen
import top.sakuraxy.client.ui.auth.SignupScreen
import top.sakuraxy.client.ui.category.CategoriesScreen
import top.sakuraxy.client.ui.category.CategoryScreen
import top.sakuraxy.client.ui.detail.DetailScreen
import top.sakuraxy.client.ui.home.HomeScreen
import top.sakuraxy.client.ui.profile.ProfileScreen
import top.sakuraxy.client.ui.search.SearchScreen
import top.sakuraxy.client.ui.settings.ManualScreen
import top.sakuraxy.client.ui.settings.SettingsScreen
import top.sakuraxy.client.ui.user.AccountSecurityScreen
import top.sakuraxy.client.ui.user.AuthorScreen
import top.sakuraxy.client.ui.user.ChatScreen
import top.sakuraxy.client.ui.user.EditProfileScreen
import top.sakuraxy.client.ui.user.MessageCenterScreen
import top.sakuraxy.client.ui.user.NewPostScreen
import top.sakuraxy.client.ui.user.UserPanelScreen

private object Routes {
    const val HOME = "home"
    const val CATEGORIES = "categories"
    const val SEARCH = "search"
    const val PROFILE = "profile"
    const val CATEGORY = "category?url={url}&name={name}"
    const val DETAIL = "detail/{postId}"
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val RESET_PASSWORD = "reset-password"
    const val SETTINGS = "settings"
    const val MANUAL = "manual"

    // ---- v0.5 用户中心 ----
    const val USER_EDIT = "user/edit"
    const val USER_ACCOUNT = "user/account"
    const val USER_MESSAGES = "user/messages?tab={tab}"
    const val USER_PANEL = "user/panel/{panel}"
    const val AUTHOR = "author/{authorId}?tab={tab}"
    const val CHAT = "chat/{userId}"
    const val NEWPOST = "newpost"

    fun category(url: String, name: String) =
        "category?url=${Uri.encode(url)}&name=${Uri.encode(name)}"

    fun detail(postId: Long) = "detail/$postId"

    fun userMessages(tab: String? = null) = "user/messages?tab=${tab.orEmpty()}"

    fun userPanel(panel: String) = "user/panel/$panel"

    fun author(authorId: Long, tab: String? = null) = "author/$authorId?tab=${tab.orEmpty()}"

    fun chat(userId: Long) = "chat/$userId"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "首页", Icons.Outlined.Home),
    Tab(Routes.CATEGORIES, "分类", Icons.Outlined.Category),
    Tab(Routes.SEARCH, "搜索", Icons.Outlined.Search),
    Tab(Routes.PROFILE, "我的", Icons.Outlined.Person),
)

/** 应用导航：底部四个主页面 + 详情 / 分类列表 / 登录 / 设置（压在整栈上方） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNav(app: SakuApp) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in tabs.map { it.route }

    fun openPost(card: SiteParser.PostCard) {
        val id = card.id
        if (id != null) navController.navigate(Routes.detail(id))
        else AndroidActions.openInBrowser(context, card.url)
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                ShortNavigationBar {
                    tabs.forEach { tab ->
                        ShortNavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navController.switchTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
        // insets 由各页面自己处理（顶栏/列表）；外层只负责底栏占位，避免双层状态栏叠加
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    app = app,
                    onOpenPost = ::openPost,
                    onOpenCategory = { term ->
                        navController.navigate(Routes.category(term.url, term.name))
                    },
                    onOpenNewPost = { navController.navigate(Routes.NEWPOST) },
                )
            }

            composable(Routes.CATEGORIES) {
                CategoriesScreen(
                    app = app,
                    onOpenCategory = { url, name ->
                        navController.navigate(Routes.category(url, name))
                    },
                )
            }

            composable(Routes.SEARCH) {
                SearchScreen(
                    app = app,
                    onOpenPost = ::openPost,
                    onOpenCategory = { term ->
                        navController.navigate(Routes.category(term.url, term.name))
                    },
                )
            }

            composable(Routes.PROFILE) {
                ProfileScreen(
                    app = app,
                    onOpenLogin = { navController.navigate(Routes.LOGIN) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenEdit = { navController.navigate(Routes.USER_EDIT) },
                    onOpenMessages = { navController.navigate(Routes.userMessages()) },
                    onOpenNewPost = { navController.navigate(Routes.NEWPOST) },
                    onOpenPanel = { panel ->
                        // 账户安全有独立页面（多步流程），其余面板共用 UserPanelScreen
                        if (panel == "account") navController.navigate(Routes.USER_ACCOUNT)
                        else navController.navigate(Routes.userPanel(panel))
                    },
                    onOpenAuthor = { authorId, tab ->
                        navController.navigate(Routes.author(authorId, tab))
                    },
                )
            }

            composable(Routes.USER_EDIT) {
                EditProfileScreen(app = app, onBack = { navController.popBackStack() })
            }

            composable(
                route = Routes.USER_MESSAGES,
                arguments = listOf(navArgument("tab") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                MessageCenterScreen(
                    app = app,
                    initialTab = entry.arguments?.getString("tab")?.ifBlank { null },
                    onBack = { navController.popBackStack() },
                    onOpenChat = { userId -> navController.navigate(Routes.chat(userId)) },
                )
            }

            composable(
                route = Routes.USER_PANEL,
                arguments = listOf(navArgument("panel") { type = NavType.StringType }),
            ) { entry ->
                UserPanelScreen(
                    app = app,
                    panel = entry.arguments?.getString("panel").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenPost = { postId -> navController.navigate(Routes.detail(postId)) },
                )
            }

            composable(Routes.USER_ACCOUNT) {
                AccountSecurityScreen(app = app, onBack = { navController.popBackStack() })
            }

            composable(
                route = Routes.AUTHOR,
                arguments = listOf(
                    navArgument("authorId") { type = NavType.LongType },
                    navArgument("tab") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val authorId = entry.arguments?.getLong("authorId") ?: return@composable
                AuthorScreen(
                    app = app,
                    authorId = authorId,
                    initialTab = entry.arguments?.getString("tab")?.ifBlank { null },
                    onBack = { navController.popBackStack() },
                    onOpenPost = { postId -> navController.navigate(Routes.detail(postId)) },
                    onOpenChat = { userId -> navController.navigate(Routes.chat(userId)) },
                    onOpenCategory = { term ->
                        navController.navigate(Routes.category(term.url, term.name))
                    },
                )
            }

            composable(
                route = Routes.CHAT,
                arguments = listOf(navArgument("userId") { type = NavType.LongType }),
            ) { entry ->
                val userId = entry.arguments?.getLong("userId") ?: return@composable
                ChatScreen(app = app, userId = userId, onBack = { navController.popBackStack() })
            }

            composable(Routes.NEWPOST) {
                NewPostScreen(
                    app = app,
                    onBack = { navController.popBackStack() },
                    onOpenLogin = { navController.navigate(Routes.LOGIN) },
                    onOpenPost = { postId -> navController.navigate(Routes.detail(postId)) },
                )
            }

            composable(
                route = Routes.CATEGORY,
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType; defaultValue = "" },
                    navArgument("name") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val url = entry.arguments?.getString("url").orEmpty()
                val name = entry.arguments?.getString("name").orEmpty()
                CategoryScreen(
                    app = app,
                    categoryUrl = url,
                    categoryName = name,
                    onBack = { navController.popBackStack() },
                    onOpenPost = ::openPost,
                    onOpenCategory = { term ->
                        navController.navigate(Routes.category(term.url, term.name))
                    },
                )
            }

            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("postId") { type = NavType.LongType }),
            ) { entry ->
                val postId = entry.arguments?.getLong("postId") ?: return@composable
                DetailScreen(
                    app = app,
                    postId = postId,
                    onBack = { navController.popBackStack() },
                    onOpenPost = { navController.navigate(Routes.detail(it)) },
                    onOpenAuthor = { navController.navigate(Routes.author(it)) },
                    onOpenChat = { navController.navigate(Routes.chat(it)) },
                    onOpenCategory = { name, url ->
                        navController.navigate(Routes.category(url, name))
                    },
                    onOpenLogin = { navController.navigate(Routes.LOGIN) },
                )
            }

            composable(Routes.LOGIN) {
                LoginScreen(
                    app = app,
                    onBack = { navController.popBackStack() },
                    onOpenSignup = { navController.navigate(Routes.SIGNUP) },
                    onOpenReset = { navController.navigate(Routes.RESET_PASSWORD) },
                )
            }

            composable(Routes.SIGNUP) {
                SignupScreen(
                    app = app,
                    onBack = { navController.popBackStack() },
                    onRegistered = { autoLoggedIn ->
                        if (autoLoggedIn) {
                            // 站点注册后自动登录（与网页注册后刷新即是登录态一致）：
                            // 连同登录页一起退出，回到来处（此时已是登录状态）
                            navController.popBackStack(Routes.LOGIN, inclusive = true)
                        } else {
                            navController.popBackStack() // 回到登录页，用新账号登录
                        }
                    },
                )
            }

            composable(Routes.RESET_PASSWORD) {
                ResetPasswordScreen(
                    app = app,
                    onBack = { navController.popBackStack() },
                    onResetSuccess = { autoLoggedIn ->
                        if (autoLoggedIn) {
                            navController.popBackStack(Routes.LOGIN, inclusive = true)
                        } else {
                            navController.popBackStack() // 回到登录页，用新密码登录
                        }
                    },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    app = app,
                    onBack = { navController.popBackStack() },
                    onOpenManual = { navController.navigate(Routes.MANUAL) },
                )
            }

            composable(Routes.MANUAL) {
                ManualScreen(
                    app = app,
                    onBack = { navController.popBackStack() },
                    onOpenPost = { postId -> navController.navigate(Routes.detail(postId)) },
                )
            }
        }
    }
}

/** 底部导航切换：保持各标签自己的滚动位置，避免重复堆栈 */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
