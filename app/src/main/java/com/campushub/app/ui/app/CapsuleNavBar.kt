package com.campushub.app.ui.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.campushub.app.ui.components.pressable
import com.campushub.app.ui.theme.CampusTheme
import com.campushub.app.ui.theme.CampusSurfaces.cardDepth
import com.campushub.app.ui.theme.CampusTokens

/** 标签页索引。 */
enum class CampusTab(val label: String, val icon: ImageVector) {
    HOME("控制台", Icons.Outlined.Home),
    PLUGINS("插件", Icons.Outlined.GridView),
    ACCOUNT("我的", Icons.Outlined.Person)
}
/** 通用胶囊导航栏项。 */
data class CapsuleNavItem(
    val id: String,
    val label: String,
    val icon: ImageVector
)

/**
 * 通用悬浮胶囊导航栏：用于首页三大 Tab 与详情页多分页。
 */
@Composable
fun GenericCapsuleNavBar(
    items: List<CapsuleNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    fraction: Float? = null,
    accentColor: Color = CampusTheme.palette.water,
    accentSoftColor: Color = CampusTheme.palette.waterSoft,
    modifier: Modifier = Modifier
) {
    val palette = CampusTheme.palette
    val count = items.size.coerceAtLeast(1)

    // 滑块位置浮点数（优先跟随实时滑动分数，否则跟随选中的下标平滑弹簧滑行）
    val targetIndex = (fraction ?: selectedIndex.toFloat()).coerceIn(0f, (count - 1).toFloat())
    val animatedIndex by animateFloatAsState(
        targetValue = targetIndex,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "navIndicator"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .height(64.dp)
            .cardDepth(CampusTokens.Shapes.extraLarge)
            .background(palette.surface, CampusTokens.Shapes.extraLarge)
            .border(
                width = 1.dp,
                color = palette.line.copy(alpha = CampusTokens.Depth.borderAlpha),
                shape = CampusTokens.Shapes.extraLarge
            )
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        val availableWidth = maxWidth
        val itemWidth = availableWidth / count

        // 1. 底层：顺滑滑动的背景指示胶囊（Sliding Indicator Pill）
        Box(
            modifier = Modifier
                .offset(x = itemWidth * animatedIndex)
                .width(itemWidth)
                .fillMaxHeight()
                .background(accentSoftColor, CampusTokens.Shapes.medium)
        )

        // 2. 顶层：图标与文字按钮行
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val active = index == selectedIndex
                val tint by animateColorAsState(
                    targetValue = if (active) accentColor else palette.ink3,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "tint_$index"
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (active) 1.08f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "iconScale_$index"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pressable(onClick = { onSelect(index) }, pressedScale = 0.94f)
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier
                            .size(19.dp)
                            .graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            }
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = tint,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * 胶囊导航（设计 `.nav`）：浮在内容之上，只在三个标签页显示。
 *
 * 高度 64dp、圆角 capsule、surface 底 + 1dp 描边；选中项用滑动水色胶囊指示。
 */
@Composable
fun CapsuleNavBar(
    selected: CampusTab,
    onSelect: (CampusTab) -> Unit,
    fraction: Float? = null,
    modifier: Modifier = Modifier
) {
    val items = remember {
        CampusTab.entries.map {
            CapsuleNavItem(id = it.name, label = it.label, icon = it.icon)
        }
    }
    GenericCapsuleNavBar(
        items = items,
        selectedIndex = selected.ordinal,
        onSelect = { onSelect(CampusTab.entries[it]) },
        fraction = fraction,
        modifier = modifier
    )
}
