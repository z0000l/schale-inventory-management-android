package net.terryu16.schale.inventory.ui.panel

import net.terryu16.schale.inventory.data.Board
import net.terryu16.schale.inventory.data.Item
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceOptionsTest {

    @Test
    fun rectangleOffersVerticalThenHorizontal() {
        for (h in 1..Board.MAX_ITEM_SIZE) for (w in 1..Board.MAX_ITEM_SIZE) {
            if (h == w) continue
            val options = placeOptions(Item(h, w, 0))
            assertEquals("高$h 宽$w", listOf("竖", "横"), options.map { it.first })
            // 文字必须与放置后的实际形状一致：高>宽 为竖
            for ((label, rotated) in options) {
                val eh = if (rotated) w else h
                val ew = if (rotated) h else w
                assertEquals("高$h 宽$w 旋转=$rotated", if (eh > ew) "竖" else "横", label)
            }
        }
    }

    @Test
    fun squareOffersSinglePlaceButton() {
        for (s in 1..Board.MAX_ITEM_SIZE) {
            assertEquals(listOf("放置" to false), placeOptions(Item(s, s, 0)))
        }
    }
}
