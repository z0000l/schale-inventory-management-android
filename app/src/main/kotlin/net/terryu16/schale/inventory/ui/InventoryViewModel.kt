package net.terryu16.schale.inventory.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.terryu16.schale.inventory.data.Board
import net.terryu16.schale.inventory.data.Coord
import net.terryu16.schale.inventory.data.Item
import net.terryu16.schale.inventory.data.ItemGroup
import net.terryu16.schale.inventory.data.PersistedState
import net.terryu16.schale.inventory.data.PlacedItem
import net.terryu16.schale.inventory.data.Preset
import net.terryu16.schale.inventory.data.Presets
import net.terryu16.schale.inventory.data.StatePersistence
import net.terryu16.schale.inventory.solver.Solver
import net.terryu16.schale.inventory.solver.SolverPlacedItem
import net.terryu16.schale.inventory.solver.SolverState
import java.util.UUID
import kotlin.math.round

/**
 * 棋盘/物品/概率的中央状态。所有 UI 操作通过这个 VM 触发。
 *
 * 关键状态：
 * - itemGroups：3 种物品的形状 + 库存数量
 * - placedItems：当前已经标定位置的物品
 * - openMap：哪些格子已被翻开（45 个布尔）
 * - showProbs：固定 true,true,true（不再暴露 toggle，热力图始终叠加 3 物品）
 * - probs：[8][45]，每个 itemFlag (0..7) 对应一张概率图
 * - isMaxProbs：[8][45]，对应概率图上达到最大值的格子（用于"推荐翻开"高亮）
 * - dirty：自上次计算以来输入有无变化
 * - running：计算中
 * - error：上次计算的错误（如"无可行配置"）
 *
 * 持久化：物品配置 / 已放置 / 翻牌图通过 StatePersistence 持久化到 SharedPreferences，
 * 重启 App 后自动恢复。概率结果不存，恢复后 dirty=true，提示用户重新计算。
 */
class InventoryViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var solveJob: Job? = null

    private fun initialState(): UiState {
        val persisted = StatePersistence.load(getApplication())
        if (persisted != null) {
            return UiState(
                presetKey = persisted.presetKey,
                itemGroups = persisted.itemGroups,
                placedItems = persisted.placedItems,
                openMap = persisted.openMap,
                showProbs = booleanArrayOf(true, true, true),
                probs = null,
                isMaxProbs = null,
                dirty = true,
                running = false,
                error = null,
            )
        }
        val preset = Presets.default
        return UiState(
            presetKey = preset.key,
            itemGroups = preset.items,
            placedItems = emptyList(),
            openMap = BooleanArray(Board.CELL_COUNT),
            showProbs = booleanArrayOf(true, true, true),
            probs = null,
            isMaxProbs = null,
            dirty = false,
            running = false,
            error = null,
        )
    }

    private fun persist(state: UiState) {
        viewModelScope.launch(Dispatchers.IO) {
            StatePersistence.save(
                getApplication(),
                PersistedState(
                    presetKey = state.presetKey,
                    itemGroups = state.itemGroups,
                    placedItems = state.placedItems,
                    openMap = state.openMap,
                )
            )
        }
    }

    fun applyPreset(preset: Preset) {
        solveJob?.cancel()
        val newState = UiState(
            presetKey = preset.key,
            itemGroups = preset.items,
            placedItems = emptyList(),
            openMap = BooleanArray(Board.CELL_COUNT),
            showProbs = booleanArrayOf(true, true, true),
            probs = null,
            isMaxProbs = null,
            dirty = false,
            running = false,
            error = null,
        )
        _uiState.value = newState
        persist(newState)
    }

    fun resetBoard() {
        solveJob?.cancel()
        var snapshot: UiState? = null
        _uiState.update {
            val newState = it.copy(
                placedItems = emptyList(),
                openMap = BooleanArray(Board.CELL_COUNT),
                probs = null,
                isMaxProbs = null,
                dirty = false,
                running = false,
                error = null,
            )
            snapshot = newState
            newState
        }
        snapshot?.let { persist(it) }
    }

    fun toggleCell(row: Int, col: Int) {
        if (row !in 0 until Board.HEIGHT || col !in 0 until Board.WIDTH) return
        // 已放置物品覆盖的格子不允许 toggle —— 若误点会把 openMap 翻成 false，
        // 但渲染又因 `occupied` 跳过该格，用户察觉不到 openMap 已和 placedItems 失同步；
        // 这种"隐形损坏"过去会导致 buildIsMaxFlags 把 max 拉到 1.0 而看不到任何 glow。
        if (_uiState.value.placedItems.any { it.covers(row, col) }) return
        var snapshot: UiState? = null
        _uiState.update {
            val newOpen = it.openMap.copyOf()
            val idx = Board.index(row, col)
            newOpen[idx] = !newOpen[idx]
            val newState = it.copy(openMap = newOpen, dirty = true)
            snapshot = newState
            newState
        }
        snapshot?.let { persist(it) }
    }

    fun modifyItemGroup(itemIndex: Int, newHeight: Int? = null, newWidth: Int? = null, newCount: Int? = null) {
        var snapshot: UiState? = null
        _uiState.update { st ->
            val newGroups = st.itemGroups.toMutableList()
            val cur = newGroups[itemIndex]
            val newItem = cur.item.copy(
                height = newHeight ?: cur.item.height,
                width = newWidth ?: cur.item.width,
            )
            newGroups[itemIndex] = cur.copy(
                item = newItem,
                count = newCount ?: cur.count,
            )

            val newPlaced = st.placedItems.map { p ->
                if (p.item.itemIndex == itemIndex) {
                    val updated = p.copy(item = newItem)
                    val maxRow = Board.HEIGHT - updated.effectiveHeight
                    val maxCol = Board.WIDTH - updated.effectiveWidth
                    updated.copy(
                        coord = Coord(
                            updated.coord.row.coerceIn(0, maxOf(0, maxRow)),
                            updated.coord.col.coerceIn(0, maxOf(0, maxCol)),
                        )
                    )
                } else p
            }.let { list ->
                val counts = IntArray(Board.ITEM_GROUP_COUNT)
                list.filter { p ->
                    val limit = newGroups[p.item.itemIndex].count
                    val ok = counts[p.item.itemIndex] < limit
                    if (ok) counts[p.item.itemIndex] += 1
                    ok
                }
            }
            val newState = st.copy(itemGroups = newGroups, placedItems = newPlaced, dirty = true)
            snapshot = newState
            newState
        }
        snapshot?.let { persist(it) }
    }

    fun addPlacedItem(itemIndex: Int, row: Int, col: Int, rotated: Boolean) {
        var snapshot: UiState? = null
        _uiState.update { st ->
            val group = st.itemGroups[itemIndex]
            val placedCount = st.placedItems.count { it.item.itemIndex == itemIndex }
            if (placedCount >= group.count) return@update st
            val item = group.item
            val eh = if (rotated) item.width else item.height
            val ew = if (rotated) item.height else item.width
            if (row + eh > Board.HEIGHT || col + ew > Board.WIDTH) return@update st

            for (p in st.placedItems) {
                for (r in row until row + eh) for (c in col until col + ew) {
                    if (p.covers(r, c)) return@update st
                }
            }

            val newItem = PlacedItem(
                id = UUID.randomUUID().toString(),
                item = item,
                coord = Coord(row, col),
                rotated = rotated,
            )

            val newOpen = st.openMap.copyOf()
            for (r in row until row + eh) for (c in col until col + ew) {
                newOpen[Board.index(r, c)] = true
            }

            val newState = st.copy(
                placedItems = st.placedItems + newItem,
                openMap = newOpen,
                dirty = true,
            )
            snapshot = newState
            newState
        }
        snapshot?.let { persist(it) }
    }

    fun removePlacedItem(id: String) {
        var snapshot: UiState? = null
        _uiState.update {
            val newState = it.copy(
                placedItems = it.placedItems.filter { p -> p.id != id },
                dirty = true,
            )
            snapshot = newState
            newState
        }
        snapshot?.let { persist(it) }
    }

    fun calculate() {
        if (_uiState.value.running) return
        solveJob?.cancel()
        _uiState.update { it.copy(running = true, error = null) }
        val snapshot = _uiState.value
        solveJob = viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val solver = Solver(
                        SolverState(
                            openMap = snapshot.openMap.copyOf(),
                            remainingItems = snapshot.itemGroups.mapIndexed { i, g ->
                                val placedHere = snapshot.placedItems.count { it.item.itemIndex == i }
                                ItemGroup(g.item, (g.count - placedHere).coerceAtLeast(0))
                            },
                            placedItems = snapshot.placedItems.map { SolverPlacedItem.fromPlaced(it) },
                        )
                    )
                    solver.solve().getOrThrow()
                }
            }
            result.fold(
                onSuccess = { probs ->
                    val isMax = buildIsMaxFlags(probs, snapshot.openMap, snapshot.placedItems)
                    _uiState.update {
                        it.copy(
                            probs = probs,
                            isMaxProbs = isMax,
                            running = false,
                            dirty = false,
                            error = null,
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(running = false, error = err.message ?: "计算失败")
                    }
                }
            )
        }
    }

    /**
     * 计算"推荐翻开"高亮格子。
     *
     * 关键点：必须同时排除 openMap=true 和"已被 placedItems 覆盖"两个集合。
     *
     * 原因：calcProbabilities 会给已放置物品的格子打 prob=1.0（任何 flag 都包括），
     * 如果只看 openMap，一旦 openMap 与 placedItems 失同步（比如手抖戳到已放置物品上
     * 让 openMap 翻成 false，或 modifyItemGroup 把物品 shape 改大但没更新 openMap），
     * 这些格子会被错误地纳入 max 比较 → max 被拉到 1.0 → 真正未翻开的格子的概率
     * （如 0.67）打不平 max → BoardCanvas 又因 `if (occupied) continue` 跳过这些
     * 已覆盖格不画 glow → 用户看到的就是"完全没有任何高亮"。
     */
    private fun buildIsMaxFlags(
        probs: List<DoubleArray>,
        openMap: BooleanArray,
        placedItems: List<PlacedItem>,
    ): List<BooleanArray> {
        val covered = BooleanArray(Board.CELL_COUNT)
        for (p in placedItems) {
            val r0 = p.coord.row
            val c0 = p.coord.col
            for (r in r0 until r0 + p.effectiveHeight) {
                for (c in c0 until c0 + p.effectiveWidth) {
                    if (r in 0 until Board.HEIGHT && c in 0 until Board.WIDTH) {
                        covered[Board.index(r, c)] = true
                    }
                }
            }
        }
        return probs.map { prob ->
            val rounded = DoubleArray(prob.size) { round(prob[it] * 1000) / 1000.0 }
            var max = 0.0
            for (i in rounded.indices) {
                if (!openMap[i] && !covered[i] && rounded[i] > max) max = rounded[i]
            }
            BooleanArray(prob.size) { i ->
                max > 0.0 && rounded[i] == max && !openMap[i] && !covered[i]
            }
        }
    }
}

data class UiState(
    val presetKey: String,
    val itemGroups: List<ItemGroup>,
    val placedItems: List<PlacedItem>,
    val openMap: BooleanArray,
    val showProbs: BooleanArray,
    val probs: List<DoubleArray>?,
    val isMaxProbs: List<BooleanArray>?,
    val dirty: Boolean,
    val running: Boolean,
    val error: String?,
) {
    val activeFlag: Int
        get() = (if (showProbs[0]) 1 else 0) or
            (if (showProbs[1]) 2 else 0) or
            (if (showProbs[2]) 4 else 0)

    fun currentProb(): DoubleArray? = probs?.get(activeFlag)
    fun currentIsMax(): BooleanArray? = isMaxProbs?.get(activeFlag)

    /** equals / hashCode 由于含 BooleanArray / DoubleArray，避免默认实现的引用比较。 */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UiState) return false
        if (presetKey != other.presetKey) return false
        if (itemGroups != other.itemGroups) return false
        if (placedItems != other.placedItems) return false
        if (!openMap.contentEquals(other.openMap)) return false
        if (!showProbs.contentEquals(other.showProbs)) return false
        if (dirty != other.dirty) return false
        if (running != other.running) return false
        if (error != other.error) return false
        if ((probs == null) != (other.probs == null)) return false
        if (probs != null && other.probs != null) {
            if (probs.size != other.probs.size) return false
            for (i in probs.indices) if (!probs[i].contentEquals(other.probs[i])) return false
        }
        if ((isMaxProbs == null) != (other.isMaxProbs == null)) return false
        if (isMaxProbs != null && other.isMaxProbs != null) {
            if (isMaxProbs.size != other.isMaxProbs.size) return false
            for (i in isMaxProbs.indices) if (!isMaxProbs[i].contentEquals(other.isMaxProbs[i])) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var h = presetKey.hashCode()
        h = 31 * h + itemGroups.hashCode()
        h = 31 * h + placedItems.hashCode()
        h = 31 * h + openMap.contentHashCode()
        h = 31 * h + showProbs.contentHashCode()
        h = 31 * h + dirty.hashCode()
        h = 31 * h + running.hashCode()
        h = 31 * h + (error?.hashCode() ?: 0)
        h = 31 * h + (probs?.sumOf { it.contentHashCode() } ?: 0)
        h = 31 * h + (isMaxProbs?.sumOf { it.contentHashCode() } ?: 0)
        return h
    }
}
