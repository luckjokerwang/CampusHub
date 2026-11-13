package com.campushub.app.ui.home.bento

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clipToBounds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.campushub.app.core.plugin.BentoCardSize
import com.campushub.app.core.plugin.CardSpec
import com.campushub.app.core.plugin.GlanceModel
import com.campushub.app.core.plugin.GlanceTarget
import com.campushub.app.core.settings.CardLayoutCodec
import com.campushub.app.core.settings.CardLayoutEntry
import com.campushub.app.ui.components.CampusButton
import com.campushub.app.ui.components.ButtonSize
import com.campushub.app.ui.components.LocalLargeFont
import com.campushub.app.ui.home.GlanceCard
import com.campushub.app.ui.theme.CampusTheme
import com.campushub.app.ui.theme.CampusTokens
import com.campushub.app.ui.theme.CampusSurfaces.cardDepth

/**
 * 便当盒网格单元：2 列 × N 行，支持 1×1 / 2×1(横) / 1×2(竖) / 2×2 四档跨度。
 */
private data class BentoCell(
    val entry: CardLayoutEntry,
    val row: Int,
    val col: Int,
    val colSpan: Int,
    val rowSpan: Int
)

/** 拖拽缩放时的实时预览尺寸与位移（像素）。 */
private data class BentoResizePreview(
    val cardId: String,
    val width: Int,
    val height: Int,
    val offsetX: Int = 0,
    val offsetY: Int = 0
)

/** 卡片边缘的缩放手柄方位。 */
private enum class ResizeHandle {
    TOP, BOTTOM, START, END;

    val isHorizontal: Boolean get() = this == START || this == END
    val growSign: Float get() = if (this == TOP || this == START) -1f else 1f
    val alignment: Alignment get() = when (this) {
        TOP -> Alignment.TopCenter
        BOTTOM -> Alignment.BottomCenter
        START -> Alignment.CenterStart
        END -> Alignment.CenterEnd
    }
    val description: String get() = when (this) {
        TOP -> "向上拉伸或收缩"
        BOTTOM -> "向下拉伸或收缩"
        START -> "向左拉伸或收缩"
        END -> "向右拉伸或收缩"
    }
}

/**
 * 贪心装箱：按顺序在 2 列网格中寻找第一个可容纳的槽位。
 * 横跨(2×1)与竖跨(1×2)共同参与占位，因此不会出现重叠或空洞。
 *
 * 算法来自旧 `BentoWidgetGrid`（已验证），此处只把输入从 `BentoCardItem` 换成
 * [CardLayoutEntry]：布局只关心 id 与尺寸。
 */
private fun layoutBentoGrid(cards: List<CardLayoutEntry>): List<BentoCell> {
    val occupied = mutableListOf<BooleanArray>()

    fun ensure(rows: Int) {
        while (occupied.size < rows) occupied.add(BooleanArray(2))
    }

    fun fits(row: Int, col: Int, cs: Int, rs: Int): Boolean {
        if (col + cs > 2) return false
        ensure(row + rs)
        for (r in row until row + rs) {
            for (c in col until col + cs) {
                if (occupied[r][c]) return false
            }
        }
        return true
    }

    val out = mutableListOf<BentoCell>()
    cards.forEachIndexed { index, raw ->
        // 网格里的第一张卡必须整宽（≥2×1）：桌宠趴在它上沿，它也是首页主操作区。
        // 这里只影响渲染，不改写用户保存的尺寸（否则未接入卡进出网格时会误改布局）。
        val card = if (index == 0 && raw.size.colSpan < 2) {
            raw.copy(size = BentoCardSize.of(2, raw.size.rowSpan))
        } else {
            raw
        }
        val cs = card.size.colSpan
        val rs = card.size.rowSpan
        var row = 0
        while (true) {
            var placed = false
            for (col in 0..(2 - cs)) {
                if (fits(row, col, cs, rs)) {
                    for (r in row until row + rs) {
                        for (c in col until col + cs) occupied[r][c] = true
                    }
                    out.add(BentoCell(card, row, col, cs, rs))
                    placed = true
                    break
                }
            }
            if (placed) break
            row++
        }
    }
    return compactHoles(out)
}

/**
 * 空洞压缩：把后续的 1×1 卡片上提到空洞位置，
 * 避免竖跨卡片旁留下整块空白（如 1×2 卡片右侧的空格）。
 */
private fun compactHoles(source: List<BentoCell>): List<BentoCell> {
    val cells = source.toMutableList()
    repeat(24) {
        val occupied = mutableSetOf<Pair<Int, Int>>()
        cells.forEach { cell ->
            for (r in cell.row until cell.row + cell.rowSpan) {
                for (c in cell.col until cell.col + cell.colSpan) occupied.add(r to c)
            }
        }
        val maxRow = cells.maxOfOrNull { it.row + it.rowSpan } ?: return cells
        var moved = false
        outer@ for (row in 0 until maxRow) {
            for (col in 0..1) {
                if (occupied.contains(row to col)) continue
                val index = cells.indexOfFirst {
                    it.colSpan == 1 && it.rowSpan == 1 && it.row > row
                }
                if (index >= 0) {
                    val target = cells[index]
                    cells[index] = target.copy(row = row, col = col)
                    moved = true
                    break@outer
                }
            }
        }
        if (!moved) return cells
    }
    return cells
}
/**
 * 流式推挤（Auto Push & Reflow）：
 * 当卡片向左或向上扩展时，它抢占左侧或上方的槽位，将原先在该槽位的卡片顺推向后排。
 * 常规（向右/向下）扩展时，由 [layoutBentoGrid] 的贪心装箱自然将后方卡片下推。
 */
private fun applyResize(
    cards: List<CardLayoutEntry>,
    cardId: String,
    newSize: BentoCardSize,
    handle: ResizeHandle?,
    cells: List<BentoCell>
): List<CardLayoutEntry> {
    val cell = cells.firstOrNull { it.entry.id == cardId } ?: return cards
    val currentSize = cell.entry.size
    if (newSize == currentSize) return cards

    val list = cards.toMutableList()
    val fromIdx = list.indexOfFirst { it.id == cardId }
    if (fromIdx < 0) return cards

    // 向左拉大且在右列 (col == 1)：移到原左侧卡片之前，抢占整行并将左卡下推
    if (handle == ResizeHandle.START && newSize.colSpan > currentSize.colSpan && cell.col == 1) {
        val leftNeighbor = cells.firstOrNull { it.row == cell.row && it.col == 0 }
        if (leftNeighbor != null) {
            val leftIdx = list.indexOfFirst { it.id == leftNeighbor.entry.id }
            if (leftIdx in list.indices && leftIdx < fromIdx) {
                val item = list.removeAt(fromIdx)
                list.add(leftIdx, item.copy(size = newSize))
                return list
            }
        }
    }

    // 向上拉大且不在首行：移到原上方卡片之前，抢占上方行并将上卡下推
    if (handle == ResizeHandle.TOP && newSize.rowSpan > currentSize.rowSpan && cell.row > 0) {
        val topNeighbor = cells.firstOrNull {
            (it.col == cell.col || it.colSpan == 2 || cell.colSpan == 2) &&
                it.row + it.rowSpan - 1 == cell.row - 1
        }
        if (topNeighbor != null) {
            val topIdx = list.indexOfFirst { it.id == topNeighbor.entry.id }
            if (topIdx in list.indices && topIdx < fromIdx) {
                val item = list.removeAt(fromIdx)
                list.add(topIdx, item.copy(size = newSize))
                return list
            }
        }
    }

    list[fromIdx] = list[fromIdx].copy(size = newSize)
    return list
}

/**
 * 绝对定位的便当盒布局。
 *
 * 正常字号：行高固定 `CampusTokens.Card.unitHeight`，卡片按跨度取固定尺寸。
 * 大字号：先用 `maxIntrinsicHeight` 量出每张卡的真实内容高度，把单行卡所在的
 * 行抬到内容高度；跨两行的卡若两行之和仍不够，就把差额加到第二行。
 * 这样文字只会让卡片长高，不会被裁掉（`design/docs/impl-plan.md` §6.5）。
 */
@Composable
private fun BentoGrid(
    cells: List<BentoCell>,
    rowCount: Int,
    modifier: Modifier = Modifier,
    largeFont: Boolean = false,
    preview: BentoResizePreview? = null,
    content: @Composable (BentoCell) -> Unit
) {
    Layout(content = { cells.forEach { content(it) } }, modifier = modifier.fillMaxWidth()) { measurables, constraints ->
        val gap = CampusTokens.Space.gridGap.roundToPx()
        val unitH = CampusTokens.Card.unitHeight.roundToPx()
        val width = constraints.maxWidth
        val unitW = (width - gap) / 2

        val rowHeights = IntArray(rowCount.coerceAtLeast(0)) { unitH }

        if (largeFont) {
            // 1) 单行卡先抬行高
            cells.forEachIndexed { index, cell ->
                if (cell.rowSpan != 1) return@forEachIndexed
                val spanW = unitW * cell.colSpan + gap * (cell.colSpan - 1)
                val contentH = runCatching { measurables[index].maxIntrinsicHeight(spanW) }
                    .getOrDefault(unitH)
                if (cell.row in rowHeights.indices) {
                    rowHeights[cell.row] = maxOf(rowHeights[cell.row], contentH)
                }
            }
            // 2) 跨两行的卡，把差额补到第二行
            cells.forEachIndexed { index, cell ->
                if (cell.rowSpan != 2) return@forEachIndexed
                val spanW = unitW * cell.colSpan + gap * (cell.colSpan - 1)
                val contentH = runCatching { measurables[index].maxIntrinsicHeight(spanW) }
                    .getOrDefault(unitH * 2 + gap)
                val first = cell.row
                val second = cell.row + 1
                if (first in rowHeights.indices && second in rowHeights.indices) {
                    val available = rowHeights[first] + rowHeights[second] + gap
                    if (available < contentH) rowHeights[second] += contentH - available
                }
            }
        }

        val rowOffsets = IntArray(rowHeights.size)
        var acc = 0
        for (i in rowHeights.indices) {
            rowOffsets[i] = acc
            acc += rowHeights[i] + gap
        }

        val placeables = measurables.mapIndexed { index, measurable ->
            val cell = cells[index]
            val spanW = unitW * cell.colSpan + gap * (cell.colSpan - 1)
            val spanH = (0 until cell.rowSpan).sumOf { r ->
                rowHeights.getOrElse(cell.row + r) { unitH }
            } + gap * (cell.rowSpan - 1)
            val w = if (preview != null && preview.cardId == cell.entry.id) preview.width else spanW
            val h = if (preview != null && preview.cardId == cell.entry.id) preview.height else spanH
            measurable.measure(Constraints.fixed(w, h))
        }

        val totalH = if (rowHeights.isEmpty()) 0 else acc - gap
        layout(width, totalH) {
            placeables.forEachIndexed { index, placeable ->
                val cell = cells[index]
                val ox = if (preview != null && preview.cardId == cell.entry.id) preview.offsetX else 0
                val oy = if (preview != null && preview.cardId == cell.entry.id) preview.offsetY else 0
                placeable.placeRelative(
                    x = cell.col * (unitW + gap) + ox,
                    y = rowOffsets.getOrElse(cell.row) { 0 } + oy
                )
            }
        }
    }
}

/**
 * 便当盒交互网格。
 *
 * 1. 非编辑态只响应「点击」与「长按进入编辑」，纵向滑动完全交给父级 LazyColumn；
 * 2. 编辑态按住卡片拖动换位、四颗圆点实时拉伸；
 * 3. 编辑态每张卡右上角「×」把它从首页移除（进插件页「可添加」）。
 */
@Composable
fun InteractiveBentoSection(
    cards: List<CardLayoutEntry>,
    glanceFor: (String) -> GlanceModel,
    specFor: (String) -> CardSpec,
    onTarget: (cardId: String, target: GlanceTarget) -> Unit,
    isEditMode: Boolean,
    onEnterEditMode: () -> Unit,
    onSetCardSize: (String, BentoCardSize) -> Unit,
    onSwapCards: (String, String) -> Unit,
    onHideCard: (String) -> Unit,
    onCardBounds: (Map<String, Rect>) -> Unit,
    onExitEditMode: () -> Unit,
    modifier: Modifier = Modifier,
    onUpdateLayout: ((List<CardLayoutEntry>) -> Unit)? = null,
    onInteractingChanged: ((Boolean) -> Unit)? = null
) {
    val palette = CampusTheme.palette
    val largeFont = LocalLargeFont.current
    val haptic = LocalHapticFeedback.current
    val cardBounds = remember { mutableStateMapOf<String, Rect>() }
    val animScope = rememberCoroutineScope()
    var draggingCardId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dropTargetId by remember { mutableStateOf<String?>(null) }

    var resizingId by remember { mutableStateOf<String?>(null) }
    var activeHandle by remember { mutableStateOf<ResizeHandle?>(null) }
    var resizeBaseW by remember { mutableFloatStateOf(0f) }
    var resizeBaseH by remember { mutableFloatStateOf(0f) }
    var resizeWFloat by remember { mutableFloatStateOf(0f) }
    var resizeHFloat by remember { mutableFloatStateOf(0f) }
    val resizeW = resizeWFloat.toInt()
    val resizeH = resizeHFloat.toInt()
    var localLayoutOverride by remember { mutableStateOf<List<CardLayoutEntry>?>(null) }
    LaunchedEffect(cards) {
        if (localLayoutOverride == cards) {
            localLayoutOverride = null
        }
    }
    val currentCards = localLayoutOverride ?: cards
    val cells = remember(currentCards) { layoutBentoGrid(currentCards) }
    val rowCount = remember(cells) { cells.maxOfOrNull { it.row + it.rowSpan } ?: 0 }
    val isInteracting = draggingCardId != null || resizingId != null
    LaunchedEffect(isInteracting) {
        onInteractingChanged?.invoke(isInteracting)
    }

    // 把卡片在根坐标系里的位置上报给桌宠层（栖息定位用）
    LaunchedEffect(Unit) {
        snapshotFlow { cardBounds.toMap() }.collect { onCardBounds(it) }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val gapPx = with(density) { CampusTokens.Space.gridGap.roundToPx() }
        val unitHpx = with(density) { CampusTokens.Card.unitHeight.roundToPx() }
        val unitWpx = with(density) { ((maxWidth - CampusTokens.Space.gridGap) / 2).roundToPx() }
        val hitSlopPx = with(density) { 10.dp.toPx() }
        val wideW = unitWpx * 2 + gapPx
        val tallH = unitHpx * 2 + gapPx

        Column(modifier = Modifier.fillMaxWidth()) {

            if (rowCount > 0) {
                val preview = resizingId?.let { id ->
                    val ox = when {
                        activeHandle == ResizeHandle.START && resizeW > resizeBaseW.toInt() -> -(resizeW - resizeBaseW.toInt())
                        else -> 0
                    }
                    val oy = when {
                        activeHandle == ResizeHandle.TOP && resizeH > resizeBaseH.toInt() -> -(resizeH - resizeBaseH.toInt())
                        else -> 0
                    }
                    BentoResizePreview(id, resizeW, resizeH, offsetX = ox, offsetY = oy)
                }

                BentoGrid(
                    cells = cells,
                    rowCount = rowCount,
                    largeFont = largeFont,
                    preview = preview
                ) { cell ->
                    val cardId = cell.entry.id
                    val isDragging = draggingCardId == cardId
                    val isResizing = resizingId == cardId

                    val effectiveSize = cell.entry.size

                    val minColSpan = if (cardId == currentCards.firstOrNull()?.id) 2 else 1
                    val targetX = cell.col * (unitWpx + gapPx)
                    val targetY = cell.row * (unitHpx + gapPx)

                    InteractiveBentoCard(
                        entry = cell.entry,
                        // 入场错峰：每张卡按网格顺序延后 40ms，最多 320ms
                        entranceIndex = cells.indexOf(cell),
                        glance = glanceFor(cardId),
                        spec = specFor(cardId),
                        col = cell.col,
                        row = cell.row,
                        targetX = targetX,
                        targetY = targetY,
                        minColSpan = minColSpan,
                        effectiveSize = effectiveSize,
                        modifier = Modifier.fillMaxSize(),
                        isEditMode = isEditMode,
                        isDragging = isDragging,
                        isResizing = isResizing,
                        isDropTarget = dropTargetId == cardId && draggingCardId != cardId,
                        dragOffset = if (isDragging) dragOffset else Offset.Zero,
                        onBoundsChanged = { rect -> cardBounds[cardId] = rect },
                        onTap = { glanceFor(cardId).tapTarget?.let { onTarget(cardId, it) } },
                        onExitEditMode = onExitEditMode,
                        onLongPressWake = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onEnterEditMode()
                        },
                        onDragStart = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (!isEditMode) onEnterEditMode()
                            draggingCardId = cardId
                            dragOffset = Offset.Zero
                            dropTargetId = null
                        },
                        onDragDelta = { delta ->
                            dragOffset += delta
                            val currentRect = cardBounds[cardId]
                            if (currentRect != null) {
                                val probePoint = currentRect.center + dragOffset
                                val hit = cardBounds.entries.firstOrNull { (otherId, rect) ->
                                    otherId != cardId && rect.inflate(hitSlopPx).contains(probePoint)
                                }?.key
                                if (hit != dropTargetId) {
                                    dropTargetId = hit
                                    if (hit != null) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            val target = dropTargetId
                            draggingCardId = null
                            dragOffset = Offset.Zero
                            dropTargetId = null
                            if (target != null) onSwapCards(cardId, target)
                        },
                        onResizeStart = { handle ->
                            activeHandle = handle
                            val baseW = if (cell.colSpan == 1) unitWpx.toFloat() else wideW.toFloat()
                            val baseH = if (cell.rowSpan == 1) unitHpx.toFloat() else tallH.toFloat()
                            resizeBaseW = baseW
                            resizeBaseH = baseH
                            resizeWFloat = baseW
                            resizeHFloat = baseH
                            resizingId = cardId
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onResizeDelta = { deltaSigned ->
                            val spec = specFor(cardId)
                            val supportedCols = spec.supportedSizes.map { it.colSpan }.toSet()
                            val supportedRows = spec.supportedSizes.map { it.rowSpan }.toSet()
                            val minW = (if (minColSpan == 2 || 1 !in supportedCols) wideW else unitWpx).toFloat()
                            val maxW = (if (2 !in supportedCols) unitWpx else wideW).toFloat()
                            val minH = (if (1 !in supportedRows) tallH else unitHpx).toFloat()
                            val maxH = (if (2 !in supportedRows) unitHpx else tallH).toFloat()

                            if (activeHandle?.isHorizontal == true) {
                                val next = resizeWFloat + deltaSigned
                                resizeWFloat = when {
                                    next < minW -> minW - (minW - next) * 0.35f
                                    next > maxW -> maxW + (next - maxW) * 0.35f
                                    else -> next
                                }
                            } else {
                                val next = resizeHFloat + deltaSigned
                                resizeHFloat = when {
                                    next < minH -> minH - (minH - next) * 0.35f
                                    next > maxH -> maxH + (next - maxH) * 0.35f
                                    else -> next
                                }
                            }
                        },
                        onResizeEnd = {
                            val spec = specFor(cardId)
                            val colSpan = if (resizeWFloat > (unitWpx + wideW) / 2f) 2 else 1
                            val rowSpan = if (resizeHFloat > (unitHpx + tallH) / 2f) 2 else 1
                            val finalColSpan = maxOf(minColSpan, colSpan)
                            val target = BentoCardSize.of(finalColSpan, rowSpan)
                            val finalTarget = if (target in spec.supportedSizes) target else cell.entry.size
                            val targetW = if (finalTarget.colSpan == 1) unitWpx.toFloat() else wideW.toFloat()
                            val targetH = if (finalTarget.rowSpan == 1) unitHpx.toFloat() else tallH.toFloat()

                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            animScope.launch {
                                val startW = resizeWFloat
                                val startH = resizeHFloat
                                animate(
                                    initialValue = 0f,
                                    targetValue = 1f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ) { progress, _ ->
                                    resizeWFloat = startW + (targetW - startW) * progress
                                    resizeHFloat = startH + (targetH - startH) * progress
                                }
                                val nextCards = applyResize(
                                    cards = currentCards,
                                    cardId = cardId,
                                    newSize = finalTarget,
                                    handle = activeHandle,
                                    cells = cells
                                )
                                val normalizedNext = CardLayoutCodec.normalizeFirstCard(
                                    nextCards,
                                    currentCards.map { specFor(it.id) }
                                )
                                localLayoutOverride = normalizedNext
                                if (onUpdateLayout != null) {
                                    onUpdateLayout(normalizedNext)
                                } else if (finalTarget != cell.entry.size) {
                                    onSetCardSize(cardId, finalTarget)
                                }
                                resizingId = null
                                activeHandle = null
                            }
                        },
                        onResizeCancel = {
                            val baseW = resizeBaseW
                            val baseH = resizeBaseH
                            if (resizingId != null && (resizeWFloat != baseW || resizeHFloat != baseH)) {
                                val startW = resizeWFloat
                                val startH = resizeHFloat
                                animScope.launch {
                                    animate(
                                        initialValue = 0f,
                                        targetValue = 1f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    ) { progress, _ ->
                                        resizeWFloat = startW + (baseW - startW) * progress
                                        resizeHFloat = startH + (baseH - startH) * progress
                                    }
                                    resizingId = null
                                    activeHandle = null
                                }
                            } else {
                                resizingId = null
                                activeHandle = null
                            }
                        },
                        onHide = { onHideCard(cardId) },
                        onTarget = { target -> onTarget(cardId, target) }
                    )
                }
            }
        }
    }
}

/**
 * 贴边缩放圆点：视觉上被卡片边框一分为二，触摸区域仍完整落在卡片内。
 */
@Composable
private fun BoxScope.ResizeDot(
    handle: ResizeHandle,
    onStart: () -> Unit,
    onDelta: (Float) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit
) {
    val palette = CampusTheme.palette
    val touchSize = 36.dp
    val halfTouch = touchSize / 2
    val dotSize = 14.dp
    val horizontal = handle.isHorizontal
    val growSign = handle.growSign

    Box(
        modifier = Modifier
            .align(handle.alignment)
            .offset(
                x = if (horizontal) halfTouch * growSign else 0.dp,
                y = if (horizontal) 0.dp else halfTouch * growSign
            )
            .size(touchSize)
            .systemGestureExclusion()
            .semantics {
                contentDescription = handle.description
            }
            .pointerInput(handle) {
                detectDragGestures(
                    onDragStart = { onStart() },
                    onDragEnd = { onEnd() },
                    onDragCancel = { onCancel() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val delta = if (horizontal) dragAmount.x else dragAmount.y
                        onDelta(delta * growSign)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .background(palette.surface, CircleShape)
                .border(2.dp, palette.water, CircleShape)
        )
    }
}

@Composable
private fun InteractiveBentoCard(
    entry: CardLayoutEntry,
    entranceIndex: Int,
    glance: GlanceModel,
    spec: CardSpec,
    col: Int,
    row: Int,
    targetX: Int,
    targetY: Int,
    minColSpan: Int,
    effectiveSize: BentoCardSize,
    modifier: Modifier,
    isEditMode: Boolean,
    isDragging: Boolean,
    isResizing: Boolean,
    isDropTarget: Boolean,
    dragOffset: Offset,
    onBoundsChanged: (Rect) -> Unit,
    onTap: () -> Unit,
    onExitEditMode: () -> Unit,
    onLongPressWake: () -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onResizeStart: (ResizeHandle) -> Unit,
    onResizeDelta: (Float) -> Unit,
    onResizeEnd: () -> Unit,
    onResizeCancel: () -> Unit,
    onHide: () -> Unit,
    onTarget: (GlanceTarget) -> Unit
) {
    val palette = CampusTheme.palette

    val infiniteTransition = rememberInfiniteTransition(label = "jiggle_${entry.id}")
    val jiggleAngle by infiniteTransition.animateFloat(
        initialValue = -0.75f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(animation = tween(190), repeatMode = RepeatMode.Reverse),
        label = "jiggleAngle"
    )

    // 按下反馈：卡片自己就是可点区域，缩一点点比涟漪更「跟手」
    var pressed by remember { mutableStateOf(false) }

    val cardScale by animateFloatAsState(
        targetValue = when {
            isDragging -> 1.04f
            isEditMode -> 0.985f
            pressed -> 0.975f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "cardScale"
    )

    // 入场：第一帧 alpha=0、下沉 10dp，按 entranceIndex 错峰上浮。只播一次。
    val entrance = remember { Animatable(0f) }
    val risePx = with(LocalDensity.current) { 10.dp.toPx() }
    LaunchedEffect(Unit) {
        delay((entranceIndex.coerceIn(0, 8) * 40).toLong())
        entrance.animateTo(1f, tween(durationMillis = 260, easing = LinearOutSlowInEasing))
    }


    // 手势分离：非编辑态不消费滑动，编辑态长按后跟手拖动
    // 关键：key 必须包含 isEditMode，否则进入编辑态时 Compose 不会重启手势块，导致未换位的卡片无法拖动！
    val gestureModifier = if (!isEditMode) {
        Modifier.pointerInput(entry.id, isEditMode) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    try {
                        tryAwaitRelease()
                    } finally {
                        pressed = false
                    }
                },
                onTap = { onTap() },
                onLongPress = { onLongPressWake() }
            )
        }
    } else {
        Modifier
            .pointerInput(entry.id, isEditMode) {
                detectTapGestures(
                    onTap = { onExitEditMode() }
                )
            }
            .pointerInput(entry.id, isEditMode) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                )
            }
    }
    val animTargetX by animateFloatAsState(
        targetValue = targetX.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "animTargetX_${entry.id}"
    )
    val animTargetY by animateFloatAsState(
        targetValue = targetY.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "animTargetY_${entry.id}"
    )

    Box(
        modifier = modifier
            .zIndex(if (isDragging || isResizing) 100f else 1f)
            .onGloballyPositioned { coords -> onBoundsChanged(coords.boundsInRoot()) }
            .graphicsLayer {
                val shiftX = when {
                    isDragging -> dragOffset.x
                    isResizing -> 0f
                    else -> animTargetX - targetX
                }
                val shiftY = when {
                    isDragging -> dragOffset.y
                    isResizing -> 0f
                    else -> animTargetY - targetY
                }
                translationX = shiftX
                translationY = shiftY + (1f - entrance.value) * risePx
                scaleX = cardScale
                scaleY = cardScale
                alpha = entrance.value
                rotationZ = if (isEditMode && !isDragging) jiggleAngle else 0f
            }
            .then(gestureModifier)
    ) {
        val cardShape = if (effectiveSize == BentoCardSize.HERO_2X2) {
            CampusTokens.Shapes.large
        } else {
            CampusTokens.Shapes.medium
        }
        Card(
            modifier = Modifier
                .fillMaxSize()
                // 阴影画在裁剪之前：clipToBounds 在链上更靠后，只裁内容不裁投影。
                .cardDepth(
                    shape = cardShape,
                    raised = isDragging || isDropTarget || effectiveSize == BentoCardSize.HERO_2X2
                )
                .clipToBounds(),
            shape = cardShape,
            colors = CardDefaults.cardColors(
                containerColor = if (isDropTarget) palette.waterSoft else palette.surface
            ),
            border = BorderStroke(
                width = if (isDragging || isDropTarget) 2.dp else if (isEditMode) 1.2.dp else 1.dp,
                color = when {
                    isDragging || isDropTarget -> palette.water
                    isEditMode -> palette.water.copy(alpha = 0.5f)
                    else -> palette.line.copy(alpha = CampusTokens.Depth.borderAlpha)
                }
            )
        ) {
            GlanceCard(
                model = glance,
                spec = spec,
                size = effectiveSize,
                onTarget = onTarget
            )
        }

        if (isEditMode) {
            // 移除按钮：编辑态才出现，点一次把卡片收进插件页「可添加」
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .background(palette.surface, CircleShape)
                    .border(1.dp, palette.line, CircleShape)
                    .semantics { contentDescription = "从首页移除 ${spec.title}" }
                    .pointerInput(entry.id) {
                        detectTapGestures { onHide() }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "×", color = palette.danger, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            val supportedCols = spec.supportedSizes.map { it.colSpan }.toSet()
            val supportedRows = spec.supportedSizes.map { it.rowSpan }.toSet()

            val canGrowH = entry.size.colSpan == 1 && 2 in supportedCols
            val canShrinkH = entry.size.colSpan == 2 && 1 in supportedCols && minColSpan != 2

            val canGrowV = entry.size.rowSpan == 1 && 2 in supportedRows
            val canShrinkV = entry.size.rowSpan == 2 && 1 in supportedRows

            val showStart = (canGrowH && col == 1) || canShrinkH
            val showEnd = (canGrowH && col == 0) || canShrinkH
            val showTop = (canGrowV && row > 0) || canShrinkV
            val showBottom = canGrowV || canShrinkV

            if (showTop) {
                ResizeDot(
                    handle = ResizeHandle.TOP,
                    onStart = { onResizeStart(ResizeHandle.TOP) },
                    onDelta = onResizeDelta,
                    onEnd = onResizeEnd,
                    onCancel = onResizeCancel
                )
            }
            if (showBottom) {
                ResizeDot(
                    handle = ResizeHandle.BOTTOM,
                    onStart = { onResizeStart(ResizeHandle.BOTTOM) },
                    onDelta = onResizeDelta,
                    onEnd = onResizeEnd,
                    onCancel = onResizeCancel
                )
            }
            if (showStart) {
                ResizeDot(
                    handle = ResizeHandle.START,
                    onStart = { onResizeStart(ResizeHandle.START) },
                    onDelta = onResizeDelta,
                    onEnd = onResizeEnd,
                    onCancel = onResizeCancel
                )
            }
            if (showEnd) {
                ResizeDot(
                    handle = ResizeHandle.END,
                    onStart = { onResizeStart(ResizeHandle.END) },
                    onDelta = onResizeDelta,
                    onEnd = onResizeEnd,
                    onCancel = onResizeCancel
                )
            }
        }
    }
}
