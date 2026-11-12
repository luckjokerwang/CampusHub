package com.campushub.app.core.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 卡片槽位可见性规则：四档 × 大字号。
 *
 * 规则来源：`design/docs/handoff.md` §3.1（内容预算）与 `impl-plan.md` §6.5（大字号降级）。
 * 核心约束：宁可少显示一层信息，也不要裁字。
 */
class GlanceLayoutRulesTest {

    @Test
    fun `1x1 shows the sub line and action button`() {
        val slots = GlanceLayoutRules.visibleSlots(BentoCardSize.COMPACT_1X1, largeFont = false)

        assertTrue(GlanceSlot.SUB in slots)
        assertTrue(GlanceSlot.ACTION in slots)
        assertFalse(GlanceSlot.CHIP in slots)
        assertFalse(GlanceSlot.SERVICE_LABEL in slots)
        assertFalse(GlanceSlot.DETAILS in slots)
    }

    @Test
    fun `2x1 shows the action button and status chip`() {
        val slots = GlanceLayoutRules.visibleSlots(BentoCardSize.EXPANDED_2X1, largeFont = false)

        assertTrue(GlanceSlot.ACTION in slots)
        assertTrue(GlanceSlot.CHIP in slots)
        assertTrue(GlanceSlot.SERVICE_LABEL in slots)
        assertTrue(GlanceSlot.DETAILS in slots)
    }

    @Test
    fun `tall and hero tiers expose the same slots as 2x1`() {
        val wide = GlanceLayoutRules.visibleSlots(BentoCardSize.EXPANDED_2X1, largeFont = false)

        assertEquals(wide, GlanceLayoutRules.visibleSlots(BentoCardSize.TALL_1X2, largeFont = false))
        assertEquals(wide, GlanceLayoutRules.visibleSlots(BentoCardSize.HERO_2X2, largeFont = false))
    }

    @Test
    fun `large font drops sub and service label at every tier`() {
        for (size in BentoCardSize.entries) {
            val slots = GlanceLayoutRules.visibleSlots(size, largeFont = true)
            assertFalse("$size 大字号不应显示副说明", GlanceSlot.SUB in slots)
            assertFalse("$size 大字号不应显示服务标签", GlanceSlot.SERVICE_LABEL in slots)
        }
    }

    @Test
    fun `large font keeps the action button so the card stays operable`() {
        val slots = GlanceLayoutRules.visibleSlots(BentoCardSize.EXPANDED_2X1, largeFont = true)

        assertTrue(GlanceSlot.ACTION in slots)
    }

    @Test
    fun `detail budget grows with the tier`() {
        assertEquals(0, GlanceLayoutRules.maxDetails(BentoCardSize.COMPACT_1X1))
        assertEquals(1, GlanceLayoutRules.maxDetails(BentoCardSize.EXPANDED_2X1))
        assertEquals(3, GlanceLayoutRules.maxDetails(BentoCardSize.TALL_1X2))
        assertEquals(6, GlanceLayoutRules.maxDetails(BentoCardSize.HERO_2X2))
    }

    @Test
    fun `capacity ordering matches the tier ladder`() {
        assertTrue(BentoCardSize.COMPACT_1X1.capacity < BentoCardSize.EXPANDED_2X1.capacity)
        assertTrue(BentoCardSize.EXPANDED_2X1.capacity < BentoCardSize.TALL_1X2.capacity)
        assertTrue(BentoCardSize.TALL_1X2.capacity < BentoCardSize.HERO_2X2.capacity)
    }
}
