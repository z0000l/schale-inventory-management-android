package net.terryu16.schale.inventory.ui.board

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.terryu16.schale.inventory.data.Board
import net.terryu16.schale.inventory.data.PlacedItem
import net.terryu16.schale.inventory.ui.PlacementMode
import net.terryu16.schale.inventory.ui.UiState
import net.terryu16.schale.inventory.ui.theme.SchaleColors
import net.terryu16.schale.inventory.ui.theme.itemColor

/**
 * 中央 9×5 棋盘。
 *
 * 视觉层次（从下往上）：
 *   1. 棋盘底面 + 网格线
 *   2. 已放置物品（带物品色块）
 *   3. 未翻开格子的暗色磨砂盖板
 *   4. 已翻开格子的概率热力图叠加
 *   5. 「推荐翻开」格子的呼吸光晕
 *   6. 概率百分比文字标签
 */
@Composable
fun BoardCanvas(
    state: UiState,
    placement: PlacementMode?,
    onCellTap: (row: Int, col: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var boardSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    // 按 9×5 比例内嵌的格子布局参数
    data class Layout(val gridLeft: Float, val gridTop: Float, val cellSize: Float)
    val layout = remember(boardSize) {
        if (boardSize.width == 0 || boardSize.height == 0) {
            Layout(0f, 0f, 0f)
        } else {
            val pad = with(density) { 16.dp.toPx() }
            val maxW = boardSize.width - pad * 2
            val maxH = boardSize.height - pad * 2
            val cs = minOf(maxW / Board.WIDTH, maxH / Board.HEIGHT)
            val w = cs * Board.WIDTH
            val h = cs * Board.HEIGHT
            Layout(
                (boardSize.width - w) / 2f,
                (boardSize.height - h) / 2f,
                cs,
            )
        }
    }

    val currentProb = state.currentProb()
    val currentIsMax = state.currentIsMax()
    val activeFlag = state.activeFlag
    val pulse = rememberPulse()

    Surface(
        modifier = modifier
            .shadow(elevation = 18.dp, shape = RoundedCornerShape(20.dp))
            .background(SchaleColors.BoardBg, RoundedCornerShape(20.dp)),
        color = SchaleColors.BoardBg,
        shape = RoundedCornerShape(20.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { boardSize = it }
                    .pointerInput(layout, state.itemGroups, state.placedItems, placement) {
                        detectTapGestures { offset ->
                            val (r, c) = cellAt(offset, layout.gridLeft, layout.gridTop, layout.cellSize)
                            if (r != null && c != null) onCellTap(r, c)
                        }
                    },
            ) {
                if (layout.cellSize == 0f) return@Canvas
                drawBoardBackground(layout.gridLeft, layout.gridTop, layout.cellSize)
                drawPlacedItems(state.placedItems, layout.gridLeft, layout.gridTop, layout.cellSize)
                drawCovers(
                    state = state,
                    flag = activeFlag,
                    prob = currentProb,
                    isMax = currentIsMax,
                    pulse = pulse,
                    gridLeft = layout.gridLeft,
                    gridTop = layout.gridTop,
                    cellSize = layout.cellSize,
                )
                if (placement?.previewCoord != null) {
                    drawPlacementPreview(
                        placement = placement,
                        state = state,
                        pulse = pulse,
                        gridLeft = layout.gridLeft,
                        gridTop = layout.gridTop,
                        cellSize = layout.cellSize,
                    )
                }
            }

            // 概率百分比文字层（脱离 Canvas，便于使用系统字体）
            if (layout.cellSize > 0f && currentProb != null) {
                ProbabilityLabels(
                    state = state,
                    prob = currentProb,
                    isMax = currentIsMax,
                    gridLeft = layout.gridLeft,
                    gridTop = layout.gridTop,
                    cellSize = layout.cellSize,
                )
            }

            // 已放置物品的中央编号标签
            if (layout.cellSize > 0f) {
                PlacedItemLabels(
                    placed = state.placedItems,
                    gridLeft = layout.gridLeft,
                    gridTop = layout.gridTop,
                    cellSize = layout.cellSize,
                )
            }

            // 预览状态下，在预览区域中央显示"点击此格确认放置"
            if (layout.cellSize > 0f && placement?.previewCoord != null) {
                PreviewConfirmLabel(
                    placement = placement,
                    state = state,
                    gridLeft = layout.gridLeft,
                    gridTop = layout.gridTop,
                    cellSize = layout.cellSize,
                )
            }

            // 放置模式下方的提示条
            if (placement != null) {
                PlacementHint(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                    itemIndex = placement.itemIndex,
                    rotated = placement.rotated,
                    hasPreview = placement.previewCoord != null,
                    state = state,
                )
            }
        }
    }
}

private fun DrawScope.drawBoardBackground(
    gridLeft: Float, gridTop: Float, cellSize: Float,
) {
    val w = cellSize * Board.WIDTH
    val h = cellSize * Board.HEIGHT
    drawRoundRect(
        color = SchaleColors.BoardSurface,
        topLeft = Offset(gridLeft - 6f, gridTop - 6f),
        size = Size(w + 12f, h + 12f),
        cornerRadius = CornerRadius(14f, 14f),
    )
    for (r in 0..Board.HEIGHT) {
        drawLine(
            color = SchaleColors.GridLine,
            start = Offset(gridLeft, gridTop + r * cellSize),
            end = Offset(gridLeft + w, gridTop + r * cellSize),
            strokeWidth = 1.2f,
        )
    }
    for (c in 0..Board.WIDTH) {
        drawLine(
            color = SchaleColors.GridLine,
            start = Offset(gridLeft + c * cellSize, gridTop),
            end = Offset(gridLeft + c * cellSize, gridTop + h),
            strokeWidth = 1.2f,
        )
    }
}

private fun DrawScope.drawPlacedItems(
    placed: List<PlacedItem>,
    gridLeft: Float, gridTop: Float, cellSize: Float,
) {
    placed.forEach { p ->
        val color = itemColor(p.item.itemIndex)
        val x = gridLeft + p.coord.col * cellSize
        val y = gridTop + p.coord.row * cellSize
        val w = p.effectiveWidth * cellSize
        val h = p.effectiveHeight * cellSize
        val corner = CornerRadius(cellSize * 0.18f, cellSize * 0.18f)
        drawRoundRect(
            color = color.copy(alpha = 0.85f),
            topLeft = Offset(x + 3f, y + 3f),
            size = Size(w - 6f, h - 6f),
            cornerRadius = corner,
        )
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                startY = y, endY = y + h * 0.55f,
            ),
            topLeft = Offset(x + 3f, y + 3f),
            size = Size(w - 6f, h - 6f),
            cornerRadius = corner,
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(x + 3f, y + 3f),
            size = Size(w - 6f, h - 6f),
            cornerRadius = corner,
            style = Stroke(width = 2.5f),
        )
    }
}

private fun DrawScope.drawCovers(
    state: UiState,
    flag: Int,
    prob: DoubleArray?,
    isMax: BooleanArray?,
    pulse: Float,
    gridLeft: Float, gridTop: Float, cellSize: Float,
) {
    for (r in 0 until Board.HEIGHT) {
        for (c in 0 until Board.WIDTH) {
            val idx = Board.index(r, c)
            val x = gridLeft + c * cellSize
            val y = gridTop + r * cellSize
            val rect = Offset(x + 2f, y + 2f)
            val sz = Size(cellSize - 4f, cellSize - 4f)
            val corner = CornerRadius(cellSize * 0.16f, cellSize * 0.16f)
            val opened = state.openMap[idx]
            val occupied = state.placedItems.any { it.covers(r, c) }

            if (occupied) continue // 已放置物品自己渲染

            if (!opened) {
                drawRoundRect(SchaleColors.CoverUnopened, rect, sz, corner)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.06f), Color.Transparent),
                        startY = y, endY = y + cellSize,
                    ),
                    topLeft = rect, size = sz, cornerRadius = corner,
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.06f),
                    topLeft = rect, size = sz, cornerRadius = corner,
                    style = Stroke(width = 1f),
                )
                if (prob != null) {
                    val p = prob[idx].coerceIn(0.0, 1.0)
                    if (p > 0.001) {
                        val tint = heatColor(p, flag)
                        drawRoundRect(
                            color = tint.copy(alpha = (0.12f + p.toFloat() * 0.58f).coerceAtMost(0.80f)),
                            topLeft = rect, size = sz, cornerRadius = corner,
                        )
                    }
                }
                if (isMax != null && isMax[idx]) {
                    val a = 0.55f + 0.35f * pulse
                    drawRoundRect(
                        color = SchaleColors.BestGlow.copy(alpha = a),
                        topLeft = rect, size = sz, cornerRadius = corner,
                        style = Stroke(width = 3f + 2f * pulse),
                    )
                }
            } else {
                drawRoundRect(
                    color = SchaleColors.CoverOpened.copy(alpha = 0.22f),
                    topLeft = rect, size = sz, cornerRadius = corner,
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.04f),
                    topLeft = rect, size = sz, cornerRadius = corner,
                    style = Stroke(width = 1f),
                )
            }
        }
    }
}

private fun heatColor(p: Double, flag: Int): Color {
    val singleIdx = when (flag) {
        1 -> 0; 2 -> 1; 4 -> 2; else -> -1
    }
    if (singleIdx >= 0) return itemColor(singleIdx)
    val t = p.coerceIn(0.0, 1.0).toFloat()
    val cold = Color(0xFF3A6EE0)
    val warm = Color(0xFFFF7A2A)
    return Color(
        red = cold.red + (warm.red - cold.red) * t,
        green = cold.green + (warm.green - cold.green) * t,
        blue = cold.blue + (warm.blue - cold.blue) * t,
        alpha = 1f,
    )
}

@Composable
private fun ProbabilityLabels(
    state: UiState,
    prob: DoubleArray,
    isMax: BooleanArray?,
    gridLeft: Float,
    gridTop: Float,
    cellSize: Float,
) {
    val density = LocalDensity.current
    Box(modifier = Modifier.fillMaxSize()) {
        for (r in 0 until Board.HEIGHT) {
            for (c in 0 until Board.WIDTH) {
                val idx = Board.index(r, c)
                if (state.openMap[idx]) continue
                if (state.placedItems.any { it.covers(r, c) }) continue
                val p = prob[idx]
                if (p < 0.005) continue
                val percent = formatPercent(p)
                val isPeak = isMax?.get(idx) == true
                Box(
                    modifier = Modifier
                        .absoluteOffset(
                            x = with(density) { (gridLeft + c * cellSize).toDp() },
                            y = with(density) { (gridTop + r * cellSize).toDp() },
                        )
                        .size(with(density) { cellSize.toDp() }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = percent,
                        color = if (isPeak) SchaleColors.BestGlow else SchaleColors.TextPrimary,
                        fontSize = (cellSize * 0.22f / density.density).coerceIn(9f, 18f).sp,
                        fontWeight = if (isPeak) FontWeight.Bold else FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlacedItemLabels(
    placed: List<PlacedItem>,
    gridLeft: Float,
    gridTop: Float,
    cellSize: Float,
) {
    val density = LocalDensity.current
    Box(modifier = Modifier.fillMaxSize()) {
        placed.groupBy { it.item.itemIndex }.forEach { (_, list) ->
            list.forEachIndexed { i, p ->
                val w = p.effectiveWidth * cellSize
                val h = p.effectiveHeight * cellSize
                Box(
                    modifier = Modifier
                        .absoluteOffset(
                            x = with(density) { (gridLeft + p.coord.col * cellSize).toDp() },
                            y = with(density) { (gridTop + p.coord.row * cellSize).toDp() },
                        )
                        .size(
                            width = with(density) { w.toDp() },
                            height = with(density) { h.toDp() },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "#${i + 1}",
                        color = Color(0xFF111111),
                        fontWeight = FontWeight.Bold,
                        fontSize = (minOf(w, h) * 0.28f / density.density).coerceIn(11f, 22f).sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlacementHint(
    modifier: Modifier,
    itemIndex: Int,
    rotated: Boolean,
    hasPreview: Boolean,
    state: UiState,
) {
    val group = state.itemGroups[itemIndex]
    val eh = if (rotated) group.item.width else group.item.height
    val ew = if (rotated) group.item.height else group.item.width
    Surface(
        modifier = modifier,
        color = itemColor(itemIndex).copy(alpha = 0.95f),
        shape = RoundedCornerShape(24.dp),
    ) {
        Text(
            text = "物品 ${itemIndex + 1} · ${eh}×${ew}${if (rotated) " (旋转)" else ""} · " +
                if (hasPreview) "再点预览格确认放置" else "点击格子选择位置",
            color = Color(0xFF111111),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * 在棋盘上画预览矩形：闪烁、半透明、物品色。
 * 若预览位置非法（出界 / 与已放置物品重叠），用红色描边表示。
 */
private fun DrawScope.drawPlacementPreview(
    placement: PlacementMode,
    state: UiState,
    pulse: Float,
    gridLeft: Float, gridTop: Float, cellSize: Float,
) {
    val coord = placement.previewCoord ?: return
    val group = state.itemGroups[placement.itemIndex]
    val eh = if (placement.rotated) group.item.width else group.item.height
    val ew = if (placement.rotated) group.item.height else group.item.width

    // 合法性：在棋盘内 + 不与已放置物品重叠
    var valid = coord.row + eh <= Board.HEIGHT && coord.col + ew <= Board.WIDTH
    if (valid) {
        outer@ for (r in coord.row until coord.row + eh) {
            for (c in coord.col until coord.col + ew) {
                if (state.placedItems.any { it.covers(r, c) }) {
                    valid = false
                    break@outer
                }
            }
        }
    }

    // 越界时按可见范围裁剪绘制（避免画到棋盘外）
    val drawRows = (coord.row until coord.row + eh).filter { it in 0 until Board.HEIGHT }
    val drawCols = (coord.col until coord.col + ew).filter { it in 0 until Board.WIDTH }
    if (drawRows.isEmpty() || drawCols.isEmpty()) return

    val color = itemColor(placement.itemIndex)
    val outlineColor = if (valid) color else Color(0xFFFF5C7A)
    val fillAlpha = (0.25f + 0.35f * pulse) * (if (valid) 1f else 0.5f)
    val x = gridLeft + drawCols.first() * cellSize
    val y = gridTop + drawRows.first() * cellSize
    val w = drawCols.size * cellSize
    val h = drawRows.size * cellSize
    val corner = CornerRadius(cellSize * 0.18f, cellSize * 0.18f)

    drawRoundRect(
        color = color.copy(alpha = fillAlpha),
        topLeft = Offset(x + 3f, y + 3f),
        size = Size(w - 6f, h - 6f),
        cornerRadius = corner,
    )
    drawRoundRect(
        color = outlineColor,
        topLeft = Offset(x + 3f, y + 3f),
        size = Size(w - 6f, h - 6f),
        cornerRadius = corner,
        style = Stroke(width = 3f + 2f * pulse),
    )
}

@Composable
private fun PreviewConfirmLabel(
    placement: PlacementMode,
    state: UiState,
    gridLeft: Float,
    gridTop: Float,
    cellSize: Float,
) {
    val coord = placement.previewCoord ?: return
    val group = state.itemGroups[placement.itemIndex]
    val eh = if (placement.rotated) group.item.width else group.item.height
    val ew = if (placement.rotated) group.item.height else group.item.width

    // 是否合法 —— 非法时不显示"点击确认"，避免误导
    var valid = coord.row + eh <= Board.HEIGHT && coord.col + ew <= Board.WIDTH
    if (valid) {
        outer@ for (r in coord.row until coord.row + eh) {
            for (c in coord.col until coord.col + ew) {
                if (state.placedItems.any { it.covers(r, c) }) {
                    valid = false
                    break@outer
                }
            }
        }
    }
    if (!valid) return

    val density = LocalDensity.current
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .absoluteOffset(
                    x = with(density) { (gridLeft + coord.col * cellSize).toDp() },
                    y = with(density) { (gridTop + coord.row * cellSize).toDp() },
                )
                .size(
                    width = with(density) { (ew * cellSize).toDp() },
                    height = with(density) { (eh * cellSize).toDp() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "点击确认",
                color = Color(0xFF111111),
                fontWeight = FontWeight.Bold,
                fontSize = (cellSize * 0.22f / density.density).coerceIn(10f, 18f).sp,
            )
        }
    }
}

@Composable
private fun rememberPulse(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "best")
    val p by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    return p
}

private fun cellAt(
    offset: Offset, gridLeft: Float, gridTop: Float, cellSize: Float,
): Pair<Int?, Int?> {
    if (cellSize <= 0f) return null to null
    val c = ((offset.x - gridLeft) / cellSize).toInt()
    val r = ((offset.y - gridTop) / cellSize).toInt()
    return if (r in 0 until Board.HEIGHT && c in 0 until Board.WIDTH) r to c else null to null
}

private fun formatPercent(p: Double): String {
    val v = (p * 1000).toInt() / 10.0
    return if (v >= 10.0) "${v.toInt()}%" else "%.1f%%".format(v)
}
