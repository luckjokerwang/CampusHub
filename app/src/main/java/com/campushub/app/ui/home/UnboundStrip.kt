package com.campushub.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.campushub.app.core.plugin.CardSpec
import com.campushub.app.core.plugin.PluginAccent
import com.campushub.app.ui.components.ChipTone
import com.campushub.app.ui.components.IconGlyph
import com.campushub.app.ui.components.StatusChip
import com.campushub.app.ui.components.pressable
import com.campushub.app.ui.theme.CampusTheme
import com.campushub.app.ui.theme.CampusSurfaces.cardDepth
import com.campushub.app.ui.theme.CampusTokens

/**
 * 「未接入」条（设计 `.card.strip`，64dp 整宽单行）。
 *
 * 未登录/未绑定的卡片不进网格，在网格下方压成一条，避免占掉一整块方块位。
 */
@Composable
fun UnboundStrip(
    spec: CardSpec,
    serviceName: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = CampusTheme.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .cardDepth(CampusTokens.Shapes.medium)
            .background(palette.surface, CampusTokens.Shapes.medium)
            .border(
                width = 1.dp,
                color = palette.line.copy(alpha = CampusTokens.Depth.borderAlpha),
                shape = CampusTokens.Shapes.medium
            )
            .pressable(onClick = onClick, pressedScale = 0.985f)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IconGlyph(glyph = spec.iconGlyph, accent = PluginAccent.NEUTRAL)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = spec.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "未接入 · $description",
                style = MaterialTheme.typography.labelSmall,
                color = palette.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        StatusChip(text = "去接入", tone = ChipTone.LINE)
        Text(
            text = serviceName,
            style = MaterialTheme.typography.labelSmall,
            color = palette.ink3,
            maxLines = 1
        )
    }
}
