package com.campushub.app.core.settings

import com.campushub.app.core.plugin.BentoCardSize
import com.campushub.app.core.plugin.CardSpec
import org.json.JSONArray
import org.json.JSONObject

/** 一张卡片在布局里的持久化状态。 */
data class CardLayoutEntry(
    val id: String,
    val size: BentoCardSize,
    val visible: Boolean
)

/**
 * 便当盒布局的编解码（纯函数，可 JVM 单测）。
 *
 * 存储格式 v2：`[{"id":"card_a","size":"EXPANDED_2X1","visible":true}, ...]`
 * 旧格式 v1（`bento_layout_v1`）：`[{"id":...,"size":...,"order":...}]`，没有 visible 字段。
 */
object CardLayoutCodec {

    fun encode(entries: List<CardLayoutEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("size", entry.size.name)
                    put("visible", entry.visible)
                }
            )
        }
        return array.toString()
    }

    /** 解析失败或为空时返回 null（调用方回退到 manifest 默认值）。 */
    fun decode(json: String?): List<CardLayoutEntry>? {
        if (json.isNullOrBlank()) return null
        return try {
            val array = JSONArray(json)
            val result = mutableListOf<CardLayoutEntry>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                val size = BentoCardSize.fromNameOrNull(item.optString("size"))
                    ?: BentoCardSize.COMPACT_1X1
                result.add(
                    CardLayoutEntry(
                        id = id,
                        size = size,
                        visible = item.optBoolean("visible", true)
                    )
                )
            }
            if (result.isEmpty()) null else result
        } catch (e: Exception) {
            null
        }
    }

    /** 解析旧版布局：按数组顺序，默认全部可见。 */
    fun fromLegacyV1(json: String?): List<CardLayoutEntry>? {
        if (json.isNullOrBlank()) return null
        return try {
            val array = JSONArray(json)
            val result = mutableListOf<CardLayoutEntry>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                val size = BentoCardSize.fromNameOrNull(item.optString("size"))
                    ?: BentoCardSize.COMPACT_1X1
                result.add(CardLayoutEntry(id = id, size = size, visible = true))
            }
            if (result.isEmpty()) null else result
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 把已保存的布局与当前已注册插件的卡片声明对齐。
     *
     * - 保留已保存条目（顺序、可见性不变），但尺寸必须在该卡的支持范围内，否则回落到默认档位；
     * - 声明里存在但布局里没有的卡，按注册顺序追加（默认尺寸 / 默认可见性）；
     * - 布局里存在但已无对应卡片的条目（如已删除的占位卡）直接丢弃；
     * - [saved] 为 null 时全部用默认值。
     */
    fun resolve(saved: List<CardLayoutEntry>?, specs: List<CardSpec>): List<CardLayoutEntry> {
        val specById = specs.associateBy { it.id }
        val result = mutableListOf<CardLayoutEntry>()

        saved?.forEach { entry ->
            val spec = specById[entry.id] ?: return@forEach
            val size = if (entry.size in spec.supportedSizes) entry.size else spec.defaultSize
            result.add(entry.copy(size = size))
        }

        val seen = result.map { it.id }.toSet()
        specs.forEach { spec ->
            if (spec.id !in seen) {
                result.add(
                    CardLayoutEntry(
                        id = spec.id,
                        size = spec.defaultSize,
                        visible = spec.defaultVisible
                    )
                )
            }
        }
        return normalizeFirstCard(result, specs)
    }

    /**
     * 把属于 [cardIds] 的条目置为不可见（「清除插件数据」用）。
     *
     * 不能直接删条目：`resolve` 会把 manifest 里有、布局里没有的卡按默认值补回来，
     * 删掉等于重启后卡片自己回来。保留条目 + `visible = false` 才能让首页真正干净，
     * 同时用户仍可在插件页「可添加」里加回。
     */
    fun hideCards(
        entries: List<CardLayoutEntry>,
        cardIds: Set<String>
    ): List<CardLayoutEntry> = if (cardIds.isEmpty()) entries
    else entries.map { if (it.id in cardIds) it.copy(visible = false) else it }

    /**
     * 首位卡必须是整宽（≥2 列）：桌宠趴在它上沿，且它是首页的主操作区。
     *
     * 1×1 的首位卡会被提升为 2×1；若该卡不支持 2×1 则尝试 2×2；都不支持就保持原样
     * （这种卡本来就不该排在首位，拖拽逻辑会拒绝这种换位）。
     */
    fun normalizeFirstCard(
        entries: List<CardLayoutEntry>,
        specs: List<CardSpec>
    ): List<CardLayoutEntry> {
        val first = entries.firstOrNull() ?: return entries
        if (first.size.colSpan >= 2) return entries
        val spec = specs.firstOrNull { it.id == first.id } ?: return entries
        val promoted = when {
            BentoCardSize.EXPANDED_2X1 in spec.supportedSizes -> BentoCardSize.EXPANDED_2X1
            BentoCardSize.HERO_2X2 in spec.supportedSizes -> BentoCardSize.HERO_2X2
            else -> null
        } ?: return entries
        return listOf(first.copy(size = promoted)) + entries.drop(1)
    }
}
