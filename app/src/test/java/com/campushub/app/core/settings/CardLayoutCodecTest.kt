package com.campushub.app.core.settings

import com.campushub.app.core.plugin.BentoCardSize
import com.campushub.app.core.plugin.CardSpec
import com.campushub.app.core.plugin.PluginAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 便当盒布局编解码与旧版迁移。 */
class CardLayoutCodecTest {

    private fun spec(
        id: String,
        defaultSize: BentoCardSize = BentoCardSize.COMPACT_1X1,
        supported: Set<BentoCardSize> = BentoCardSize.entries.toSet(),
        defaultVisible: Boolean = true
    ) = CardSpec(
        id = id,
        title = id,
        iconGlyph = "字",
        accent = PluginAccent.NEUTRAL,
        defaultSize = defaultSize,
        supportedSizes = supported,
        defaultVisible = defaultVisible
    )

    private val specs = listOf(
        spec("water", BentoCardSize.EXPANDED_2X1),
        spec("billing", BentoCardSize.EXPANDED_2X1),
        spec("narrow_card", supported = setOf(BentoCardSize.COMPACT_1X1, BentoCardSize.EXPANDED_2X1))
    )

    @Test
    fun `encode 与 decode 往返保持顺序与可见性`() {
        val entries = listOf(
            CardLayoutEntry("billing", BentoCardSize.TALL_1X2, visible = false),
            CardLayoutEntry("water", BentoCardSize.HERO_2X2, visible = true)
        )

        val decoded = CardLayoutCodec.decode(CardLayoutCodec.encode(entries))

        assertEquals(entries, decoded)
    }

    @Test
    fun `decode 对垃圾输入返回 null`() {
        assertNull(CardLayoutCodec.decode("garbage"))
        assertNull(CardLayoutCodec.decode(""))
        assertNull(CardLayoutCodec.decode(null))
        assertNull(CardLayoutCodec.decode("[]"))
    }

    @Test
    fun `旧版布局按数组顺序解析且默认可见`() {
        val legacy = """[{"id":"water","size":"HERO_2X2","order":0},{"id":"legacy_card","size":"EXPANDED_2X1","order":1}]"""

        val entries = CardLayoutCodec.fromLegacyV1(legacy)!!

        assertEquals(listOf("water", "legacy_card"), entries.map { it.id })
        assertEquals(BentoCardSize.HERO_2X2, entries.first().size)
        assertTrue(entries.all { it.visible })
    }

    @Test
    fun `resolve 丢弃已无对应卡片的旧条目并追加新卡`() {
        val saved = listOf(
            CardLayoutEntry("water", BentoCardSize.EXPANDED_2X1, visible = true),
            CardLayoutEntry("legacy_card", BentoCardSize.EXPANDED_2X1, visible = true),
            CardLayoutEntry("old_card", BentoCardSize.COMPACT_1X1, visible = true)
        )

        val resolved = CardLayoutCodec.resolve(saved, specs)

        assertEquals(listOf("water", "billing", "narrow_card"), resolved.map { it.id })
    }

    @Test
    fun `resolve 把不支持的尺寸纠正为默认档位`() {
        // 非首位卡：narrow_card 不支持 2×2，回落到默认 1×1（首位会被升到 2×1，这里不测那条规则）
        val saved = listOf(
            CardLayoutEntry("water", BentoCardSize.EXPANDED_2X1, visible = true),
            CardLayoutEntry("narrow_card", BentoCardSize.HERO_2X2, visible = true)
        )

        val resolved = CardLayoutCodec.resolve(saved, specs)

        val narrow = resolved.first { it.id == "narrow_card" }
        assertEquals(BentoCardSize.COMPACT_1X1, narrow.size)
    }

    @Test
    fun `resolve 保留隐藏状态`() {
        val saved = listOf(
            CardLayoutEntry("billing", BentoCardSize.COMPACT_1X1, visible = false)
        )

        val resolved = CardLayoutCodec.resolve(saved, specs)

        assertFalse(resolved.first { it.id == "billing" }.visible)
    }

    @Test
    fun `首位卡是 1x1 时自动升为 2x1`() {
        val saved = listOf(
            CardLayoutEntry("narrow_card", BentoCardSize.COMPACT_1X1, visible = true),
            CardLayoutEntry("water", BentoCardSize.EXPANDED_2X1, visible = true)
        )

        val resolved = CardLayoutCodec.resolve(saved, specs)

        assertEquals("narrow_card", resolved.first().id)
        assertEquals(BentoCardSize.EXPANDED_2X1, resolved.first().size)
    }

    @Test
    fun `normalizeFirstCard 对不支持 2x1 的首位卡保持原样`() {
        val narrow = spec("narrow", supported = setOf(BentoCardSize.COMPACT_1X1))
        val entries = listOf(CardLayoutEntry("narrow", BentoCardSize.COMPACT_1X1, visible = true))

        val normalized = CardLayoutCodec.normalizeFirstCard(entries, listOf(narrow))

        assertEquals(BentoCardSize.COMPACT_1X1, normalized.first().size)
    }

    @Test
    fun `normalizeFirstCard 不改变非首位卡`() {
        val entries = listOf(
            CardLayoutEntry("water", BentoCardSize.EXPANDED_2X1, visible = true),
            CardLayoutEntry("narrow_card", BentoCardSize.COMPACT_1X1, visible = true)
        )

        assertEquals(entries, CardLayoutCodec.normalizeFirstCard(entries, specs))
    }

    @Test
    fun `resolve 在无存档时使用 manifest 默认值`() {
        val resolved = CardLayoutCodec.resolve(null, specs)

        assertEquals(specs.map { it.id }, resolved.map { it.id })
        assertEquals(BentoCardSize.EXPANDED_2X1, resolved.first().size)
        assertTrue(resolved.all { it.visible })
    }

    @Test
    fun `hideCards 把目标插件的条目全部置为不可见`() {
        val entries = listOf(
            CardLayoutEntry("ilife", BentoCardSize.EXPANDED_2X1, visible = true),
            CardLayoutEntry("score", BentoCardSize.EXPANDED_2X1, visible = true),
            CardLayoutEntry("lexiaotong", BentoCardSize.COMPACT_1X1, visible = true),
            CardLayoutEntry("wallet", BentoCardSize.COMPACT_1X1, visible = false)
        )

        val hidden = CardLayoutCodec.hideCards(entries, setOf("ilife", "score", "wallet"))

        assertTrue(hidden.filter { it.id in setOf("ilife", "score", "wallet") }.none { it.visible })
        assertTrue(hidden.first { it.id == "lexiaotong" }.visible)
        assertEquals(entries.map { it.id }, hidden.map { it.id })
    }

    @Test
    fun `hideCards 后 resolve 不会把卡片补回可见`() {
        // 关键回归：删除条目会被 resolve 按默认值补回来，所以必须用隐藏而不是删除。
        val specsWithTwo = listOf(spec("a", BentoCardSize.EXPANDED_2X1), spec("b"))
        val entries = listOf(
            CardLayoutEntry("a", BentoCardSize.EXPANDED_2X1, visible = true),
            CardLayoutEntry("b", BentoCardSize.COMPACT_1X1, visible = true)
        )

        val hidden = CardLayoutCodec.hideCards(entries, setOf("b"))
        val resolved = CardLayoutCodec.resolve(hidden, specsWithTwo)

        assertFalse(resolved.first { it.id == "b" }.visible)
        assertEquals(setOf("a"), resolved.filter { it.visible }.map { it.id }.toSet())
    }

    @Test
    fun `hideCards 空集合不改变布局`() {
        val entries = listOf(
            CardLayoutEntry("ilife", BentoCardSize.EXPANDED_2X1, visible = true),
            CardLayoutEntry("wallet", BentoCardSize.COMPACT_1X1, visible = false)
        )

        assertEquals(entries, CardLayoutCodec.hideCards(entries, emptySet()))
    }
}
