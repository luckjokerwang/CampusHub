package com.campushub.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance

/**
 * 页面与卡片的「层次」入口。
 *
 * 全项目只有这一处决定背景渐变和卡片阴影，其他界面不许自己拼 `Brush.verticalGradient`
 * 或调 `Modifier.shadow`，否则层次会各写各的、越改越乱。
 *
 * 参数全部来自 `design/tokens.json` 的 `depth` 段（[CampusTokens.Depth]），改数值请改令牌。
 */
object CampusSurfaces {

    /**
     * 页面底色：顶部一层极淡的品牌色，往下 [CampusTokens.Depth.pageTintStop] 处收到画布色。
     *
     * 只差几个百分点的亮度，不做紫蓝大渐变——目的是让屏幕看起来「有光从上面下来」，
     * 而不是给界面贴一张渐变壁纸。深浅色共用一套算法，色值各自取调色板。
     */
    @Composable
    @ReadOnlyComposable
    fun pageBrush(): Brush {
        val palette = CampusTheme.palette
        return Brush.verticalGradient(
            0f to palette.waterSoft.copy(alpha = CampusTokens.Depth.pageTintAlpha),
            CampusTokens.Depth.pageTintStop to palette.canvas,
            1f to palette.canvas
        )
    }

    /** 把 [pageBrush] 画成背景。替代原先各页面的 `background(palette.canvas)`。 */
    @Composable
    @ReadOnlyComposable
    fun Modifier.pageBackground(): Modifier = background(pageBrush())

    /**
     * 卡片阴影分层。
     *
     * 浅色主题用 `ink` 着色（纯黑会显脏）；深色主题用纯黑，并加重不透明度——
     * 深色背景上浅色阴影会变成发光，只能靠更暗的投影和更亮的描边来拉层次。
     *
     * @param raised 浮起态（大卡 / 拖拽中 / 放置目标）用更大的一档
     */
    @Composable
    @ReadOnlyComposable
    fun Modifier.cardDepth(shape: Shape, raised: Boolean = false): Modifier {
        val palette = CampusTheme.palette
        val dark = palette.canvas.luminance() < 0.2f
        val elevation = if (raised) {
            CampusTokens.Depth.shadowRaised
        } else {
            CampusTokens.Depth.shadowRest
        }
        val alpha = when {
            dark && raised -> CampusTokens.Depth.shadowAlphaRaisedDark
            dark -> CampusTokens.Depth.shadowAlphaDark
            raised -> CampusTokens.Depth.shadowAlphaRaised
            else -> CampusTokens.Depth.shadowAlpha
        }
        val color = if (dark) Color.Black else palette.ink
        return shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = color.copy(alpha = alpha),
            spotColor = color.copy(alpha = alpha)
        )
    }
}
