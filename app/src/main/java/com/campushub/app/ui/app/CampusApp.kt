package com.campushub.app.ui.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.campushub.app.core.plugin.ConfigurablePlugin
import com.campushub.app.core.service.ServiceRegistry
import com.campushub.app.core.settings.AppSettingsRepository
import com.campushub.app.core.settings.ThemeMode
import com.campushub.app.ui.account.AccountScreen
import com.campushub.app.ui.components.LocalLargeFont
import com.campushub.app.ui.dashboard.DashboardViewModel
import com.campushub.app.ui.detail.DetailScreen
import com.campushub.app.ui.home.HomeScreen
import com.campushub.app.ui.login.LoginScreen
import com.campushub.app.ui.plugins.PluginsScreen
import com.campushub.app.ui.theme.CampusTokens
import kotlinx.coroutines.launch

/**
 * 应用根组件：负责导航、胶囊导航栏与三个标签页。
 *
 * 三个标签页 + 两个二级页；插件不新增页面，只往首页网格与插件列表里塞数据。
 */
@Composable
fun CampusApp(
    dashboardVm: DashboardViewModel,
    settings: AppSettingsRepository,
    onThemeButtonPosition: (androidx.compose.ui.geometry.Offset) -> Unit = {}
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val themeMode by settings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val uiState by dashboardVm.uiState.collectAsStateWithLifecycle()

    val largeFont = LocalDensity.current.fontScale >= CampusTokens.Card.largeFontThreshold

    val isHome = currentDestination?.hasRoute<HomeRoute>() == true
    val loopMultiplier = 1000
    val virtualPageCount = CampusTab.entries.size * loopMultiplier
    val initialPage = (loopMultiplier / 2) * CampusTab.entries.size
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { virtualPageCount })
    var pluginsRequest by rememberSaveable { mutableStateOf("enabled" to 0) }

    CompositionLocalProvider(LocalLargeFont provides largeFont) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(navController = navController, startDestination = HomeRoute) {
                composable<HomeRoute> {
                    val currentTab = CampusTab.entries[Math.floorMod(pagerState.currentPage, CampusTab.entries.size)]
                    val isNotAtHome = currentTab != CampusTab.HOME
                    BackHandler(enabled = isNotAtHome) {
                        val currentMod = Math.floorMod(pagerState.currentPage, CampusTab.entries.size)
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - currentMod) }
                    }

                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = !uiState.isEditBentoMode,
                        modifier = Modifier.fillMaxSize()
                    ) { pageIndex ->
                        when (Math.floorMod(pageIndex, CampusTab.entries.size)) {
                            0 -> HomeScreen(
                                viewModel = dashboardVm,
                                isDark = themeMode == ThemeMode.DARK,
                                onToggleTheme = {
                                    val next = if (themeMode == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
                                    scope.launch { settings.setThemeMode(next) }
                                },
                                onOpenDetail = { pluginId, pageId ->
                                    navController.navigate(DetailRoute(pluginId, pageId))
                                },
                                onOpenLogin = { pluginId -> navController.navigate(LoginRoute(pluginId)) },
                                onOpenPlugins = { segment ->
                                    pluginsRequest = segment to (pluginsRequest.second + 1)
                                    val currentMod = Math.floorMod(pagerState.currentPage, CampusTab.entries.size)
                                    val diff = CampusTab.PLUGINS.ordinal - currentMod
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + diff) }
                                },
                                onThemeButtonPosition = onThemeButtonPosition
                            )
                            1 -> PluginsScreen(
                                viewModel = dashboardVm,
                                initialSegment = pluginsRequest.first,
                                onOpenLogin = { pluginId -> navController.navigate(LoginRoute(pluginId)) },
                                requestNonce = pluginsRequest.second
                            )
                            2 -> AccountScreen(
                                viewModel = dashboardVm,
                                themeMode = themeMode,
                                onSetThemeMode = { mode -> scope.launch { settings.setThemeMode(mode) } },
                                onOpenLogin = { pluginId -> navController.navigate(LoginRoute(pluginId)) }
                            )
                        }
                    }
                }

                composable<DetailRoute> { entry ->
                    val route = entry.toRoute<DetailRoute>()
                    DetailScreen(
                        pluginId = route.pluginId,
                        initialPageId = route.pageId,
                        onBack = { navController.popBackStack() },
                        onOpenLogin = { pluginId -> navController.navigate(LoginRoute(pluginId)) }
                    )
                }

                composable<LoginRoute> { entry ->
                    val route = entry.toRoute<LoginRoute>()
                    val plugin = ServiceRegistry.get(route.pluginId) as? ConfigurablePlugin
                    if (plugin == null) {
                        LaunchedEffect(route.pluginId) { navController.popBackStack() }
                    } else {
                        LoginScreen(
                            plugin = plugin,
                            addAccount = route.addAccount,
                            initialGroupId = route.groupId,
                            viewModelKey = "login_${plugin.id}_${entry.id}",
                            onBack = { navController.popBackStack() },
                            onLoggedIn = { dashboardVm.refreshAfterLogin() }
                        )
                    }
                }
            }

            if (isHome) {
                val currentTab = CampusTab.entries[Math.floorMod(pagerState.currentPage, CampusTab.entries.size)]
                val tabFraction = Math.floorMod(pagerState.currentPage, CampusTab.entries.size) +
                    pagerState.currentPageOffsetFraction
                CapsuleNavBar(
                    selected = currentTab,
                    fraction = tabFraction,
                    onSelect = { tab ->
                        val currentMod = Math.floorMod(pagerState.currentPage, CampusTab.entries.size)
                        val diff = tab.ordinal - currentMod
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + diff) }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}
