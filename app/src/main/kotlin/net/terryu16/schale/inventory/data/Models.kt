package net.terryu16.schale.inventory.data

/**
 * 棋盘宽 9 列 × 高 5 行 = 45 格。
 * 物品最多 3 种，单个物品最大尺寸 4 × 4。
 */
object Board {
    const val WIDTH = 9
    const val HEIGHT = 5
    const val CELL_COUNT = WIDTH * HEIGHT
    const val ITEM_GROUP_COUNT = 3
    const val MAX_ITEM_SIZE = 4

    fun index(row: Int, col: Int): Int = row * WIDTH + col
    fun rowOf(index: Int): Int = index / WIDTH
    fun colOf(index: Int): Int = index % WIDTH
}

data class Coord(val row: Int, val col: Int)

/**
 * 单个物品定义。height/width 为占用的格子数，itemIndex 0..2 区分三种物品。
 */
data class Item(
    val height: Int,
    val width: Int,
    val itemIndex: Int,
) {
    init {
        require(height in 1..Board.MAX_ITEM_SIZE)
        require(width in 1..Board.MAX_ITEM_SIZE)
        require(itemIndex in 0 until Board.ITEM_GROUP_COUNT)
    }

    val isSquare: Boolean get() = height == width
    fun rotated(): Item? = if (isSquare) null else copy(height = width, width = height)
}

/** 同类物品集合：一种形状 + 数量。 */
data class ItemGroup(
    val item: Item,
    val count: Int,
)

/** 已确定位置的物品。coord 是物品左上角坐标。rotated 时按旋转后的形状占用格子。 */
data class PlacedItem(
    val id: String,
    val item: Item,
    val coord: Coord,
    val rotated: Boolean,
) {
    val effectiveHeight: Int get() = if (rotated) item.width else item.height
    val effectiveWidth: Int get() = if (rotated) item.height else item.width

    fun covers(row: Int, col: Int): Boolean =
        row in coord.row until coord.row + effectiveHeight &&
            col in coord.col until coord.col + effectiveWidth
}

/** 关卡预设：3 种物品的初始形状与数量。 */
data class Preset(
    val key: String,
    val titleResId: Int,
    val items: List<ItemGroup>,
)
