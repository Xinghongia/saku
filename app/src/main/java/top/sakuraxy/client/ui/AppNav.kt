package top.sakuraxy.client.ui

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import top.sakuraxy.client.ui.category.CategoriesScreen
import top.sakuraxy.client.ui.category.CategoryScreen
import top.sakuraxy.client.ui.detail.DetailScreen
import top.sakuraxy.client.ui.home.HomeScreen
import top.sakuraxy.client.ui.profile.ProfileScreen
import top.sakuraxy.client.ui.search.SearchScreen

private object Routes {
    const val HOME = "home"
    const val CATEGORIES = "categories"
    const val SEARCH = "search"
    const val PROFILE = "profile"
    const val CATEGORY = "category?url={url}&name={name}"
    const val DETAIL = "detail/{postId}"
    const val LOGIN = "login"

    fun category(url: String, name: String) =
        "category?url=${Uri.encode(url)}&name=${Uri.encode(name)}"

    fun detail(postId: Long) = "detail/$postId"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "首页", Icons.Outlined.Home),
    Tab(Routes.CATEGORIES, "分类", Icons.Outlined.Category),
    Tab(Routes.SEARCH, "搜索", Icons.Outlined.Search),
    Tab(Routes.PROFILE, "我的", Icons.Outlined.Person),
)

/** 应用导航：底部四个主页面 + 详情 / 分类列表 / 登录（压在整栈上方） */
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
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navController.switchTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    app = app,
                    onOpenPost = ::openPost,
                    onOpenCategory = { term ->
                        navController.navigate(Routes.category(term.url, term.name))
                    },
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
                ProfileScreen(app = app, onOpenLogin = { navController.navigate(Routes.LOGIN) })
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
                    onOpenCategory = { name, url ->
                        navController.navigate(Routes.category(url, name))
                    },
                    onOpenLogin = { navController.navigate(Routes.LOGIN) },
                )
            }

            composable(Routes.LOGIN) {
                LoginScreen(app = app, onBack = { navController.popBackStack() })
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
