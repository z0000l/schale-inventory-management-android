package net.terryu16.schale.inventory.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * SharedPreferences + 内嵌 JSON 持久化棋盘配置。
 *
 * 仅持久化"用户输入"层 —— 物品配置 + 已放置 + 翻牌图。
 * 概率结果不存（恢复后让用户重新计算）。
 */
private const val PREFS_NAME = "schale_inventory_state"
private const val KEY_STATE = "state_v1"

data class PersistedState(
    val presetKey: String,
    val itemGroups: List<ItemGroup>,
    val placedItems: List<PlacedItem>,
    val openMap: BooleanArray,
)

object StatePersistence {

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(context: Context, state: PersistedState) {
        val json = JSONObject().apply {
            put("presetKey", state.presetKey)
            put("itemGroups", JSONArray().apply {
                state.itemGroups.forEach { g ->
                    put(JSONObject().apply {
                        put("height", g.item.height)
                        put("width", g.item.width)
                        put("itemIndex", g.item.itemIndex)
                        put("count", g.count)
                    })
                }
            })
            put("placedItems", JSONArray().apply {
                state.placedItems.forEach { p ->
                    put(JSONObject().apply {
                        put("id", p.id)
                        put("height", p.item.height)
                        put("width", p.item.width)
                        put("itemIndex", p.item.itemIndex)
                        put("row", p.coord.row)
                        put("col", p.coord.col)
                        put("rotated", p.rotated)
                    })
                }
            })
            put("openMap", state.openMap.joinToString("") { if (it) "1" else "0" })
        }
        prefs(context).edit().putString(KEY_STATE, json.toString()).apply()
    }

    fun load(context: Context): PersistedState? {
        val raw = prefs(context).getString(KEY_STATE, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val presetKey = json.getString("presetKey")

            val groupsArr = json.getJSONArray("itemGroups")
            require(groupsArr.length() == Board.ITEM_GROUP_COUNT)
            val itemGroups = (0 until groupsArr.length()).map { i ->
                val o = groupsArr.getJSONObject(i)
                ItemGroup(
                    item = Item(o.getInt("height"), o.getInt("width"), o.getInt("itemIndex")),
                    count = o.getInt("count"),
                )
            }

            val placedArr = json.getJSONArray("placedItems")
            val placedItems = (0 until placedArr.length()).map { i ->
                val o = placedArr.getJSONObject(i)
                PlacedItem(
                    id = o.getString("id"),
                    item = Item(o.getInt("height"), o.getInt("width"), o.getInt("itemIndex")),
                    coord = Coord(o.getInt("row"), o.getInt("col")),
                    rotated = o.getBoolean("rotated"),
                )
            }

            val openStr = json.getString("openMap")
            val openMap = BooleanArray(Board.CELL_COUNT) { i ->
                i < openStr.length && openStr[i] == '1'
            }

            PersistedState(presetKey, itemGroups, placedItems, openMap)
        }.getOrNull()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_STATE).apply()
    }
}
