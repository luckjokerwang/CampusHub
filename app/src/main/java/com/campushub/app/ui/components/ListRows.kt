package com.campushub.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.campushub.app.core.plugin.PluginAccent
import com.campushub.app.ui.theme.CampusTheme
import com.campushub.app.ui.theme.CampusTokens

/** 分组小标题（设计 `.sect`）。 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = CampusTheme.palette.ink3,
        modifier = modifier.padding(start = 2.dp, top = 2.dp, bottom = 4.dp)
    )
}

/** 圆角分组容器（设计 `.grp`）：白底、1dp 描边、内部行之间 1dp 分隔线。 */
@Composable
fun ListGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = CampusTheme.palette
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.surface, CampusTokens.Shapes.medium)
            .border(1.dp, palette.line, CampusTokens.Shapes.medium)
    ) {
        content()
    }
}

/**
 * 分组内的一行（设计 `.li2`，高 52dp）。
 *
 * @param glyph 单字图标；为 null 时不显示图标块。
 * @param trailing 右侧内容（值文字、开关、按钮）。
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    glyph: String? = null,
    accent: PluginAccent = PluginAccent.NEUTRAL,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null
) {
    val palette = CampusTheme.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .then(
                if (onClick != null) Modifier.clickableNoIndication(onClick) else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (glyph != null) {
            IconGlyph(glyph = glyph, accent = accent, size = 24.dp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.ink2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing?.let { it() }
    }
}

/** 分组内的分隔线。 */
@Composable
fun ListRowDivider() {
    HorizontalDivider(thickness = 1.dp, color = CampusTheme.palette.line)
}
