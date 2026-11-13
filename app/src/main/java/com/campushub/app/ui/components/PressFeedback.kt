package com.campushub.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 按压缩放：手指按下时缩小 [pressedScale]，松手弹回。
 *
 * 只给「自己就是一整块可点区域」的控件用（按钮、卡片、导航项、未接入条）。
 * 列表分组里的行不要用——行内容在分组白底上缩放，看起来像文字在抖。
 *
 * 必须和 `clickable` / `Button` 共用同一个 [interactionSource]，否则收不到按压事件。
 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.97f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        // 不弹跳：按下要「立刻到位」，回弹才需要弹性
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 1200f
        ),
        label = "pressScale"
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** 无涟漪点击 + 按压缩放，绑定同一个 [MutableInteractionSource]。 */
@Composable
fun Modifier.pressable(
    onClick: () -> Unit,
    pressedScale: Float = 0.97f
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return clickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick
    ).pressScale(interaction, pressedScale)
}
