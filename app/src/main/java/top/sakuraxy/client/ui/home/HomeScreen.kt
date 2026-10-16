package top.sakuraxy.client.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import top.sakuraxy.client.SakuApp
import top.sakuraxy.client.core.SiteParser
import top.sakuraxy.client.ui.components.CenterLoading
import top.sakuraxy.client.ui.components.EmptyHint
import top.sakuraxy.client.ui.components.ErrorRetry
import top.sakuraxy.client.ui.components.ListFooter
import top.sakuraxy.client.ui.components.PageJumpDialog
import top.sakuraxy.client.ui.components.PostCardItem
import top.sakuraxy.client.ui.components.SakuTopAppBar
import top.sakuraxy.client.ui.components.SortMenuButton
import top.sakuraxy.client.vm.SiteListViewModel

/** 排序选项：null = 站点默认（推荐） */
internal val listSortOptions =
    listOf(null to "推荐") + SiteParser.knownSortOptions.map { it.key to it.label }

/** 首页：站点最新/热门资源列表（卡片 + 排序下拉 + 分页 + 登录后显示发布入口） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    app: SakuApp,
    onOpenPost: (SiteParser.PostCard) -> Unit,
    onOpenCategory: (SiteParser.CardTerm) -> Unit,
    onOpenNewPost: () -> Unit,
) {
    val vm: SiteListViewModel = viewModel { SiteListViewModel(app) }
    val state by vm.state.collectAsState()
    var jumpDialog by remember { mutableStateOf(false) }
    // 登录态变化（session.revision 自增）时重算「+」入口显隐
    val sessionRevision by app.client.session.revision.collectAsState()
    val loggedIn = remember(sessionRevision) { app.client.session.isLoggedIn() }

    Scaffold(
        topBar = {
            SakuTopAppBar(
                title = "SakuraXy",
                subtitle = "玄夜の资源小站",
                actions = {
                    // 与站点浮动「发布」按钮一致：登录后才显示（对应 /newposts）
                    if (loggedIn) {
                        IconButton(onClick = onOpenNewPost) {
                            Icon(Icons.Outlined.Add, contentDescription = "发布内容")
                        }
                    }
                    SortMenuButton(
                        options = listSortOptions,
                        selected = state.sort,
                        onSelect = vm::setSort,
                    )
                },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {            when {
                state.error != null && state.cards.isEmpty() -> ErrorRetry(state.error!!, vm::retry)
                state.cards.isEmpty() && state.loading -> CenterLoading()
                state.cards.isEmpty() -> EmptyHint("这里还没有内容")
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.cards, key = { it.url }) { card ->
                        PostCardItem(
                            card = card,
                            onClick = { onOpenPost(card) },
                            onCategoryClick = onOpenCategory,
                        )
                    }
                    item {
                        ListFooter(
                            loading = state.loading || state.loadingMore,
                            canLoadMore = state.canLoadMore,
                            error = state.error,
                            page = state.page,
                            maxPage = state.maxPage,
                            onLoadMore = vm::loadMore,
                            onRetry = vm::retry,
                            onJump = { jumpDialog = true },
                        )
                    }
                }
            }
        }
    }

    if (jumpDialog) {
        PageJumpDialog(
            maxPage = state.maxPage,
            onDismiss = { jumpDialog = false },
            onJump = vm::jumpTo,
        )
    }
}
