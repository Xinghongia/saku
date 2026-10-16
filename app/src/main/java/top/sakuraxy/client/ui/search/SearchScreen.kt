package top.sakuraxy.client.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
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
import top.sakuraxy.client.vm.SearchViewModel

/** 搜索：站点搜索结果（卡片与列表页一致；常驻翻页条与列表页同一套分页模型） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    app: SakuApp,
    onOpenPost: (SiteParser.PostCard) -> Unit,
    onOpenCategory: (SiteParser.CardTerm) -> Unit,
) {
    val vm: SearchViewModel = viewModel { SearchViewModel(app) }
    val state by vm.state.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
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
        topBar = { SakuTopAppBar(title = "搜索") },
        bottomBar = {
            if (state.searched && maxPage != null && maxPage > 1 && state.cards.isNotEmpty()) {
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
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::onQueryChange,
                    placeholder = { Text("输入关键词…") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    shape = RoundedCornerShape(50),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboard?.hide()
                        vm.search()
                    }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        keyboard?.hide()
                        vm.search()
                    },
                    enabled = state.query.isNotBlank() && !state.loading,
                ) { Text("搜索") }
            }

            when {
                state.loading -> CenterLoading()
                state.error != null -> ErrorRetry(state.error!!, vm::search)
                state.searched && state.cards.isEmpty() -> EmptyHint("没有找到相关内容")
                !state.searched -> EmptyHint("输入关键词搜索资源")
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
                            loading = state.loadingMore,
                            canLoadMore = state.canLoadMore,
                            error = null,
                            page = state.page,
                            maxPage = maxPage,
                            onLoadMore = vm::loadMore,
                            onRetry = vm::search,
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
