package top.sakuraxy.client.ui.category

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import top.sakuraxy.client.SakuApp
import top.sakuraxy.client.core.CategoryTree
import top.sakuraxy.client.ui.components.CenterLoading
import top.sakuraxy.client.ui.components.ErrorRetry
import top.sakuraxy.client.ui.components.SakuTopAppBar
import top.sakuraxy.client.vm.CategoriesViewModel

/** 分类页：站点完整分类树（含二级、三级嵌套），点击进入分类列表 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    app: SakuApp,
    onOpenCategory: (path: String, name: String) -> Unit,
) {
    val vm: CategoriesViewModel = viewModel { CategoriesViewModel(app) }
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = { SakuTopAppBar(title = "分类") },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        when {
            state.loading -> CenterLoading(Modifier.padding(padding))
            state.error != null -> ErrorRetry(state.error!!, vm::load, Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(state.tree, key = { it.term.id }) { node ->
                    CategoryRow(node, depth = 0, onOpenCategory = onOpenCategory)
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    node: CategoryTree.Node,
    depth: Int,
    onOpenCategory: (String, String) -> Unit,
) {
    val url = node.term.link ?: node.term.slug
    val topLevel = depth == 0

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (depth * 18).dp)
                .clickable { onOpenCategory(url, node.term.name) },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            color = if (topLevel) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surface
            },
        ) {
            Row(
                modifier = Modifier.padding(
                    start = if (topLevel) 14.dp else 10.dp,
                    end = 12.dp,
                    top = if (topLevel) 12.dp else 8.dp,
                    bottom = if (topLevel) 12.dp else 8.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (topLevel) {
                    Icon(
                        Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                } else {
                    Icon(
                        Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = node.term.name,
                    style = if (topLevel) MaterialTheme.typography.titleSmall
                    else MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${node.term.count} 篇",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "进入分类",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        node.children.forEach { child ->
            CategoryRow(child, depth + 1, onOpenCategory)
        }
    }
}
