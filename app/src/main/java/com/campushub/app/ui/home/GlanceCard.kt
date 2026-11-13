package com.campushub.app.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campushub.app.core.plugin.BentoCardSize
import com.campushub.app.core.plugin.CardSpec
import com.campushub.app.core.plugin.CardState
import com.campushub.app.core.plugin.GlanceLayoutRules
import com.campushub.app.core.plugin.GlanceModel
import com.campushub.app.core.plugin.GlanceSlot
import com.campushub.app.core.plugin.GlanceTarget
import com.campushub.app.ui.components.CampusButton
import com.campushub.app.ui.components.ButtonSize
import com.campushub.app.ui.components.CampusTextLink
import com.campushub.app.ui.components.ChipTone
import com.campushub.app.ui.components.IconGlyph
import com.campushub.app.ui.components.LocalLargeFont
import com.campushub.app.ui.components.StatusChip
import com.campushub.app.ui.components.color
import com.campushub.app.ui.components.soft
import com.campushub.app.ui.theme.CampusTheme
import com.campushub.app.ui.theme.CampusTokens
import androidx.compose.material3.ModalBottomSheet
import com.campushub.app.core.plugin.ActionIcon
import com.campushub.app.core.plugin.GlanceOption
import com.campushub.app.ui.components.ListRow
import com.campushub.app.ui.components.WaterDroplet
import com.campushub.app.ui.components.clickableNoIndication
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 一张便当盒卡片的渲染器。
 *
 * 插件只提供 [GlanceModel]（数据），这里按档位与状态统一渲染：
 * 槽位可见性由 [GlanceLayoutRules] 决定，内容放不下就整块隐藏，不压缩、不换行。
 *
 * @param animateRunning 是否播放「出水中」流光描边；同一屏只允许一张卡动画。
 */
@Composable
fun GlanceCard(
    model: GlanceModel,
    spec: CardSpec,
    size: BentoCardSize,
    onTarget: (GlanceTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = CampusTheme.palette
    val largeFont = LocalLargeFont.current
    var switcherOpen by remember { mutableStateOf(false) }
    val slots = GlanceLayoutRules.visibleSlots(size, largeFont)
    val accent = spec.accent.color()

    // 出水中：竖向渐变（浅水色 → 白），与设计 `linear-gradient(180deg,#F2F8FD,#FFF)` 一致。
    // 不能用整块 waterSoft 铺底，否则同为 waterSoft 的状态 chip 会完全看不见。
    //
    // 状态切换做颜色过渡而不是硬切：idle → running → error 时底色渐变过去，
    // 上/下两个色标一起动，error 的两端取同一个色所以看起来仍是纯色块。
    val tintSpec = tween<Color>(
        durationMillis = CampusTokens.Motion.stateTintMs.toInt(),
        easing = FastOutSlowInEasing
    )
    val tintTop by animateColorAsState(
        targetValue = when (model.state) {
            CardState.RUNNING -> spec.accent.soft().copy(alpha = 0.55f)
            CardState.ERROR -> palette.dangerSoft
            // 同色相、零透明度：从透明黑过渡会让中间帧发灰
            else -> spec.accent.soft().copy(alpha = 0f)
        },
        animationSpec = tintSpec,
        label = "glanceTintTop"
    )
    val tintBottom by animateColorAsState(
        targetValue = when (model.state) {
            CardState.RUNNING -> palette.surface
            CardState.ERROR -> palette.dangerSoft
            else -> palette.surface.copy(alpha = 0f)
        },
        animationSpec = tintSpec,
        label = "glanceTintBottom"
    )
    val containerBrush = Brush.verticalGradient(listOf(tintTop, tintBottom))
    val borderModifier = when {
        model.state == CardState.RUNNING -> Modifier.runningBand(spec.accent.color())
        model.state == CardState.ERROR -> Modifier.border(1.dp, palette.danger, CampusTokens.Shapes.medium)
        else -> Modifier
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(containerBrush, CampusTokens.Shapes.medium)
            .then(borderModifier)

    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    if (size == BentoCardSize.HERO_2X2) {
                        CampusTokens.Space.cardPaddingHero
                    } else {
                        CampusTokens.Space.cardPadding
                    }
                )
        ) {
            // ---- 头部：图标 + 标题(+服务名) + 头部文字链 + 状态 chip
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconGlyph(glyph = spec.iconGlyph, accent = spec.accent)
                Spacer(modifier = Modifier.width(8.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (model.switcher != null) {
                                Modifier.clickableNoIndication { switcherOpen = true }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = model.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (model.switcher != null) {
                            Text(
                                text = "▾",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.ink3,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                    if (GlanceSlot.SERVICE_LABEL in slots && !model.serviceLabel.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(accent, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = model.serviceLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.ink2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (GlanceSlot.HEADER_LINK in slots) {
                    model.headerLink?.let { link ->
                        CampusTextLink(label = link.label, onClick = { onTarget(link.target) })
                    }
                }
                if (GlanceSlot.CHIP in slots && !model.chip.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    StatusChip(
                        text = model.chip,
                        tone = if (model.state == CardState.RUNNING) {
                            ChipTone.TINT
                        } else if (model.state == CardState.ERROR) {
                            ChipTone.DANGER
                        } else {
                            ChipTone.NEUTRAL
                        },
                        accent = spec.accent
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ---- 读数行
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = animatedReadout(model.readout),
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (size == BentoCardSize.COMPACT_1X1) 20.sp else if (largeFont) 22.sp else 26.sp,
                    maxLines = 1,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFeatureSettings = "tnum"
                    )
                )
                if (!model.readoutUnit.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = model.readoutUnit,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.ink2,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (size == BentoCardSize.EXPANDED_2X1 && GlanceSlot.DETAILS in slots) {
                    model.details.firstOrNull()?.let { detail ->
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = detail.value,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.ink,
                                maxLines = 1
                            )
                            Text(
                                text = detail.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.ink3,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            if (GlanceSlot.SUB in slots && !model.sub.isNullOrBlank()) {
                Text(
                    text = model.sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }

            if (GlanceSlot.PROGRESS in slots && model.progress != null) {
                Spacer(modifier = Modifier.height(6.dp))
                ProgressBar(progress = model.progress, color = accent)
            }

            if (GlanceSlot.DETAILS in slots && size != BentoCardSize.EXPANDED_2X1) {
                val details = model.details.take(GlanceLayoutRules.maxDetails(size))
                if (details.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (size == BentoCardSize.HERO_2X2) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            details.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    pair.forEach { detail ->
                                        DetailItem(detail.label, detail.value, Modifier.weight(1f))
                                    }
                                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            details.forEach { detail ->
                                DetailItem(detail.label, detail.value, Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ---- 底部动作 / 文字链
            if (GlanceSlot.ACTION in slots) {
                model.action?.let { action ->
                    CampusButton(
                        label = action.label,
                        onClick = { onTarget(action.target) },
                        style = action.style,
                        size = if (size == BentoCardSize.COMPACT_1X1) ButtonSize.SM else ButtonSize.MD,
                        loading = action.busy,
                        leadingIcon = if (action.icon == ActionIcon.DROPLET) WaterDroplet else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (GlanceSlot.LINK in slots && model.action == null) {
                model.link?.let { link ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        CampusTextLink(label = "${link.label} ›", onClick = { onTarget(link.target) })
                    }
                }
            }
        }
    }

    // 头部下拉：直接列出全部水机，点哪个切哪个
    val switcher = model.switcher
    if (switcher != null && switcherOpen) {
        ModalBottomSheet(
            onDismissRequest = { switcherOpen = false },
            containerColor = palette.surface
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = "选择水机",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.ink,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
                switcher.options.forEach { option ->
                    ListRow(
                        title = option.label,
                        subtitle = if (option.selected) "当前使用" else null,
                        onClick = {
                            switcherOpen = false
                            if (!option.selected) {
                                onTarget(GlanceTarget.Command(switcher.commandId, option.id))
                            }
                        }
                    ) {
                        if (option.selected) {
                            StatusChip(text = "当前", tone = ChipTone.TINT, accent = spec.accent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String, modifier: Modifier = Modifier) {
    val palette = CampusTheme.palette
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.ink3,
            maxLines = 1
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = palette.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProgressBar(progress: Float, color: Color) {
    val palette = CampusTheme.palette
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .background(palette.fill, CircleShape)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(color, CircleShape)
        )
    }
}

/**
 * 「出水中」流光描边（设计 `.card.run::after`）。
 *
 * 组成：一圈静态淡描边（任何时刻都能看出边界）+ 一条沿边框扫过的高光。
 * 高光用**旋转的线性渐变**实现——**只转渐变，不转几何**：
 * 早先版本对整个绘制上下文 `rotate()`，圆角矩形会跟着转，45° 时描边斜着穿过卡片中部，
 * 看起来就是「中间有块蓝色形状在乱飘」。
 */
@Composable
private fun Modifier.runningBand(accent: Color): Modifier {
    val transition = rememberInfiniteTransition(label = "runningBand")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(CampusTokens.Motion.borderBandMs.toInt(), easing = LinearEasing)
        ),
        label = "runningBandPhase"
    )
    return drawWithContent {
        drawContent()
        val strokeWidth = 3.dp.toPx()
        val corner = CornerRadius(16.dp.toPx())

        // 1) 外发光：更宽的淡环，让流动感在浅色背景上也看得出来
        drawRoundRect(
            color = accent.copy(alpha = 0.16f),
            style = Stroke(width = 9.dp.toPx()),
            cornerRadius = corner
        )
        // 2) 静态底环
        drawRoundRect(
            color = accent.copy(alpha = 0.30f),
            style = Stroke(width = strokeWidth),
            cornerRadius = corner
        )

        // 3) 沿中心旋转的线性渐变：光带随角度扫过整圈，几何保持不动
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = hypot(size.width, size.height) / 2f
        val dx = cos(phase) * radius
        val dy = sin(phase) * radius
        drawRoundRect(
            brush = Brush.linearGradient(
                // 与设计 `#2E86C1 → #FFF → #2E86C1` 一致：高光带更宽、更实，扫过时明显可见
                colors = listOf(
                    accent.copy(alpha = 0f),
                    accent.copy(alpha = 0.35f),
                    accent,
                    accent,
                    Color.White,
                    accent,
                    accent,
                    accent.copy(alpha = 0.35f),
                    accent.copy(alpha = 0f)
                ),
                start = Offset(cx - dx, cy - dy),
                end = Offset(cx + dx, cy + dy)
            ),
            style = Stroke(width = strokeWidth),
            cornerRadius = corner
        )
    }
}

/**
 * 主读数变化时滚到新值（[CampusTokens.Motion.readoutMs]）。
 *
 * 只有「一个数字 + 可选单位」才做动画：`—`、`未接入`、带千分位的串一律原样显示。
 * 首次组合直接落在当前值，不播 0 → 3945 的假动画。
 */
@Composable
private fun animatedReadout(raw: String?): String {
    val parsed = remember(raw) { ReadoutFormat.parse(raw) } ?: return raw ?: "—"
    val animated = remember { Animatable(parsed.value) }
    LaunchedEffect(parsed.value) {
        if (animated.value != parsed.value) {
            animated.animateTo(
                targetValue = parsed.value,
                animationSpec = tween(
                    durationMillis = CampusTokens.Motion.readoutMs.toInt(),
                    easing = FastOutSlowInEasing
                )
            )
        }
    }
    return ReadoutFormat.format(parsed, animated.value)
}
