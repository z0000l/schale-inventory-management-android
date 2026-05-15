package net.terryu16.schale.inventory.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.terryu16.schale.inventory.R
import net.terryu16.schale.inventory.data.Coord
import net.terryu16.schale.inventory.ui.board.BoardCanvas
import net.terryu16.schale.inventory.ui.panel.ItemSidePanel
import net.terryu16.schale.inventory.ui.theme.SchaleColors

/**
 * 当前活动的放置模式，null 表示棋盘处于普通"翻牌"模式。
 *
 * 两步式放置：
 *   1. 用户从右栏点击"放置 W×H"按钮 → placement=PlacementMode(idx, rot, previewCoord=null)
 *   2. 用户在棋盘上点击任意格子 → previewCoord 被填上，棋盘渲染闪烁/半透明预览
 *   3. 用户在预览区域内再点击一次 → 确认放置
 *      用户点击预览以外的合法格子 → 预览移到新位置
 *      用户再次点击右栏当前激活的放置按钮 → 完全退出放置模式
 */
data class PlacementMode(
    val itemIndex: Int,
    val rotated: Boolean,
    val previewCoord: Coord? = null,
)

@Composable
fun HomeScreen(viewModel: InventoryViewModel) {
    val state by viewModel.uiState.collectAsState()
    var placement by remember { mutableStateOf<PlacementMode?>(null) }
    var resetDialogVisible by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        containerColor = SchaleColors.BgMain,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = SchaleColors.BgMain,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                SchaleColors.BgMain,
                                SchaleColors.BgPanel.copy(alpha = 0.6f),
                                SchaleColors.BgMain,
                            )
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 中央列：9×5 棋盘 + 下方右对齐的操作按钮行
                    // 按钮放在棋盘下方独立 Row 中，避免遮挡棋盘格子
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.74f),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            BoardCanvas(
                                state = state,
                                placement = placement,
                                onCellTap = { row, col ->
                                    val mode = placement
                                    if (mode != null) {
                                        val group = state.itemGroups[mode.itemIndex]
                                        val eh = if (mode.rotated) group.item.width else group.item.height
                                        val ew = if (mode.rotated) group.item.height else group.item.width
                                        val preview = mode.previewCoord
                                        if (preview != null &&
                                            row in preview.row until preview.row + eh &&
                                            col in preview.col until preview.col + ew
                                        ) {
                                            // 点击预览区域内 → 确认放置
                                            viewModel.addPlacedItem(
                                                mode.itemIndex,
                                                preview.row,
                                                preview.col,
                                                mode.rotated,
                                            )
                                            placement = null
                                        } else {
                                            // 移动预览到新格子（合法性由 BoardCanvas 渲染时判断）
                                            placement = mode.copy(previewCoord = Coord(row, col))
                                        }
                                    } else {
                                        viewModel.toggleCell(row, col)
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        BoardFloatingActions(
                            state = state,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, end = 4.dp),
                            onCalculate = viewModel::calculate,
                            onReset = { resetDialogVisible = true },
                        )
                    }

                    // 右侧：3 个物品卡片纵向均分
                    ItemSidePanel(
                        state = state,
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.26f),
                        onModifyItem = { idx, h, w, c ->
                            viewModel.modifyItemGroup(idx, h, w, c)
                        },
                        onStartPlace = { itemIndex, rotated ->
                            // 再次点击当前激活的放置按钮 → 取消放置
                            placement = if (placement?.itemIndex == itemIndex && placement?.rotated == rotated) {
                                null
                            } else {
                                PlacementMode(itemIndex, rotated)
                            }
                        },
                        onRemovePlaced = viewModel::removePlacedItem,
                        currentPlacement = placement,
                    )
                }

                // 计算中浮动提示
                AnimatedVisibility(
                    visible = state.running,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp),
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically(),
                ) {
                    Surface(
                        color = SchaleColors.Primary,
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Text(
                            stringResource(R.string.status_running),
                            color = SchaleColors.TextPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }

        if (resetDialogVisible) {
            AlertDialog(
                onDismissRequest = { resetDialogVisible = false },
                title = { Text(stringResource(R.string.confirm_reset_title)) },
                text = { Text(stringResource(R.string.confirm_reset_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.resetBoard()
                        placement = null
                        resetDialogVisible = false
                    }) { Text(stringResource(R.string.action_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { resetDialogVisible = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                containerColor = SchaleColors.BgPanelHigh,
            )
        }
    }
}

/**
 * 棋盘右下角的两个悬浮操作按钮：重新计算 + 重置棋盘。
 *
 * 棋盘单元格被居中布局，剩余空间在两侧/上下作为内边距。
 * 把按钮锚定在 BottomEnd，落在右下角的内边距区域，不会盖到格子上。
 */
@Composable
private fun BoardFloatingActions(
    state: UiState,
    modifier: Modifier = Modifier,
    onCalculate: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FloatingIconButton(
            icon = if (state.dirty) Icons.Filled.Bolt else Icons.Filled.PlayArrow,
            contentDescription = stringResource(
                if (state.dirty) R.string.action_calculate else R.string.action_recalculate
            ),
            onClick = onCalculate,
            enabled = !state.running,
            container = if (state.dirty) SchaleColors.Accent else SchaleColors.Primary,
            content = if (state.dirty) Color(0xFF181818) else Color.White,
            size = 48.dp,
        )
        FloatingIconButton(
            icon = Icons.Filled.Refresh,
            contentDescription = stringResource(R.string.action_reset),
            onClick = onReset,
            enabled = true,
            container = SchaleColors.BgPanelHigh,
            content = SchaleColors.TextSecondary,
            size = 40.dp,
        )
    }
}

@Composable
private fun FloatingIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
    container: Color,
    content: Color,
    size: androidx.compose.ui.unit.Dp,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(size),
        color = container,
        contentColor = content,
        shape = CircleShape,
        shadowElevation = 6.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(size * 0.45f),
            )
        }
    }
}
