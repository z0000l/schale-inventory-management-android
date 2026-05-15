package net.terryu16.schale.inventory.ui.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import net.terryu16.schale.inventory.data.ItemGroup
import net.terryu16.schale.inventory.ui.PlacementMode
import net.terryu16.schale.inventory.ui.UiState
import net.terryu16.schale.inventory.ui.theme.SchaleColors
import net.terryu16.schale.inventory.ui.theme.itemColor

@Composable
fun ItemSidePanel(
    state: UiState,
    modifier: Modifier = Modifier,
    onModifyItem: (itemIndex: Int, height: Int?, width: Int?, count: Int?) -> Unit,
    onStartPlace: (itemIndex: Int, rotated: Boolean) -> Unit,
    onRemovePlaced: (id: String) -> Unit,
    currentPlacement: PlacementMode?,
) {
    Surface(
        modifier = modifier,
        color = SchaleColors.BgPanel,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            state.itemGroups.forEachIndexed { idx, group ->
                ItemCard(
                    itemIndex = idx,
                    group = group,
                    placedCount = state.placedItems.count { it.item.itemIndex == idx },
                    onChangeHeight = { onModifyItem(idx, it, null, null) },
                    onChangeWidth = { onModifyItem(idx, null, it, null) },
                    onChangeCount = { onModifyItem(idx, null, null, it) },
                    onPlace = { rotated -> onStartPlace(idx, rotated) },
                    placedItems = state.placedItems
                        .filter { it.item.itemIndex == idx }
                        .map { it.id to (if (it.rotated) "${it.effectiveHeight}×${it.effectiveWidth} ↻" else "${it.effectiveHeight}×${it.effectiveWidth}") },
                    onRemovePlaced = onRemovePlaced,
                    isActivePlacement = currentPlacement?.itemIndex == idx,
                    activeRotated = currentPlacement?.takeIf { it.itemIndex == idx }?.rotated,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ItemCard(
    itemIndex: Int,
    group: ItemGroup,
    placedCount: Int,
    onChangeHeight: (Int) -> Unit,
    onChangeWidth: (Int) -> Unit,
    onChangeCount: (Int) -> Unit,
    onPlace: (rotated: Boolean) -> Unit,
    placedItems: List<Pair<String, String>>,
    onRemovePlaced: (id: String) -> Unit,
    isActivePlacement: Boolean,
    activeRotated: Boolean?,
    modifier: Modifier = Modifier,
) {
    val tint = itemColor(itemIndex)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SchaleColors.BgPanelHigh,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isActivePlacement) tint else Color.Transparent),
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            // 标题行
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(tint, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${itemIndex + 1}",
                        color = Color(0xFF181818),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    "物品 ${itemIndex + 1}",
                    color = SchaleColors.TextPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "$placedCount/${group.count}",
                    color = SchaleColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            // 高/宽/数 三列紧凑步进器
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                NumberStepper(
                    label = "高", value = group.item.height,
                    range = 1..4, onChange = onChangeHeight,
                    modifier = Modifier.weight(1f),
                )
                NumberStepper(
                    label = "宽", value = group.item.width,
                    range = 1..4, onChange = onChangeWidth,
                    modifier = Modifier.weight(1f),
                )
                NumberStepper(
                    label = "数", value = group.count,
                    range = 0..8, onChange = onChangeCount,
                    modifier = Modifier.weight(1f),
                )
            }

            // 放置按钮行：[删除已放置 dropdown] [按当前形状放置] [旋转后放置 (仅矩形)]
            val isSquare = group.item.height == group.item.width
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlacedDropdownButton(
                    placedItems = placedItems,
                    onRemove = onRemovePlaced,
                    color = tint,
                )
                PlaceButton(
                    text = "${group.item.height}×${group.item.width}",
                    color = tint,
                    enabled = placedCount < group.count,
                    active = isActivePlacement && activeRotated == false,
                    onClick = { onPlace(false) },
                    modifier = Modifier.weight(1f),
                )
                if (!isSquare) {
                    PlaceButton(
                        text = "${group.item.width}×${group.item.height}",
                        color = tint,
                        icon = Icons.Filled.Rotate90DegreesCw,
                        enabled = placedCount < group.count,
                        active = isActivePlacement && activeRotated == true,
                        onClick = { onPlace(true) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * 已放置物品的下拉删除按钮：
 * - 触发按钮：trash 图标的方块按钮，无已放置物品时灰化禁用
 * - 展开后：横向胶囊弹窗，列出 1/2/3/... 序号 chip，仅显数字
 * - 容器色用物品 tint，与右栏深色底强对比；点击 chip 即删除对应物品
 */
@Composable
private fun PlacedDropdownButton(
    placedItems: List<Pair<String, String>>,
    onRemove: (id: String) -> Unit,
    color: Color,
) {
    var expanded by remember { mutableStateOf(false) }
    val enabled = placedItems.isNotEmpty()
    val density = LocalDensity.current
    Box {
        Surface(
            onClick = { if (enabled) expanded = !expanded },
            enabled = enabled,
            modifier = Modifier
                .height(30.dp)
                .width(34.dp),
            color = if (enabled) color.copy(alpha = 0.18f) else SchaleColors.BgPanel,
            contentColor = if (enabled) color else SchaleColors.TextDisabled,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, if (enabled) color.copy(alpha = 0.5f) else SchaleColors.Divider),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "删除已放置物品",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (expanded) {
            Popup(
                alignment = Alignment.BottomStart,
                // 按钮高 30 dp，向下偏移 4 dp 使弹窗紧贴按钮底
                offset = IntOffset(0, with(density) { 4.dp.roundToPx() }),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    color = color,
                    shape = RoundedCornerShape(14.dp),
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, Color(0x33000000)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        placedItems.forEachIndexed { i, (id, _) ->
                            NumberChip(
                                index = i + 1,
                                onClick = {
                                    onRemove(id)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberChip(index: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color(0xFF111111).copy(alpha = 0.18f),
        contentColor = Color(0xFF111111),
        modifier = Modifier.size(26.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "$index",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/**
 * 紧凑数字步进器：标签 + 横向 [−] 数字 [+]
 * 用 Icon 而非 Text 字符，避免在窄列下被字体上下边距挤裁。
 */
@Composable
private fun NumberStepper(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            color = SchaleColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SchaleColors.BgPanel, RoundedCornerShape(6.dp))
                .padding(horizontal = 1.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StepperIconButton(
                icon = Icons.Filled.Remove,
                enabled = value > range.first,
                onClick = { if (value > range.first) onChange(value - 1) },
            )
            Text(
                value.toString(),
                color = SchaleColors.TextPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            StepperIconButton(
                icon = Icons.Filled.Add,
                enabled = value < range.last,
                onClick = { if (value < range.last) onChange(value + 1) },
            )
        }
    }
}

@Composable
private fun StepperIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) SchaleColors.TextPrimary else SchaleColors.TextDisabled,
            modifier = Modifier.size(12.dp),
        )
    }
}

/**
 * 紧凑放置按钮：单行 [icon] [W×H]，无 "放置" 字样以节省宽度。
 * Icon 表达动作：+ 表示按当前形状放置，↻ 表示旋转后放置。
 *
 * 当 active=true 时（即当前激活的放置模式），再次点击会触发 onClick —— 上层把它解释为
 * "退出放置模式"。
 */
@Composable
private fun PlaceButton(
    text: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    enabled: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        // active 时即使 placedCount 已满也允许点击（用来退出放置模式）
        enabled = enabled || active,
        modifier = modifier.height(30.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) color else color.copy(alpha = 0.18f),
            contentColor = if (active) Color(0xFF111111) else SchaleColors.TextPrimary,
            disabledContainerColor = SchaleColors.BgPanel,
            disabledContentColor = SchaleColors.TextDisabled,
        ),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
    ) {
        Icon(
            icon ?: Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
