package com.campushub.app.core.plugin

/**
 * 详情页：插件只允许四种段落（KPI / 图表 / 记录流 / 表单）。
 * 需要新段落类型时，先在设计仓 `design/components.html` 里定义，再扩这里的枚举。
 */

/** 记录行金额的语气：收入（water）/ 支出（score）/ 中性。 */
enum class AmountTone { IN, OUT, NEUTRAL }

/** 行内动作的交互方式。 */
enum class RowActionKind {
    /** 直接执行。 */
    COMMAND,

    /** 先弹一个单输入框（[RowAction.prompt] 作为标题），把输入作为 `input` 传回。 */
    PROMPT_TEXT,

    /** 先弹确认框。 */
    CONFIRM
}

data class RowAction(
    val id: String,
    val label: String,
    val kind: RowActionKind,
    val prompt: String? = null
)

data class RowItem(
    val id: String,
    val title: String,
    val sub: String? = null,
    val amount: String? = null,
    val tone: AmountTone = AmountTone.NEUTRAL,
    val badge: String? = null,
    val actions: List<RowAction> = emptyList()
)

data class KpiItem(val label: String, val value: String, val unit: String? = null)

/** 大号 KPI 卡（如「1284 分」+ 右侧两行明细 + 进度条）。 */
data class KpiHighlight(
    val chip: String?,
    val readout: String,
    val unit: String?,
    val side: List<GlanceDetail>,
    val progress: Float? = null
)

data class ChartBar(val label: String, val value: Double, val highlighted: Boolean)

sealed interface DetailSection {
    data class Kpi(val items: List<KpiItem>, val highlight: KpiHighlight? = null) : DetailSection
    data class Chart(val title: String, val summary: String?, val bars: List<ChartBar>) : DetailSection
    data class Records(val title: String?, val rows: List<RowItem>, val emptyText: String) : DetailSection
    data class Form(
        val title: String?,
        val fields: List<ConfigField>,
        val values: Map<String, String>
    ) : DetailSection
}

/** 页面级按钮（页头右侧或底部）。 */
data class PageAction(
    val label: String,
    val commandId: String,
    val style: ActionStyle = ActionStyle.PRIMARY,
    val busy: Boolean = false
)
/** 详情页悬浮胶囊导航图标（宿主内置映射）。 */
enum class DetailNavIcon { STATS, DEVICES, TASKS, ACCOUNT, CREDIT, OVERVIEW }


/**
 * 一个详情页。多个页由宿主的 `HorizontalPager` 横向排列。
 *
 * @param headerAction 页头右侧动作（如「添加」「导出」）。
 * @param actions 底部按钮，最多 2 个。
 * @param note 页面末尾的说明文字。
 */
data class DetailPage(
    val id: String,
    val title: String,
    val headerAction: PageAction? = null,
    val sections: List<DetailSection>,
    val actions: List<PageAction> = emptyList(),
    val note: String? = null,
    val navLabel: String = title,
    val navIcon: DetailNavIcon = DetailNavIcon.OVERVIEW
)
