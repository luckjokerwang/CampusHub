package com.campushub.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.campushub.app.core.plugin.PluginAccent
import com.campushub.app.ui.theme.CampusTheme

/** 强调色 → 实色。规则见 `design/tokens.json` 的 color.usage。 */
@Composable
@ReadOnlyComposable
fun PluginAccent.color(): Color {
    val palette = CampusTheme.palette
    return when (this) {
        PluginAccent.WATER -> palette.water
        PluginAccent.BATH -> palette.bath
        PluginAccent.CREDIT -> palette.credit
        PluginAccent.SCORE -> palette.score
        PluginAccent.NEUTRAL -> palette.ink3
    }
}

/** 强调色 → 浅底（chip / 图标底色）。 */
@Composable
@ReadOnlyComposable
fun PluginAccent.soft(): Color {
    val palette = CampusTheme.palette
    return when (this) {
        PluginAccent.WATER -> palette.waterSoft
        PluginAccent.BATH -> palette.bathSoft
        PluginAccent.CREDIT -> palette.creditSoft
        PluginAccent.SCORE -> palette.scoreSoft
        PluginAccent.NEUTRAL -> palette.fill
    }
}
