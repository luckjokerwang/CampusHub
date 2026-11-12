package com.campushub.app.core.plugin

/**
 * 卡片 glance：插件告诉宿主「这张卡现在显示什么」。
 *
 * 宿主按 [GlanceLayoutRules] 决定哪些槽位在该档位可见，插件**不能**改槽位规则：
 * 内容放不下就整块隐藏，绝不压缩、不换行。
 */

/** 六状态（固定枚举，插件不得新增）。只有 [RUNNING] 允许抢视觉。 */
enum class CardState { IDLE, RUNNING, DONE, EMPTY, ERROR, UNBOUND }

/** 动作样式。[DANGER] 只用于「停止/删除」这类破坏性动作。 */
enum class ActionStyle { PRIMARY, DANGER, SECONDARY }

/** 动作按钮的前置图标（宿主内置，插件只选不画）。 */
enum class ActionIcon { DROPLET }

/** 点击目标。宿主负责路由；插件只声明意图。 */
sealed interface GlanceTarget {
    /** 进入本插件的某个详情页。 */
    data class Page(val pageId: String) : GlanceTarget

    /** 进入本插件的登录/配置页。 */
    data object Login : GlanceTarget

    /** 进入宿主钱包页（由宿主决定是否提供）。 */
    data object Wallet : GlanceTarget

    /**
     * 执行一个命令，交给宿主分发（如 `water.toggle`、`score.run`）。
     *
     * @param arg 命令参数（如 `device.switch` 的目标 did）
     */
    data class Command(val id: String, val arg: String? = null) : GlanceTarget
}

/**
 * 一个可点的动作。
 *
 * @param busy 进行中：按钮显示转圈且不可点。
 */
data class GlanceAction(
    val label: String,
    val target: GlanceTarget,
    val style: ActionStyle = ActionStyle.PRIMARY,
    val busy: Boolean = false,
    val icon: ActionIcon? = null
)

/** 头部下拉里的一个选项（如一台水机）。 */
data class GlanceOption(val id: String, val label: String, val selected: Boolean)

/**
 * 头部下拉：把「当前用的是哪一个」直接摊开，而不是给一个抽象的「切换」按钮。
 *
 * 宿主把当前选项的 label 显示在卡片标题后面并带一个 ▾，点开是全部选项；
 * 选中后以 [commandId] + 选项 id 发回宿主。
 */
data class GlanceSwitcher(
    val options: List<GlanceOption>,
    val commandId: String
)

/** 一项次要明细（如「流速 1.90 L/min」）。 */
data class GlanceDetail(val label: String, val value: String)

/**
 * 一张卡的全部展示数据。
 *
 * @param serviceLabel 服务名（卡片标题下方的小字，如「示例服务」）。
 * @param chip 状态 chip 文案（如「出水中」）。
 * @param readout 主读数（已格式化，如「1.42」）。
 * @param readoutUnit 读数单位（如「L」）。
 * @param sub 一行副说明。
 * @param progress 0f..1f，null = 不显示进度条。
 * @param action 底部动作按钮。
 * @param link 文字链（如「去领取 ›」），与 [action] 互斥使用。
 * @param headerLink 头部右侧文字链（如「切换」）。
 * @param tapTarget 整卡点击目标，null = 不响应。
 */
data class GlanceModel(
    val cardId: String,
    val state: CardState,
    val title: String,
    val serviceLabel: String? = null,
    val chip: String? = null,
    val readout: String? = null,
    val readoutUnit: String? = null,
    val sub: String? = null,
    val details: List<GlanceDetail> = emptyList(),
    val progress: Float? = null,
    val action: GlanceAction? = null,
    val link: GlanceAction? = null,
    val headerLink: GlanceAction? = null,
    val switcher: GlanceSwitcher? = null,
    val tapTarget: GlanceTarget? = null
)

/** 卡片内可显示的槽位。 */
enum class GlanceSlot {
    SERVICE_LABEL,
    CHIP,
    SUB,
    DETAILS,
    PROGRESS,
    ACTION,
    LINK,
    HEADER_LINK
}

/**
 * 槽位可见性规则（纯函数，便于单测）。
 *
 * 1×1 只保留图标 + 标题 + 读数 + 一行副说明；其余档位全开。
 * 大字号（fontScale ≥ 1.3）时去掉副说明与服务标签——宁可少显示一层信息，也不要裁字。
 */
object GlanceLayoutRules {

    private val WIDE_SLOTS = setOf(
        GlanceSlot.SERVICE_LABEL,
        GlanceSlot.CHIP,
        GlanceSlot.SUB,
        GlanceSlot.DETAILS,
        GlanceSlot.PROGRESS,
        GlanceSlot.ACTION,
        GlanceSlot.LINK,
        GlanceSlot.HEADER_LINK
    )

    fun visibleSlots(size: BentoCardSize, largeFont: Boolean): Set<GlanceSlot> {
        val base = when (size) {
            BentoCardSize.COMPACT_1X1 -> setOf(GlanceSlot.SUB, GlanceSlot.ACTION)
            BentoCardSize.EXPANDED_2X1,
            BentoCardSize.TALL_1X2,
            BentoCardSize.HERO_2X2 -> WIDE_SLOTS
        }
        if (!largeFont) return base
        return base - setOf(GlanceSlot.SERVICE_LABEL, GlanceSlot.SUB)
    }

    /** 该档位最多显示几条明细。 */
    fun maxDetails(size: BentoCardSize): Int = when (size) {
        BentoCardSize.COMPACT_1X1 -> 0
        BentoCardSize.EXPANDED_2X1 -> 1
        BentoCardSize.TALL_1X2 -> 3
        BentoCardSize.HERO_2X2 -> 6
    }
}
