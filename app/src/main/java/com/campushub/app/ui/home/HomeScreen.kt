package com.campushub.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campushub.app.core.plugin.CardState
import com.campushub.app.core.plugin.GlanceTarget
import com.campushub.app.core.service.ServiceRegistry
import com.campushub.app.ui.components.CampusButton
import com.campushub.app.ui.components.CampusPullRefresh
import com.campushub.app.ui.components.CampusTopBar
import com.campushub.app.ui.components.EmptyState
import com.campushub.app.ui.components.clickableNoIndication
import com.campushub.app.ui.dashboard.DashboardViewModel
import com.campushub.app.ui.home.bento.InteractiveBentoSection
import com.campushub.app.ui.theme.CampusTheme
import com.campushub.app.ui.theme.CampusTokens

/**
 * 控制台（首页）。
 *
 * 结构：品牌栏（含主题切换）→ 便当盒网格 → 未接入条 → 「＋ 添加插件」。
 * 所有卡片数据来自插件声明与 glance，宿主不认识具体服务。
 */
@Composable
fun HomeScreen(
    viewModel: DashboardViewModel,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onOpenDetail: (pluginId: String, pageId: String) -> Unit,
    onOpenLogin: (pluginId: String) -> Unit,
    onOpenPlugins: (segment: String) -> Unit,
    onThemeButtonPosition: (Offset) -> Unit = {}
) {
    val palette = CampusTheme.palette
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val specs = remember(uiState.cardLayout) { viewModel.allCardSpecs().associateBy { it.id } }
    var cardBounds by remember { mutableStateOf<Map<String, Rect>>(emptyMap()) }

    /**
     * 卡片动作分发。
     *
     * 只处理契约里定义的通用目标（进详情 / 去登录）。`Wallet` 与 `Command`
     * 需要宿主侧的页面或命令实现，公开壳没有对应能力，因此忽略——插件不应
     * 依赖它们，详见 `docs/plugin-development.md` 的「宿主命令」一节。
     */
    fun dispatch(cardId: String, target: GlanceTarget) {
        when (target) {
            is GlanceTarget.Page -> {
                val pluginId = viewModel.pluginIdOfCard(cardId) ?: return
                onOpenDetail(pluginId, target.pageId)
            }
            GlanceTarget.Login -> {
                val pluginId = viewModel.pluginIdOfCard(cardId) ?: return
                onOpenLogin(pluginId)
            }
            GlanceTarget.Wallet -> Unit
            is GlanceTarget.Command -> Unit
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshSourceGlances()
    }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeError()
        }
    }

    val visibleCards = uiState.cardLayout.filter { it.visible }
    val boundCards = visibleCards.filter { LiveGlances.of(it.id, uiState, specs).state != CardState.UNBOUND }
    val unboundCards = visibleCards.filter { LiveGlances.of(it.id, uiState, specs).state == CardState.UNBOUND }

    // 编辑模式下按返回键直接退出编辑模式
    BackHandler(enabled = uiState.isEditBentoMode) {
        viewModel.toggleEditBentoMode()
    }

    var isInteractingWithGrid by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        CampusPullRefresh(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.loadAccountAndData(showRefreshing = true) },
            modifier = Modifier
                .fillMaxSize()
                .background(palette.canvas)
        ) {
            LazyColumn(
                userScrollEnabled = !isInteractingWithGrid,
                modifier = Modifier
                    .fillMaxSize()
                    .background(CampusTheme.palette.canvas)
                    .pointerInput(uiState.isEditBentoMode) {
                        if (!uiState.isEditBentoMode) return@pointerInput
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            val up = waitForUpOrCancellation()
                            if (up != null && !up.isConsumed) {
                                up.consume()
                                viewModel.toggleEditBentoMode()
                            }
                        }
                    },
                contentPadding = PaddingValues(
                    start = CampusTokens.Space.pageMargin,
                    end = CampusTokens.Space.pageMargin,
                    top = 0.dp,
                    bottom = CampusTokens.Space.bottomReserve + 56.dp
                )
            ) {
                item(key = "top_bar") {
                    CampusTopBar(title = "校园百宝箱", brand = true) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(
                                    palette.surface,
                                    androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                                )
                                .onGloballyPositioned { coords ->
                                    onThemeButtonPosition(coords.boundsInRoot().center)
                                }
                                .clickableNoIndication(onToggleTheme),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                contentDescription = if (isDark) "切换到浅色" else "切换到深色",
                                tint = palette.ink2,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                if (boundCards.isNotEmpty()) {
                    item(key = "bento") {
                        InteractiveBentoSection(
                            cards = boundCards,
                            glanceFor = { LiveGlances.of(it, uiState, specs) },
                            specFor = { specs[it] ?: error("unknown card $it") },
                            onTarget = { cardId, target -> dispatch(cardId, target) },
                            isEditMode = uiState.isEditBentoMode,
                            onEnterEditMode = viewModel::enterEditBentoMode,
                            onSetCardSize = { id, size -> viewModel.setCardSize(id, size) },
                            onSwapCards = viewModel::swapBentoCards,
                            onHideCard = { id -> viewModel.setCardVisible(id, false) },
                            onCardBounds = { cardBounds = it },
                            onUpdateLayout = viewModel::updateCardLayout,
                            onInteractingChanged = { isInteractingWithGrid = it },
                            onExitEditMode = { viewModel.toggleEditBentoMode() },
                            modifier = Modifier.padding(top = 14.dp)
                        )
                    }
                }

                if (unboundCards.isNotEmpty()) {
                    item(key = "unbound_header") {
                        Spacer(modifier = Modifier.height(CampusTokens.Space.sectionGap))
                    }
                    items(unboundCards.size, key = { "unbound_${unboundCards[it].id}" }) { index ->
                        val entry = unboundCards[index]
                        val spec = specs[entry.id] ?: return@items
                        val service = ServiceRegistry.get(viewModel.pluginIdOfCard(entry.id).orEmpty())
                        val glance = LiveGlances.of(entry.id, uiState, specs)
                        UnboundStrip(
                            spec = spec,
                            serviceName = service?.manifest?.name ?: "",
                            description = glance.sub ?: service?.manifest?.description ?: "",
                            onClick = {
                                viewModel.pluginIdOfCard(entry.id)?.let(onOpenLogin)
                            },
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                if (boundCards.isEmpty() && unboundCards.isEmpty()) {
                    item(key = "empty") {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            EmptyState(
                                title = "还没有任何插件",
                                body = "去「插件」页把要用的加回来。"
                            )
                            CampusButton(
                                label = "去插件页",
                                onClick = { onOpenPlugins("available") },
                                style = com.campushub.app.core.plugin.ActionStyle.SECONDARY
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
