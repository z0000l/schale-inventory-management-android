package net.terryu16.schale.inventory.solver

import net.terryu16.schale.inventory.data.Board
import net.terryu16.schale.inventory.data.Coord
import net.terryu16.schale.inventory.data.Item
import net.terryu16.schale.inventory.data.ItemGroup
import net.terryu16.schale.inventory.data.PlacedItem

/**
 * 求解时使用的不可变状态。
 *
 * - openMap: 哪些格子已经被翻开（不能放未确定物品）
 * - remainingItems: 还未确定位置的物品组（3 种）
 * - placedItems: 已经确定位置的物品
 *
 * 内部预计算两张二维累积和表，使 O(1) 判定矩形范围内是否含被翻开格 / 已占用格。
 */
class SolverState(
    val openMap: BooleanArray,
    val remainingItems: List<ItemGroup>,
    val placedItems: List<SolverPlacedItem>,
) {
    private val vacantPrefix: IntArray
    private val occupiedPrefix: IntArray

    init {
        require(openMap.size == Board.CELL_COUNT) { "openMap size must be ${Board.CELL_COUNT}" }
        require(remainingItems.size == Board.ITEM_GROUP_COUNT)

        val occupied = IntArray(Board.CELL_COUNT)
        for (placed in placedItems) {
            val r0 = placed.coord.row
            val c0 = placed.coord.col
            val r1 = r0 + placed.effectiveHeight
            val c1 = c0 + placed.effectiveWidth
            for (r in r0 until r1) {
                for (c in c0 until c1) {
                    occupied[r * Board.WIDTH + c] += 1
                }
            }
        }

        val vacant = IntArray(Board.CELL_COUNT)
        for (i in 0 until Board.CELL_COUNT) {
            vacant[i] = if (openMap[i] && occupied[i] == 0) 1 else 0
        }

        vacantPrefix = buildPrefix(vacant)
        occupiedPrefix = buildPrefix(occupied)
    }

    /** 半开区间 [r0,r1) × [c0,c1) 内是否存在「已翻开但未被既定物品占用」的格子。 */
    fun hasVacant(r0: Int, c0: Int, r1: Int, c1: Int): Boolean =
        rangeSum(vacantPrefix, r0, c0, r1, c1) > 0

    /** 半开区间 [r0,r1) × [c0,c1) 内是否存在「已被既定物品占用」的格子。 */
    fun hasOccupied(r0: Int, c0: Int, r1: Int, c1: Int): Boolean =
        rangeSum(occupiedPrefix, r0, c0, r1, c1) > 0

    companion object {
        private fun buildPrefix(map: IntArray): IntArray {
            val w = Board.WIDTH
            val h = Board.HEIGHT
            val p = IntArray((w + 1) * (h + 1))
            for (r in 0 until h) {
                for (c in 0 until w) {
                    p[(r + 1) * (w + 1) + (c + 1)] = map[r * w + c]
                }
            }
            for (r in 0..h) {
                for (c in 0 until w) {
                    p[r * (w + 1) + (c + 1)] += p[r * (w + 1) + c]
                }
            }
            for (c in 0..w) {
                for (r in 0 until h) {
                    p[(r + 1) * (w + 1) + c] += p[r * (w + 1) + c]
                }
            }
            return p
        }

        private fun rangeSum(p: IntArray, r0: Int, c0: Int, r1: Int, c1: Int): Int {
            val w = Board.WIDTH + 1
            return p[r1 * w + c1] - p[r0 * w + c1] - p[r1 * w + c0] + p[r0 * w + c0]
        }
    }
}

/** 求解器内部使用：已放置物品（含旋转后的有效尺寸）。 */
data class SolverPlacedItem(
    val item: Item,
    val coord: Coord,
    val rotated: Boolean,
) {
    val effectiveHeight: Int get() = if (rotated) item.width else item.height
    val effectiveWidth: Int get() = if (rotated) item.height else item.width

    companion object {
        fun fromPlaced(p: PlacedItem) = SolverPlacedItem(p.item, p.coord, p.rotated)
    }
}
