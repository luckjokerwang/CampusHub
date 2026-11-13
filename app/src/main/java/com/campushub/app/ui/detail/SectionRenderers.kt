package com.campushub.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import java.util.Locale
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campushub.app.core.plugin.ConfigField
import com.campushub.app.core.plugin.ConfigFieldType
import com.campushub.app.core.plugin.ConfigOption
import com.campushub.app.ui.components.clickableNoIndication
import com.campushub.app.core.plugin.AmountTone
import com.campushub.app.core.plugin.ChartBar
import com.campushub.app.core.plugin.DetailSection
import com.campushub.app.core.plugin.KpiHighlight
import com.campushub.app.core.plugin.KpiItem
import com.campushub.app.core.plugin.PluginAccent
import com.campushub.app.core.plugin.RowAction
import com.campushub.app.core.plugin.RowActionKind
import com.campushub.app.core.plugin.RowItem
import com.campushub.app.ui.components.CampusButton
import com.campushub.app.ui.components.CampusSwitch
import com.campushub.app.ui.components.CampusField
import com.campushub.app.ui.components.ChipTone
import com.campushub.app.ui.components.ListGroup
import com.campushub.app.ui.components.ListRow
import com.campushub.app.ui.components.ListRowDivider
import com.campushub.app.ui.components.SectionLabel
import com.campushub.app.ui.components.StatusChip
import com.campushub.app.ui.components.color
import com.campushub.app.ui.components.soft
import com.campushub.app.ui.theme.CampusTheme
import com.campushub.app.ui.theme.CampusSurfaces.cardDepth
import com.campushub.app.ui.theme.CampusTokens

/**
 * 详情页段落渲染器：只认四种段落（KPI / 图表 / 记录流 / 表单）。
 *
 * @param onCommand 行内动作或页面动作的命令回调（commandId, rowId, input）
 * @param onFormChange 表单字段即时变更（开关类）
 */
@Composable
fun DetailSectionContent(
    section: DetailSection,
    accent: PluginAccent,
    onCommand: (commandId: String, rowId: String?, input: String?) -> Unit,
    onFormChange: (key: String, value: String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (section) {
        is DetailSection.Kpi -> KpiSection(section.items, section.highlight, accent, modifier)
        is DetailSection.Chart -> ChartSection(section.title, section.summary, section.bars, accent, modifier)
        is DetailSection.Records -> RecordsSection(section, onCommand, modifier)
        is DetailSection.Form -> FormSection(section, onFormChange, modifier)
    }
}

@Composable
private fun KpiSection(
    items: List<KpiItem>,
    highlight: KpiHighlight?,
    accent: PluginAccent,
    modifier: Modifier = Modifier
) {
    val palette = CampusTheme.palette
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (highlight != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(accent.soft(), CampusTokens.Shapes.medium)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!highlight.chip.isNullOrBlank()) {
                    StatusChip(text = highlight.chip, tone = ChipTone.SURFACE_TINT, accent = accent)
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = highlight.readout,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent.color(),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFeatureSettings = "tnum"
                        )
                    )
                    if (!highlight.unit.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = highlight.unit,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.ink2
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        highlight.side.forEach { detail ->
                            Text(
                                text = "${detail.label} ${detail.value}",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.ink2,
                                maxLines = 1
                            )
                        }
                    }
                }
                highlight.progress?.let { progress ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .background(palette.surface, CircleShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .background(accent.color(), CircleShape)
                        )
                    }
                }
            }
        }
        if (items.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { item ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(palette.surface, CampusTokens.Shapes.small)
                            .padding(horizontal = 11.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.ink3
                        )
                        Text(
                            text = item.value,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFeatureSettings = "tnum"
                            ),
                            fontWeight = FontWeight.Bold,
                            color = palette.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!item.unit.isNullOrBlank()) {
                            Text(
                                text = item.unit,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.ink2,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartSection(
    title: String,
    summary: String?,
    bars: List<ChartBar>,
    accent: PluginAccent,
    modifier: Modifier = Modifier
) {
    val palette = CampusTheme.palette
    val max = bars.maxOfOrNull { it.value }?.takeIf { it > 0.0 } ?: 1.0
    var selectedIndex by remember(bars) { mutableIntStateOf(bars.lastIndex) }

    val selectedBar = bars.getOrNull(selectedIndex)
    val displaySummary = if (selectedBar != null) {
        val ml = selectedBar.value.toInt()
        val litersText = if (ml >= 1000) {
            String.format(Locale.getDefault(), "%.2f L", ml / 1000.0)
        } else {
            "${ml} ml"
        }
        "${selectedBar.label} · $litersText"
    } else {
        summary
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .cardDepth(CampusTokens.Shapes.medium)
            .background(palette.surface, CampusTokens.Shapes.medium)
            .padding(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = palette.ink
            )
            Spacer(modifier = Modifier.weight(1f))
            if (!displaySummary.isNullOrBlank()) {
                Text(
                    text = displaySummary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (selectedBar != null && selectedBar.value > 0) accent.color() else palette.ink3
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        // 柱体固定 80dp 高，底部对齐，点击任意柱子可交互查看当天具体出水
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            bars.forEachIndexed { index, bar ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickableNoIndication { selectedIndex = index },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight((bar.value / max).toFloat().coerceIn(0.08f, 1f))
                            .background(
                                when {
                                    isSelected -> accent.color()
                                    bar.value > 0 -> accent.soft()
                                    else -> palette.fill
                                },
                                CampusTokens.Shapes.extraSmall
                            )
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            bars.forEachIndexed { index, bar ->
                val isSelected = index == selectedIndex
                Text(
                    text = bar.label,
                    fontSize = 8.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) accent.color() else palette.ink3,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickableNoIndication { selectedIndex = index }
                )
            }
        }
    }
}

@Composable
private fun RecordsSection(
    section: DetailSection.Records,
    onCommand: (commandId: String, rowId: String?, input: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = CampusTheme.palette
    var actionRow by remember { mutableStateOf<RowItem?>(null) }
    var promptAction by remember { mutableStateOf<Pair<RowItem, RowAction>?>(null) }
    var confirmAction by remember { mutableStateOf<Pair<RowItem, RowAction>?>(null) }
    var promptValue by remember { mutableStateOf("") }

    fun run(row: RowItem, action: RowAction) {
        when (action.kind) {
            RowActionKind.COMMAND -> onCommand(action.id, row.id, null)
            RowActionKind.PROMPT_TEXT -> {
                promptValue = ""
                promptAction = row to action
            }
            RowActionKind.CONFIRM -> confirmAction = row to action
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        section.title?.let { SectionLabel(it) }
        if (section.rows.isEmpty()) {
            Text(
                text = section.emptyText,
                style = MaterialTheme.typography.labelSmall,
                color = palette.ink3,
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 2.dp)
            )
        } else {
            ListGroup {
                section.rows.forEachIndexed { index, row ->
                    if (index > 0) ListRowDivider()
                    ListRow(
                        title = row.title,
                        subtitle = row.sub,
                        onClick = if (row.actions.isNotEmpty()) {
                            { actionRow = row }
                        } else {
                            null
                        }
                    ) {
                        if (!row.badge.isNullOrBlank()) {
                            StatusChip(text = row.badge, tone = ChipTone.LINE)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        if (!row.amount.isNullOrBlank()) {
                            Text(
                                text = row.amount,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFeatureSettings = "tnum"
                                ),
                                fontWeight = FontWeight.Bold,
                                color = when (row.tone) {
                                    AmountTone.IN -> palette.water
                                    AmountTone.OUT -> palette.score
                                    AmountTone.NEUTRAL -> palette.ink
                                },
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    // 行内动作菜单
    actionRow?.let { row ->
        ModalBottomSheet(
            onDismissRequest = { actionRow = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = palette.surface
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.ink,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
                row.actions.forEach { action ->
                    ListRow(
                        title = action.label,
                        onClick = {
                            actionRow = null
                            run(row, action)
                        }
                    )
                }
            }
        }
    }

    promptAction?.let { (row, action) ->
        AlertDialog(
            onDismissRequest = { promptAction = null },
            title = { Text(action.prompt ?: action.label) },
            text = {
                CampusField(
                    value = promptValue,
                    onValueChange = { promptValue = it },
                    label = action.prompt ?: action.label
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val input = promptValue.trim()
                    promptAction = null
                    if (input.isNotEmpty()) onCommand(action.id, row.id, input)
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { promptAction = null }) { Text("取消") }
            },
            containerColor = palette.surface
        )
    }

    confirmAction?.let { (row, action) ->
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text("确认${action.label}") },
            text = { Text("${action.label}「${row.title}」？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmAction = null
                    onCommand(action.id, row.id, null)
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) { Text("取消") }
            },
            containerColor = palette.surface
        )
    }
}

@Composable
private fun FormSection(
    section: DetailSection.Form,
    onFormChange: (key: String, value: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = CampusTheme.palette
    var activeSelectField by remember { mutableStateOf<ConfigField?>(null) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        section.title?.let { SectionLabel(it) }
        ListGroup {
            section.fields.forEachIndexed { index, field ->
                if (index > 0) ListRowDivider()
                when (field.type) {
                    ConfigFieldType.SELECT -> {
                        val currentValue = section.values[field.key].orEmpty()
                        val currentOption = field.options.find { it.value == currentValue }
                        val label = currentOption?.label ?: currentValue.ifBlank { "点击选择" }
                        ListRow(
                            title = field.label,
                            subtitle = field.subtitle
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.clickableNoIndication { activeSelectField = field }
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.ink2
                                )
                                Text(
                                    text = "›",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.ink3
                                )
                            }
                        }
                    }
                    else -> {
                        ListRow(
                            title = field.label,
                            subtitle = field.subtitle
                        ) {
                            CampusSwitch(
                                checked = section.values[field.key] == "true",
                                onCheckedChange = { onFormChange(field.key, it.toString()) }
                            )
                        }
                    }
                }
            }
        }
    }

    activeSelectField?.let { field ->
        val currentValue = section.values[field.key].orEmpty()
        AlertDialog(
            onDismissRequest = { activeSelectField = null },
            title = { Text(field.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    field.options.forEach { option ->
                        val selected = option.value == currentValue
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickableNoIndication {
                                    activeSelectField = null
                                    onFormChange(field.key, option.value)
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) palette.water else palette.ink,
                                modifier = Modifier.weight(1f)
                            )
                            if (selected) {
                                Text(
                                    text = "✓",
                                    color = palette.water,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeSelectField = null }) { Text("取消") }
            },
            containerColor = palette.surface
        )
    }
}
