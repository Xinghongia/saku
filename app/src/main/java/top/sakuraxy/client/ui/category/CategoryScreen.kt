package top.sakuraxy.client.ui.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import top.sakuraxy.client.ui.home.listSortOptions
import top.sakuraxy.client.vm.SiteListViewModel

/** 分类列表页：分类内文章（卡片 + 排序下拉 + 常驻翻页条），顶栏显示分类名与篇数 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    app: SakuApp,
    categoryUrl: String,
    categoryName: String,
    onBack: () -> Unit,
    onOpenPost: (SiteParser.PostCard) -> Unit,
    onOpenCategory: (SiteParser.CardTerm) -> Unit,
) {
    val vm: SiteListViewModel = viewModel(key = "category-$categoryUrl") {
        SiteListViewModel(app, categoryUrl)
    }
    val state by vm.state.collectAsState()
    var picker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val visiblePage by remember(state.window) {
        derivedStateOf { state.window.pageAt(listState.firstVisibleItemIndex) }
    }
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
    val maxPage = state.maxPage

    Scaffold(
        topBar = {
            SakuTopAppBar(
                title = state.header?.name ?: categoryName,
                subtitle = state.header?.postCountText,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
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
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
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
