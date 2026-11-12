package com.campushub.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.campushub.app.ui.theme.CampusTheme
import com.campushub.app.ui.theme.CampusTokens
/**
 * 分段控件（设计 `.seg`）：浅底容器 + 选中项白底胶囊。
 *
 * 用于插件页「已启用 / 可添加」与登录页的账号分组切换。
 */
@Composable
fun SegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = CampusTheme.palette
    val count = items.size.coerceAtLeast(1)

    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.coerceIn(0, count - 1).toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "segIndicator"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(palette.fill, CampusTokens.Shapes.small)
            .padding(3.dp)
    ) {
        val availableWidth = maxWidth
        val itemWidth = availableWidth / count

        // 1. 底层：顺滑滑动的指示胶囊
        Box(
            modifier = Modifier
                .offset(x = itemWidth * animatedIndex)
                .width(itemWidth)
                .fillMaxHeight()
                .background(palette.surface, CampusTokens.Shapes.extraSmall)
        )

        // 2. 顶层：文字选项行
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val textColor by animateColorAsState(
                    targetValue = if (selected) palette.ink else palette.ink2,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "textColor_$index"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickableNoIndication { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = textColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
