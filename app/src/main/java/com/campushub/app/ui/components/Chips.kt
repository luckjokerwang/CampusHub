package com.campushub.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** chip 语气（对应 `components.html` 的 `.chip` / `.chip.w` / `.chip.d` / `.chip.line`）。 */
enum class ChipTone { NEUTRAL, TINT, DANGER, LINE, SURFACE_TINT }

/**
 * 状态 chip。
 *
 * @param accent [ChipTone.TINT] 时使用该强调色（浅底 + 实色字）。
 */
@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: ChipTone = ChipTone.NEUTRAL,
    accent: PluginAccent = PluginAccent.NEUTRAL
) {
    val palette = CampusTheme.palette
    val background = when (tone) {
        ChipTone.NEUTRAL -> palette.fill
        ChipTone.TINT -> accent.soft()
        ChipTone.DANGER -> palette.dangerSoft
        ChipTone.LINE, ChipTone.SURFACE_TINT -> palette.surface
    }
    val content = when (tone) {
        ChipTone.NEUTRAL -> palette.ink2
        ChipTone.TINT -> accent.color()
        ChipTone.DANGER -> palette.danger
        ChipTone.LINE -> palette.ink3
        ChipTone.SURFACE_TINT -> accent.color()
    }

    Box(
        modifier = modifier
            .background(background, RoundedCornerShape(6.dp))
            .then(
                if (tone == ChipTone.LINE || tone == ChipTone.SURFACE_TINT) {
                    Modifier.border(1.dp, palette.line, RoundedCornerShape(6.dp))
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
