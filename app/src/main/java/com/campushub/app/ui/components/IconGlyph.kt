package com.campushub.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campushub.app.core.plugin.PluginAccent
import com.campushub.app.ui.theme.CampusTheme
import com.campushub.app.ui.theme.CampusTokens

/**
 * 单字图标块（设计里的 `.ic`：浅底 + 强调色字）。
 *
 * 插件用 `CardSpec.iconGlyph` / `PluginManifest.iconGlyph` 声明字形，
 * 宿主不内置图标资源，所以这里用文字字形而不是 ImageVector。
 */
@Composable
fun IconGlyph(
    glyph: String,
    accent: PluginAccent,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(accent.soft(), CampusTokens.Shapes.extraSmall),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = glyph,
            color = accent.color(),
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.42f).sp,
            maxLines = 1
        )
    }
}
