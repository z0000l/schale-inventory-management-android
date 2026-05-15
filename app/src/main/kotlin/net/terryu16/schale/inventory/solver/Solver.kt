package net.terryu16.schale.inventory.solver

import net.terryu16.schale.inventory.data.Board
import net.terryu16.schale.inventory.data.Coord
import net.terryu16.schale.inventory.data.Item
import kotlin.random.Random

/**
 * 概率求解器：Kotlin 移植自 terry-u16/wx257osn2 的 Rust WASM 版本。
 *
 * 算法：
 *   10 维 DP 统计「将 3 种物品 (cnt0, cnt1, cnt2) 完整放进剩余棋盘」的配置总数。
 *   - 当总数 ≤ sampleCount 时，回溯枚举全部配置；
 *   - 当总数 > sampleCount 时，按场景数权重在 DP 表上做加权随机回溯采样。
 *
 *   再统计每个格子被 (物品1, 物品2, 物品3) 任意子集覆盖的频次，
 *   除以总样本数即得 8 种「物品组合显示开关」对应的概率图。
 *
 * 状态空间：dp[col][row][cnt0][cnt1][cnt2][w0][w1][w2][w3][w4]
 *   - row, col：当前扫描到的格子位置（按列优先扫描）
 *   - cnti：已经放下的第 i 种物品数
 *   - wi：第 i 行从当前列往后还被先前放下的物品堵住几列
 *
 * 为了适应 Kotlin/JVM，dp 表使用 LongArray 平铺；
 * from 表用 IntArray（紧凑结构编码 History）平铺。
 */
class Solver(private val state: SolverState, private val sampleCount: Int = 100_000) {

    private val cnt0Max: Int
    private val cnt1Max: Int
    private val cnt2Max: Int
    private val cntStride0: Int
    private val cntStride1: Int
    private val cntStride2: Int
    private val maxItemSize: Int
    private val wStride: Int   // 单维 w 的状态数 = maxItemSize
    private val w5: Int        // wStride^5
    private val rowDim: Int = Board.HEIGHT + 1
    private val colDim: Int = Board.WIDTH + 1
    private val totalStates: Int

    private val dp: LongArray
    /** from 表：每个状态用 List<History> 记录前驱。仅在被访问的状态上分配，节省内存。 */
    private val from: Array<MutableList<History>?>

    init {
        cnt0Max = state.remainingItems[0].count
        cnt1Max = state.remainingItems[1].count
        cnt2Max = state.remainingItems[2].count
        cntStride0 = cnt0Max + 1
        cntStride1 = cnt1Max + 1
        cntStride2 = cnt2Max + 1

        val rawMaxSize = state.remainingItems
            .filter { it.count > 0 }
            .maxOfOrNull { maxOf(it.item.height, it.item.width) } ?: 1
        maxItemSize = maxOf(rawMaxSize, 1)
        wStride = maxItemSize
        w5 = wStride * wStride * wStride * wStride * wStride

        totalStates = colDim * rowDim * cntStride0 * cntStride1 * cntStride2 * w5

        dp = LongArray(totalStates)
        @Suppress("UNCHECKED_CAST")
        from = arrayOfNulls<MutableList<History>>(totalStates) as Array<MutableList<History>?>
    }

    /** 状态编码：高维到低维顺序为 col, row, cnt0, cnt1, cnt2, w0, w1, w2, w3, w4。 */
    private fun encode(
        col: Int, row: Int,
        c0: Int, c1: Int, c2: Int,
        w0: Int, w1: Int, w2: Int, w3: Int, w4: Int,
    ): Int {
        var idx = col
        idx = idx * rowDim + row
        idx = idx * cntStride0 + c0
        idx = idx * cntStride1 + c1
        idx = idx * cntStride2 + c2
        idx = idx * wStride + w0
        idx = idx * wStride + w1
        idx = idx * wStride + w2
        idx = idx * wStride + w3
        idx = idx * wStride + w4
        return idx
    }

    /** History：用 IntArray 紧凑表示（13 个 short 字段即可装下，但用 Int 更省心）。 */
    private data class History(
        val row: Int,
        val col: Int,
        val c0: Int, val c1: Int, val c2: Int,
        val w0: Int, val w1: Int, val w2: Int, val w3: Int, val w4: Int,
        // itemKind: 0..2 表示放物品，-1 表示空步进
        val itemKind: Int,
        val rotated: Boolean,
    )

    /** 主入口：返回 8 个长度 45 的概率图。下标 flag 是位掩码，bit i 表示是否计入物品 i。 */
    fun solve(): Result<List<DoubleArray>> {
        val (allCount, placements) = samplePlacements()
        if (allCount == 0L) {
            return Result.failure(IllegalStateException("条件を満たす配置が存在しません"))
        }
        val probs = (0 until (1 shl Board.ITEM_GROUP_COUNT)).map { flag ->
            calcProbabilities(flag, placements)
        }
        return Result.success(probs)
    }

    /** 计算指定物品子集 (flag) 下，每个格子被这些物品覆盖的概率。 */
    private fun calcProbabilities(flag: Int, placements: List<List<Placement>>): DoubleArray {
        val sampled = placements.size
        val counts = LongArray(Board.CELL_COUNT)

        // 已固定放置的物品（PlacedItem）按 flag 计入。
        for (placed in state.placedItems) {
            if (flag and (1 shl placed.item.itemIndex) == 0) continue
            val r0 = placed.coord.row
            val c0 = placed.coord.col
            val r1 = r0 + placed.effectiveHeight
            val c1 = c0 + placed.effectiveWidth
            for (r in r0 until r1) {
                for (c in c0 until c1) {
                    counts[r * Board.WIDTH + c] += sampled.toLong()
                }
            }
        }

        // 采样得到的剩余物品配置。
        for (placement in placements) {
            for (p in placement) {
                if (flag and (1 shl p.itemIndex) == 0) continue
                val baseItem = state.remainingItems[p.itemIndex].item
                val eh = if (p.rotated) baseItem.width else baseItem.height
                val ew = if (p.rotated) baseItem.height else baseItem.width
                val r0 = p.coord.row
                val c0 = p.coord.col
                for (r in r0 until r0 + eh) {
                    for (c in c0 until c0 + ew) {
                        val idx = r * Board.WIDTH + c
                        counts[idx] = counts[idx] + 1L
                    }
                }
            }
        }

        val prob = DoubleArray(Board.CELL_COUNT)
        val invSampled = 1.0 / sampled
        for (i in 0 until Board.CELL_COUNT) {
            prob[i] = counts[i].toDouble() * invSampled
        }
        return prob
    }

    /**
     * 10 维 DP 计算可行配置总数 & 采样配置列表。
     * 返回值 first 是全部配置数（饱和到 Long），second 是采样得到的配置（每个元素是一组 Placement）。
     */
    private fun samplePlacements(): Pair<Long, List<List<Placement>>> {
        dp[encode(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)] = 1L

        val items = state.remainingItems.map { it.item }
        val W = Board.WIDTH
        val H = Board.HEIGHT
        val ITEMS = Board.ITEM_GROUP_COUNT

        // 扫描顺序：列优先，按 col→row→cnt0→cnt1→cnt2→w0..w4 升序。
        for (col in 0 until W) {
            for (row in 0 until H) {
                for (c0 in 0..cnt0Max) for (c1 in 0..cnt1Max) for (c2 in 0..cnt2Max) {
                    for (w0 in 0 until wStride) for (w1 in 0 until wStride) for (w2 in 0 until wStride)
                        for (w3 in 0 until wStride) for (w4 in 0 until wStride) {
                            val cur = dp[encode(col, row, c0, c1, c2, w0, w1, w2, w3, w4)]
                            if (cur == 0L) continue

                            val cnts = intArrayOf(c0, c1, c2)
                            val ws = intArrayOf(w0, w1, w2, w3, w4)

                            // 转移 1：在 (row, col) 放下一个物品。
                            for (i in 0 until ITEMS) {
                                if (cnts[i] + 1 > state.remainingItems[i].count) continue

                                val newCnts = cnts.copyOf().also { it[i] += 1 }
                                tryPlace(items[i], i, false, row, col, cur, newCnts, ws)
                                items[i].rotated()?.let { rotItem ->
                                    tryPlace(rotItem, i, true, row, col, cur, newCnts, ws)
                                }
                            }

                            // 转移 2：不放，扫描指针前进。
                            val newW = ws.copyOf()
                            newW[row] = maxOf(newW[row] - 1, 0)
                            if (row + 1 < H) {
                                val nIdx = encode(col, row + 1, c0, c1, c2,
                                    newW[0], newW[1], newW[2], newW[3], newW[4])
                                dp[nIdx] += cur
                                addHistory(nIdx, row, col, c0, c1, c2, ws, -1, false)
                            } else if (col + 1 <= W) {
                                val nIdx = encode(col + 1, 0, c0, c1, c2,
                                    newW[0], newW[1], newW[2], newW[3], newW[4])
                                dp[nIdx] += cur
                                addHistory(nIdx, row, col, c0, c1, c2, ws, -1, false)
                            }
                        }
                }
            }
        }

        val finalIdx = encode(W, 0, cnt0Max, cnt1Max, cnt2Max, 0, 0, 0, 0, 0)
        val totalCount = dp[finalIdx]

        val samples: List<List<Placement>> = when {
            totalCount == 0L -> emptyList()
            totalCount <= sampleCount.toLong() -> restoreAll()
            else -> restoreRandom()
        }
        return totalCount to samples
    }

    private fun tryPlace(
        item: Item, itemKind: Int, rotated: Boolean,
        row: Int, col: Int, cur: Long, newCnts: IntArray, ws: IntArray,
    ) {
        val br = row + item.height
        val bc = col + item.width
        if (br > Board.HEIGHT || bc > Board.WIDTH) return
        if (state.hasOccupied(row, col, br, bc)) return
        if (state.hasVacant(row, col, br, bc)) return

        val newW = ws.copyOf()
        for (r in row until row + item.height) {
            if (newW[r] != 0) return
            newW[r] = item.width - 1
        }

        val nextRow: Int
        val nextCol: Int
        if (row + item.height == Board.HEIGHT) {
            nextRow = 0; nextCol = col + 1
        } else {
            nextRow = row + item.height; nextCol = col
        }

        val nIdx = encode(nextCol, nextRow, newCnts[0], newCnts[1], newCnts[2],
            newW[0], newW[1], newW[2], newW[3], newW[4])
        dp[nIdx] += cur
        addHistory(nIdx, row, col, newCnts[0] - if (itemKind == 0) 1 else 0,
            newCnts[1] - if (itemKind == 1) 1 else 0,
            newCnts[2] - if (itemKind == 2) 1 else 0,
            ws, itemKind, rotated)
    }

    private fun addHistory(
        targetIdx: Int,
        prevRow: Int, prevCol: Int,
        prevC0: Int, prevC1: Int, prevC2: Int,
        prevW: IntArray,
        itemKind: Int, rotated: Boolean,
    ) {
        var list = from[targetIdx]
        if (list == null) {
            list = ArrayList(1)
            from[targetIdx] = list
        }
        list.add(History(prevRow, prevCol, prevC0, prevC1, prevC2,
            prevW[0], prevW[1], prevW[2], prevW[3], prevW[4], itemKind, rotated))
    }

    /** 配置数 ≤ sampleCount 时枚举所有解。 */
    private fun restoreAll(): List<List<Placement>> {
        val all = mutableListOf<List<Placement>>()
        val cur = mutableListOf<Placement>()
        dfs(Board.WIDTH, 0, cnt0Max, cnt1Max, cnt2Max, intArrayOf(0, 0, 0, 0, 0), cur, all)
        return all
    }

    private fun dfs(
        col: Int, row: Int, c0: Int, c1: Int, c2: Int, w: IntArray,
        current: MutableList<Placement>, all: MutableList<List<Placement>>,
    ) {
        if (col == 0 && row == 0) {
            all.add(current.toList())
            return
        }
        val idx = encode(col, row, c0, c1, c2, w[0], w[1], w[2], w[3], w[4])
        val histories = from[idx] ?: return
        for (h in histories) {
            val pushed = if (h.itemKind >= 0) {
                current.add(Placement(Coord(h.row, h.col), h.itemKind, h.rotated))
                true
            } else false

            dfs(h.col, h.row, h.c0, h.c1, h.c2,
                intArrayOf(h.w0, h.w1, h.w2, h.w3, h.w4),
                current, all)

            if (pushed) current.removeAt(current.size - 1)
        }
    }

    /** 配置数 > sampleCount 时按场景数加权随机回溯采样。 */
    private fun restoreRandom(): List<List<Placement>> {
        val rng = Random(System.nanoTime())
        val all = ArrayList<List<Placement>>(sampleCount)
        repeat(sampleCount) {
            val sample = ArrayList<Placement>()
            var row = 0
            var col = Board.WIDTH
            var c0 = cnt0Max
            var c1 = cnt1Max
            var c2 = cnt2Max
            var w0 = 0; var w1 = 0; var w2 = 0; var w3 = 0; var w4 = 0

            while (row > 0 || col > 0) {
                val idx = encode(col, row, c0, c1, c2, w0, w1, w2, w3, w4)
                val histories = from[idx] ?: break

                val weights = LongArray(histories.size)
                var total = 0L
                for (i in histories.indices) {
                    val h = histories[i]
                    val wIdx = encode(h.col, h.row, h.c0, h.c1, h.c2, h.w0, h.w1, h.w2, h.w3, h.w4)
                    weights[i] = dp[wIdx]
                    total += weights[i]
                }
                if (total <= 0L) break

                val pick = (rng.nextDouble() * total).toLong().coerceIn(0L, total - 1)
                var acc = 0L
                var chosen = 0
                for (i in weights.indices) {
                    acc += weights[i]
                    if (pick < acc) { chosen = i; break }
                }
                val h = histories[chosen]
                if (h.itemKind >= 0) {
                    sample.add(Placement(Coord(h.row, h.col), h.itemKind, h.rotated))
                }
                row = h.row; col = h.col
                c0 = h.c0; c1 = h.c1; c2 = h.c2
                w0 = h.w0; w1 = h.w1; w2 = h.w2; w3 = h.w3; w4 = h.w4
            }
            all.add(sample)
        }
        return all
    }
}

/** 求解输出：一种物品在某坐标的（可能旋转的）放置。 */
data class Placement(
    val coord: Coord,
    val itemIndex: Int,
    val rotated: Boolean,
)
