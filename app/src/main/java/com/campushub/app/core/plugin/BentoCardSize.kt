package com.campushub.app.core.plugin

/**
 * 便当盒卡片档位（两列网格）。
 *
 * 成员名保持不变：旧的布局持久化字符串（`bento_layout_v1` 里的 `size` 字段）
 * 直接按枚举名解析，迁移时无需额外映射表。
 */
enum class BentoCardSize(val colSpan: Int, val rowSpan: Int) {
    COMPACT_1X1(1, 1),
    EXPANDED_2X1(2, 1),
    TALL_1X2(1, 2),
    HERO_2X2(2, 2);

    /**
     * 内容容量档：1×1 < 2×1 < 1×2 < 2×2。
     * 用于判断某个槽位在该档位是否放得下（见 `GlanceLayoutRules`）。
     */
    val capacity: Int
        get() = when (this) {
            COMPACT_1X1 -> 0
            EXPANDED_2X1 -> 1
            TALL_1X2 -> 2
            HERO_2X2 -> 3
        }

    companion object {
        fun of(colSpan: Int, rowSpan: Int): BentoCardSize = when {
            colSpan >= 2 && rowSpan >= 2 -> HERO_2X2
            colSpan >= 2 -> EXPANDED_2X1
            rowSpan >= 2 -> TALL_1X2
            else -> COMPACT_1X1
        }

        /** 按持久化的枚举名解析，无法识别时回退 [COMPACT_1X1]。 */
        fun fromNameOrNull(name: String?): BentoCardSize? =
            entries.firstOrNull { it.name == name }
    }
}
