package com.campushub.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 水滴图标（设计 `.drip`，viewBox 15×19）。
 *
 * 路径照抄 `design/screens/home.html` 的内联 SVG：
 * `M7.5 1C7.5 1 1.5 8.2 1.5 12.1a6 6 0 0 0 12 0C13.5 8.2 7.5 1 7.5 1Z`
 */
val WaterDroplet: ImageVector by lazy {
    ImageVector.Builder(
        name = "WaterDroplet",
        defaultWidth = 15.dp,
        defaultHeight = 19.dp,
        viewportWidth = 15f,
        viewportHeight = 19f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(7.5f, 1f)
            curveTo(7.5f, 1f, 1.5f, 8.2f, 1.5f, 12.1f)
            arcToRelative(
                a = 6f,
                b = 6f,
                theta = 0f,
                isMoreThanHalf = false,
                isPositiveArc = false,
                dx1 = 12f,
                dy1 = 0f
            )
            curveTo(13.5f, 8.2f, 7.5f, 1f, 7.5f, 1f)
            close()
        }
    }.build()
}
