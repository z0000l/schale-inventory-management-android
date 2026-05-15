package net.terryu16.schale.inventory.data

import net.terryu16.schale.inventory.R

/**
 * 五尘来降活动的 7 个关卡物品配置，与原 Web 版完全一致。
 *   龙须糖   3×2
 *   老豆糕   3×1
 *   月饼     2×1
 *   麻花     4×2
 *   杏仁豆腐 2×2
 *   班戟     3×3
 *   糖葫芦   1×4
 */
object Presets {
    private fun item(h: Int, w: Int, idx: Int) = Item(h, w, idx)

    val all: List<Preset> = listOf(
        Preset(
            "1", R.string.preset_1, listOf(
                ItemGroup(item(2, 3, 0), 1),  // 龙须糖
                ItemGroup(item(1, 3, 1), 5),  // 老豆糕
                ItemGroup(item(1, 2, 2), 2),  // 月饼
            )
        ),
        Preset(
            "2", R.string.preset_2, listOf(
                ItemGroup(item(2, 4, 0), 1),  // 麻花
                ItemGroup(item(2, 2, 1), 2),  // 杏仁豆腐
                ItemGroup(item(1, 3, 2), 3),  // 老豆糕
            )
        ),
        Preset(
            "3", R.string.preset_3, listOf(
                ItemGroup(item(3, 3, 0), 1),  // 班戟
                ItemGroup(item(4, 1, 1), 3),  // 糖葫芦
                ItemGroup(item(1, 2, 2), 2),  // 月饼
            )
        ),
        Preset(
            "4", R.string.preset_4, listOf(
                ItemGroup(item(2, 3, 0), 1),
                ItemGroup(item(1, 3, 1), 5),
                ItemGroup(item(1, 2, 2), 2),
            )
        ),
        Preset(
            "5", R.string.preset_5, listOf(
                ItemGroup(item(2, 4, 0), 1),
                ItemGroup(item(2, 2, 1), 2),
                ItemGroup(item(1, 3, 2), 3),
            )
        ),
        Preset(
            "6", R.string.preset_6, listOf(
                ItemGroup(item(3, 3, 0), 1),
                ItemGroup(item(4, 1, 1), 3),
                ItemGroup(item(1, 2, 2), 2),
            )
        ),
        Preset(
            "7", R.string.preset_7, listOf(
                ItemGroup(item(2, 2, 0), 2),  // 杏仁豆腐
                ItemGroup(item(1, 3, 1), 3),  // 老豆糕
                ItemGroup(item(1, 2, 2), 6),  // 月饼
            )
        ),
    )

    val default: Preset get() = all.first()
}
