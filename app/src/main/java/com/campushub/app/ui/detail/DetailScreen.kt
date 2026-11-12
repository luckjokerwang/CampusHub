package com.campushub.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.campushub.app.core.plugin.DetailNavIcon
import com.campushub.app.core.plugin.DetailPage
import com.campushub.app.core.plugin.DetailSource
import com.campushub.app.core.plugin.PageAction
import com.campushub.app.core.plugin.PluginAccent
import com.campushub.app.core.service.ServiceRegistry
import com.campushub.app.core.service.ServiceResult
import com.campushub.app.ui.app.CapsuleNavItem
import com.campushub.app.ui.app.GenericCapsuleNavBar
import com.campushub.app.ui.components.ButtonSize
import com.campushub.app.ui.components.CampusButton
import com.campushub.app.ui.components.CampusPullRefresh
import com.campushub.app.ui.components.PageHeader
import com.campushub.app.ui.components.color
import com.campushub.app.ui.components.soft
import com.campushub.app.ui.theme.CampusTheme
import com.campushub.app.ui.theme.CampusTokens
import kotlinx.coroutines.launch

/**
 * 详情页：`HorizontalPager` 横滑遍历某个插件的全部详情页。
 *
 * 数据全部来自插件的 [DetailSource]；宿主只负责渲染四种段落
 * （KPI / 图表 / 记录流 / 表单）与把命令回传给插件。
 */
@Composable
fun DetailScreen(
    pluginId: String,
    initialPageId: String,
    onBack: () -> Unit,
    onOpenLogin: (String) -> Unit
) {
    val service = ServiceRegistry.get(pluginId)
    val accent = service?.manifest?.accent ?: PluginAccent.NEUTRAL
    val source = service as? DetailSource

    if (source == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CampusTheme.palette.canvas)
        ) {
            PageHeader(title = service?.manifest?.name ?: "详情", onBack = onBack)
            Text(
                text = "该服务没有详情页",
                style = MaterialTheme.typography.bodySmall,
                color = CampusTheme.palette.ink3,
                modifier = Modifier.padding(CampusTokens.Space.pageMargin)
            )
        }
        return
    }

    var pages by remember(pluginId) { mutableStateOf<List<DetailPage>>(emptyList()) }
    var loading by remember(pluginId) { mutableStateOf(true) }
    var error by remember(pluginId) { mutableStateOf<String?>(null) }
    var unauthorized by remember(pluginId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loading = true
            error = null
            unauthorized = false
            when (val result = source.detailPages()) {
                is ServiceResult.Ok -> pages = result.value
                is ServiceResult.NetworkError -> error = result.message
                is ServiceResult.Unauthorized -> {
                    unauthorized = true
                    error = result.message
                }
                is ServiceResult.BusinessError -> error = result.message
            }
            loading = false
        }
    }

    LaunchedEffect(pluginId) { reload() }

    // 从登录页返回时自动刷新，避免看到旧数据
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reload() }

    when {
        loading && pages.isEmpty() -> Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(title = service.manifest.name, onBack = onBack)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("正在加载…", style = MaterialTheme.typography.bodySmall)
            }
        }

        unauthorized -> Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(title = service.manifest.name, onBack = onBack)
            Column(
                modifier = Modifier.padding(CampusTokens.Space.pageMargin),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = error ?: "登录已失效",
                    style = MaterialTheme.typography.bodySmall,
                    color = CampusTheme.palette.ink2
                )
                CampusButton(
                    label = "去登录",
                    onClick = { onOpenLogin(pluginId) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        error != null && pages.isEmpty() -> Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(title = service.manifest.name, onBack = onBack)
            Column(
                modifier = Modifier.padding(CampusTokens.Space.pageMargin),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = CampusTheme.palette.danger
                )
                CampusButton(
                    label = "重试",
                    onClick = { reload() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        else -> DetailPager(
            pages = pages,
            initialPageId = initialPageId,
            accent = accent,
            onBack = onBack,
            isRefreshing = loading,
            onRefresh = { reload() },
            onCommand = { commandId, rowId, input ->
                scope.launch {
                    val result = source.onDetailCommand(commandId, rowId, input)
                    if (result is ServiceResult.Ok) reload()
                }
            },
            onFormChange = { _, _ -> }
        )
    }
}

private fun navIconOf(icon: DetailNavIcon) = when (icon) {
    DetailNavIcon.STATS -> Icons.Outlined.BarChart
    DetailNavIcon.DEVICES -> Icons.Outlined.Tune
    DetailNavIcon.TASKS -> Icons.Outlined.TaskAlt
    DetailNavIcon.ACCOUNT -> Icons.Outlined.AccountBalanceWallet
    DetailNavIcon.CREDIT -> Icons.Outlined.School
    DetailNavIcon.OVERVIEW -> Icons.Outlined.Apps
}

@Composable
private fun DetailPager(
    pages: List<DetailPage>,
    initialPageId: String,
    accent: PluginAccent,
    onBack: () -> Unit,
    onCommand: (String, String?, String?) -> Unit,
    onFormChange: (String, String) -> Unit,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    val palette = CampusTheme.palette
    val scope = rememberCoroutineScope()
    if (pages.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(title = "详情", onBack = onBack)
        }
        return
    }
    val initialIndex = pages.indexOfFirst { it.id == initialPageId }.takeIf { it >= 0 } ?: 0
    val isLooping = pages.size > 1
    val loopMultiplier = if (isLooping) 1000 else 1
    val virtualPageCount = pages.size * loopMultiplier
    val initialPage = if (isLooping) {
        (loopMultiplier / 2) * pages.size + initialIndex
    } else {
        0
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { virtualPageCount }
    val currentRealIndex = Math.floorMod(pagerState.currentPage, pages.size)
    val current = pages[currentRealIndex]
    val navItems = remember(pages) {
        pages.map { page ->
            CapsuleNavItem(
                id = page.id,
                label = page.navLabel,
                icon = navIconOf(page.navIcon)
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(
                title = current.title,
                onBack = onBack,
                action = current.headerAction,
                onAction = { action: PageAction -> onCommand(action.commandId, null, null) }
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { virtualIndex ->
                val realIndex = Math.floorMod(virtualIndex, pages.size)
                val page = pages[realIndex]
                CampusPullRefresh(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = CampusTokens.Space.pageMargin,
                            end = CampusTokens.Space.pageMargin,
                            top = 4.dp,
                            bottom = CampusTokens.Space.bottomReserve + 32.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(CampusTokens.Space.sectionGap)
                    ) {
                        items(page.sections.size, key = { "${page.id}_${it}" }) { sectionIndex ->
                            DetailSectionContent(
                                section = page.sections[sectionIndex],
                                accent = accent,
                                onCommand = onCommand,
                                onFormChange = onFormChange
                            )
                        }
                        page.note?.let { note ->
                            item(key = "${page.id}_note") {
                                Text(
                                    text = note,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.ink3
                                )
                            }
                        }
                        if (page.actions.isNotEmpty()) {
                            item(key = "${page.id}_actions") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    page.actions.forEachIndexed { index, action ->
                                        CampusButton(
                                            label = action.label,
                                            onClick = { onCommand(action.commandId, null, null) },
                                            style = action.style,
                                            size = ButtonSize.LG,
                                            loading = action.busy,
                                            modifier = if (index == 0) {
                                                Modifier.weight(1f)
                                            } else {
                                                Modifier.width(96.dp)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        GenericCapsuleNavBar(
            items = navItems,
            selectedIndex = currentRealIndex,
            fraction = currentRealIndex + pagerState.currentPageOffsetFraction,
            onSelect = { targetIndex ->
                if (isLooping) {
                    val diff = targetIndex - currentRealIndex
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + diff) }
                } else {
                    scope.launch { pagerState.animateScrollToPage(targetIndex) }
                }
            },
            accentColor = accent.color(),
            accentSoftColor = accent.soft(),
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
