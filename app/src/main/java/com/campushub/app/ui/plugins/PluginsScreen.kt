package com.campushub.app.ui.plugins

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campushub.app.core.plugin.CardSpec
import com.campushub.app.core.plugin.ConfigurablePlugin
import com.campushub.app.core.service.CampusService
import com.campushub.app.core.service.ServiceRegistry
import com.campushub.app.ui.components.CampusTopBar
import com.campushub.app.ui.components.ChipTone
import com.campushub.app.ui.components.EmptyState
import com.campushub.app.ui.components.ListGroup
import com.campushub.app.ui.components.ListRow
import com.campushub.app.ui.components.ListRowDivider
import com.campushub.app.ui.components.SectionLabel
import com.campushub.app.ui.components.SegmentedControl
import com.campushub.app.ui.components.StatusChip
import com.campushub.app.ui.components.clickableNoIndication
import com.campushub.app.ui.dashboard.DashboardViewModel
import com.campushub.app.ui.theme.CampusTheme
import com.campushub.app.ui.theme.CampusTokens

/**
 * 插件页：已启用 / 可添加。
 *
 * 「启用」= 卡片在首页可见；停用不删除凭据，随时可以加回，布局照旧。
 * 自定义插件上传按规范只写文档、不放入口。
 */
@Composable
fun PluginsScreen(
    viewModel: DashboardViewModel,
    initialSegment: String,
    onOpenLogin: (pluginId: String) -> Unit,
    requestNonce: Int = 0
) {
    val palette = CampusTheme.palette
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val specs = remember(uiState.cardLayout) { viewModel.allCardSpecs().associateBy { it.id } }
    val enabled = uiState.cardLayout.filter { it.visible }
    val available = uiState.cardLayout.filter { !it.visible }
    var segment by remember(initialSegment, requestNonce) {
        mutableIntStateOf(if (initialSegment == "available") 1 else 0)
    }

    fun serviceOf(spec: CardSpec): CampusService? =
        ServiceRegistry.get(viewModel.pluginIdOfCard(spec.id).orEmpty())

    fun loginStatus(service: CampusService?): String {
        val plugin = service as? ConfigurablePlugin ?: return ""
        val groups = plugin.configGroups
        if (groups.isEmpty()) return ""
        return groups.joinToString(" · ") { group ->
            "${group.label} ${if ("${plugin.id}/${group.id}" in uiState.configuredGroups) "已登录" else "未登录"}"
        }
    }

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
        item(key = "top") {
            CampusTopBar(
                title = "插件",
                subtitle = "${enabled.size} 已启用 · ${available.size} 可添加"
            )
        }
        item(key = "seg") {
            SegmentedControl(
                items = listOf("已启用 ${enabled.size}", "可添加 ${available.size}"),
                selectedIndex = segment,
                onSelect = { segment = it }
            )
        }

        if (segment == 0) {
            if (enabled.isEmpty()) {
                item(key = "empty_enabled") {
                    EmptyState(
                        title = "首页没有卡片",
                        body = "到「可添加」把要用的插件加回来，布局与凭据都还在。"
                    )
                }
            } else {
                item(key = "enabled_group") {
                    ListGroup {
                        enabled.forEachIndexed { index, entry ->
                            if (index > 0) ListRowDivider()
                            val spec = specs[entry.id] ?: return@forEachIndexed
                            val service = serviceOf(spec)
                            ListRow(
                                title = spec.title,
                                subtitle = listOfNotNull(
                                    service?.manifest?.name,
                                    loginStatus(service).ifBlank { null }
                                ).joinToString(" · "),
                                glyph = spec.iconGlyph,
                                accent = spec.accent,
                                onClick = {
                                    viewModel.pluginIdOfCard(entry.id)?.let(onOpenLogin)
                                }
                            ) {
                                Switch(
                                    checked = true,
                                    onCheckedChange = { viewModel.setCardVisible(entry.id, false) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = palette.surface,
                                        checkedTrackColor = palette.water
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } else {
            if (available.isEmpty()) {
                item(key = "empty_available") {
                    EmptyState(
                        title = "全部插件都在首页了",
                        body = "把首页卡片拖进编辑模式可以隐藏它们，随时在这里加回。"
                    )
                }
            } else {
                item(key = "available_label") { SectionLabel("可添加") }
                item(key = "available_group") {
                    ListGroup {
                        available.forEachIndexed { index, entry ->
                            if (index > 0) ListRowDivider()
                            val spec = specs[entry.id] ?: return@forEachIndexed
                            val service = serviceOf(spec)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickableNoIndication { viewModel.setCardVisible(entry.id, true) }
                            ) {
                                ListRow(
                                    title = spec.title,
                                    subtitle = service?.manifest?.description,
                                    glyph = spec.iconGlyph,
                                    accent = spec.accent,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    StatusChip(text = "加回", tone = ChipTone.TINT, accent = spec.accent)
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "note") {
            Column {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.fill, CampusTokens.Shapes.small)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "停用插件不会删除凭据；卡片随时可以加回，布局照旧。",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.ink2
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "插件都是编译期内置的：没有热安装、没有动态加载，本页只控制卡片显隐。",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.ink3,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}
