package com.campushub.app.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campushub.app.BuildConfig
import com.campushub.app.core.plugin.ConfigurablePlugin
import com.campushub.app.core.service.ServiceRegistry
import com.campushub.app.core.settings.ThemeMode
import com.campushub.app.ui.components.ButtonSize
import com.campushub.app.ui.components.CampusButton
import com.campushub.app.ui.components.CampusTopBar
import com.campushub.app.ui.components.ListGroup
import com.campushub.app.ui.components.ListRow
import com.campushub.app.ui.components.ListRowDivider
import com.campushub.app.ui.components.SectionLabel
import com.campushub.app.ui.components.clickableNoIndication
import com.campushub.app.ui.dashboard.DashboardViewModel
import com.campushub.app.ui.theme.CampusTheme
import com.campushub.app.ui.theme.CampusTokens

/**
 * 「我的」：账号、外观、关于、退出登录。
 *
 * 只放真实能力：没有后端支撑的开关一律不做。
 */
@Composable
fun AccountScreen(
    viewModel: DashboardViewModel,
    themeMode: ThemeMode,
    onSetThemeMode: (ThemeMode) -> Unit,
    onOpenLogin: (String) -> Unit
) {
    val palette = CampusTheme.palette
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    val plugins = remember { ServiceRegistry.all().filterIsInstance<ConfigurablePlugin>() }
    val configuredCount = plugins.count { plugin ->
        plugin.configGroups.isNotEmpty() &&
            plugin.configGroups.all { "${plugin.id}/${it.id}" in uiState.configuredGroups }
    }
    val displayName = if (configuredCount > 0) "$configuredCount 个插件已登录" else "点击登录校园账号"
    val subtitle = if (configuredCount > 0) {
        "凭据保存在本机加密存储，不会上传"
    } else {
        "登录后可同步插件数据"
    }
    val firstPluginId = plugins.firstOrNull()?.id

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvas),
        contentPadding = PaddingValues(
            start = CampusTokens.Space.pageMargin,
            end = CampusTokens.Space.pageMargin,
            bottom = CampusTokens.Space.bottomReserve + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "top") { CampusTopBar(title = "我的") }

        item(key = "profile") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surface, CampusTokens.Shapes.medium)
                    .clickableNoIndication { firstPluginId?.let(onOpenLogin) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(palette.waterSoft, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "校",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.water,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (configuredCount > 0) palette.ink else palette.water,
                        maxLines = 1
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(text = "›", color = palette.ink3)
            }
        }

        item(key = "account_label") { SectionLabel("账号") }
        item(key = "account_group") {
            if (plugins.isEmpty()) {
                Text(
                    text = "还没有可配置的插件。",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.ink3
                )
            } else {
                ListGroup {
                    plugins.forEachIndexed { index, plugin ->
                        if (index > 0) ListRowDivider()
                        val groups = plugin.configGroups
                        val configured = groups.count { "${plugin.id}/${it.id}" in uiState.configuredGroups }
                        val allConfigured = groups.isNotEmpty() && configured == groups.size
                        ListRow(
                            title = plugin.manifest.name,
                            subtitle = groups.joinToString(" · ") { group ->
                                "${group.label} ${if ("${plugin.id}/${group.id}" in uiState.configuredGroups) "已登录" else "未登录"}"
                            },
                            glyph = plugin.manifest.iconGlyph,
                            accent = plugin.manifest.accent,
                            onClick = { onOpenLogin(plugin.id) }
                        ) {
                            if (!allConfigured) {
                                Text(
                                    text = "去登录",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.water
                                )
                            } else {
                                Text(text = "›", color = palette.ink3)
                            }
                        }
                    }
                }
            }
        }

        item(key = "appearance_label") { SectionLabel("外观") }
        item(key = "appearance_group") {
            ListGroup {
                ListRow(
                    title = "深色模式",
                    subtitle = when (themeMode) {
                        ThemeMode.SYSTEM -> "跟随系统"
                        ThemeMode.LIGHT -> "浅色"
                        ThemeMode.DARK -> "深色"
                    },
                    onClick = { showThemeDialog = true }
                ) {
                    Text(text = "›", color = palette.ink3)
                }
            }
        }

        item(key = "data_label") { SectionLabel("关于") }
        item(key = "data_group") {
            ListGroup {
                ListRow(
                    title = "关于 CampusHub",
                    subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · 开源许可",
                    onClick = { showAbout = true }
                ) {
                    Text(text = "›", color = palette.ink3)
                }
            }
        }

        item(key = "logout") {
            Spacer(modifier = Modifier.height(8.dp))
            CampusButton(
                label = "退出登录",
                onClick = { showLogoutConfirm = true },
                style = com.campushub.app.core.plugin.ActionStyle.SECONDARY,
                size = ButtonSize.LG,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            containerColor = palette.surface,
            title = { Text("深色模式") },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickableNoIndication {
                                    onSetThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = mode == themeMode,
                                onClick = {
                                    onSetThemeMode(mode)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (mode) {
                                    ThemeMode.SYSTEM -> "跟随系统"
                                    ThemeMode.LIGHT -> "浅色"
                                    ThemeMode.DARK -> "深色"
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("关闭") }
            }
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            containerColor = palette.surface,
            title = { Text("关于 CampusHub") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    Text("非商业、不收取任何费用。")
                    Text(
                        "这是公开壳：只内置一个离线示例插件，不含任何真实校园服务实现。",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.ink3
                    )
                    Text(
                        "凭据保存在本机加密存储，不会上传。",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.ink3
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("关闭") }
            }
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            containerColor = palette.surface,
            title = { Text("退出登录") },
            text = { Text("退出所有插件账号？卡片布局会保留。") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    viewModel.logoutAll()
                }) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("取消") }
            }
        )
    }
}
