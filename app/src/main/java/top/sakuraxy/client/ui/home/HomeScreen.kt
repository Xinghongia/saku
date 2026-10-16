package top.sakuraxy.client.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import top.sakuraxy.client.ui.components.PagePickerSheet
import top.sakuraxy.client.ui.components.PagerBar
import top.sakuraxy.client.ui.components.PostCardItem
import top.sakuraxy.client.ui.components.SakuTopAppBar
import top.sakuraxy.client.ui.components.SortMenuButton
import top.sakuraxy.client.vm.SiteListViewModel

/** 排序选项：null = 站点默认（推荐） */
internal val listSortOptions =
    listOf(null to "推荐") + SiteParser.knownSortOptions.map { it.key to it.label }

/** 首页：站点最新/热门资源列表（卡片 + 排序下拉 + 常驻翻页条 + 登录后显示发布入口） */
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
    var picker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // 当前可见页 = 列表第一个可见项落在哪一页（翻页条随滚动联动）
    val visiblePage by remember(state.window) {
        derivedStateOf { state.window.pageAt(listState.firstVisibleItemIndex) }
    }
    // 一次性滚动定位（翻页/加载新页后列表滚到该页首项）
    LaunchedEffect(state.scrollTarget) {
        state.scrollTarget?.let { target ->
            if (kotlin.math.abs(target - listState.firstVisibleItemIndex) <= 30) {
                listState.animateScrollToItem(target)
            } else {
                listState.scrollToItem(target)
            }
            vm.consumeScrollTarget()
        }
    }
    // 登录态变化（session.revision 自增）时重算「+」入口显隐
    val sessionRevision by app.client.session.revision.collectAsState()
    val loggedIn = remember(sessionRevision) { app.client.session.isLoggedIn() }
    val maxPage = state.maxPage

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
        bottomBar = {
            if (maxPage != null && maxPage > 1 && state.cards.isNotEmpty()) {
                PagerBar(
                    currentPage = visiblePage,
                    maxPage = maxPage,
                    loading = state.loading || state.loadingMore,
                    onPrev = { vm.prevPage(visiblePage) },
                    onNext = { vm.nextPage(visiblePage) },
                    onOpenPicker = { picker = true },
                )
            }
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
                    state = listState,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.cards, key = { it.url }) { card ->
                        PostCardItem(
                            card = card,
                            onClick = { onOpenPost(card) },
                            onTermClick = onOpenCategory,
                        )
                    }
                    item {
                        ListFooter(
                            loading = state.loading || state.loadingMore,
                            canLoadMore = state.canLoadMore,
                            error = state.error,
                            page = state.page,
                            maxPage = maxPage,
                            onLoadMore = vm::loadMore,
                            onRetry = vm::retry,
                            onJump = { picker = true },
                        )
                    }
                }
            }
        }
    }

    if (picker && maxPage != null) {
        PagePickerSheet(
            currentPage = visiblePage,
            maxPage = maxPage,
            onDismiss = { picker = false },
            onPick = { page ->
                picker = false
                vm.jumpTo(page)
            },
        )
    }
}
