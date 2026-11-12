package com.campushub.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campushub.app.ui.theme.CampusTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 下拉刷新的四个阶段。 */
private enum class PullPhase { IDLE, PULLING, READY, REFRESHING, COMPLETE }

/**
 * 灵动手势下拉刷新：
 * - 下拉带橡皮筋阻尼；
 * - 达到阈值触发震动，文案变为「松开刷新」；
 * - 松手后圆圈持续飞速打转，文案「正在刷新…」；
 * - 完成后短暂显示「刷新完成」，再弹簧回弹收起。
 */
@Composable
fun CampusPullRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = CampusTheme.palette
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val thresholdPx = with(density) { 64.dp.toPx() }
    val maxDragPx = with(density) { 160.dp.toPx() }
    val headerPx = with(density) { 64.dp.toPx() }

    val pullOffset = remember { Animatable(0f) }
    var readyHaptic by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }

    // 刷新期间圆圈持续打转
    val spin = rememberInfiniteTransition(label = "pullSpin")
    val spinAngle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinAngle"
    )

    // 外部刷新状态驱动：进入时停在阈值位并打转，结束时显示「刷新完成」再回弹
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            completed = false
            pullOffset.animateTo(thresholdPx, spring(stiffness = 600f))
        } else {
            if (pullOffset.value > 1f) {
                completed = true
                delay(520L)
                completed = false
            }
            pullOffset.animateTo(0f, spring(stiffness = 600f))
            readyHaptic = false
        }
    }

    val phase = when {
        isRefreshing -> PullPhase.REFRESHING
        completed -> PullPhase.COMPLETE
        pullOffset.value >= thresholdPx -> PullPhase.READY
        pullOffset.value > 1f -> PullPhase.PULLING
        else -> PullPhase.IDLE
    }

    val hintText = when (phase) {
        PullPhase.IDLE -> ""
        PullPhase.PULLING -> "下拉刷新"
        PullPhase.READY -> "松开刷新"
        PullPhase.REFRESHING -> "正在刷新…"
        PullPhase.COMPLETE -> "刷新完成"
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0 && pullOffset.value > 0f) {
                    val next = (pullOffset.value + available.y).coerceAtLeast(0f)
                    scope.launch { pullOffset.snapTo(next) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y > 0f && !isRefreshing) {
                    val next = (pullOffset.value + available.y * 0.45f).coerceAtMost(maxDragPx)
                    scope.launch { pullOffset.snapTo(next) }
                    if (next >= thresholdPx && !readyHaptic) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        readyHaptic = true
                    } else if (next < thresholdPx && readyHaptic) {
                        readyHaptic = false
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!isRefreshing) {
                    if (pullOffset.value >= thresholdPx) {
                        onRefresh()
                    } else {
                        pullOffset.animateTo(0f, spring(stiffness = 700f))
                        readyHaptic = false
                    }
                }
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        // 内容层跟随手指下移
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = with(density) { pullOffset.value.toDp() }),
            content = content
        )

        // 顶部悬浮指示器：圆环 + 状态文案
        if (phase != PullPhase.IDLE) {
            val progress = (pullOffset.value / thresholdPx).coerceIn(0f, 1f)
            val ringAngle = if (isRefreshing || completed) spinAngle else progress * 280f
            val ringProgress = when (phase) {
                PullPhase.REFRESHING -> 0.72f
                PullPhase.COMPLETE -> 1f
                else -> progress
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = with(density) { (pullOffset.value - headerPx).toDp() }.coerceAtLeast(4.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .shadow(elevation = 3.dp, shape = CircleShape)
                        .background(palette.surface, CircleShape)
                        .border(1.dp, palette.line, CircleShape)
                        .padding(7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { ringProgress },
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(ringAngle),
                        color = palette.water,
                        strokeWidth = 2.5.dp,
                        trackColor = palette.waterSoft
                    )
                }
                if (hintText.isNotEmpty()) {
                    Text(
                        text = hintText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.ink2
                    )
                }
            }
        }
    }
}
