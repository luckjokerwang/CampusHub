package com.campushub.app.core.plugin

/**
 * 插件声明（manifest）。
 *
 * 插件只提供数据：卡片要看什么（[CardSpec]）、详情页分几段（`DetailPage`）、
 * 登录/配置表单长什么样（`ConfigGroup`）。**插件不得自带 Compose UI**，
 * 也不得新增标签页或页面——宿主统一渲染，新增插件不增加屏幕数。
 *
 * 数值与视觉约束见 `design/tokens.json` 与 `docs/specs/plugin-contract.md`。
 */

/** 强调色角色。同屏最多 4 个；未接入项一律用 [NEUTRAL]（ink3）。 */
enum class PluginAccent { WATER, BATH, CREDIT, SCORE, NEUTRAL }

/**
 * 插件来源。
 *
 * [USER] 只是为将来的「上传 manifest.json」预留；当前没有任何加载器实现，
 * 所有已注册服务都是 [BUILTIN]。
 */
enum class PluginSource { BUILTIN, USER }

/**
 * 一张卡片的声明。
 *
 * @param id 卡片 id，同时是布局持久化与 glance 查表的键。沿用旧 id 以便旧布局迁移。
 * @param title 卡片标题（不用服务名，服务名放 `GlanceModel.serviceLabel`）。
 * @param iconGlyph 单字图标字形（如「水」「浴」「学」）。
 * @param live true = 该卡由宿主会话/积分状态机产出（只允许水控与积分任务），
 *             插件不必实现 `GlanceSource`；false = 插件自己实现 `GlanceSource`。
 */
data class CardSpec(
    val id: String,
    val title: String,
    val iconGlyph: String,
    val accent: PluginAccent,
    val defaultSize: BentoCardSize,
    val supportedSizes: Set<BentoCardSize> = BentoCardSize.entries.toSet(),
    val defaultVisible: Boolean = true,
    val live: Boolean = false
)

/**
 * 一个服务的完整声明。
 *
 * @param id 与服务 `CampusService.id` 相同，也是 `SecureStorage.forPlugin(id)` 的命名空间。
 * @param version 展示用版本号（如 `v3.1.9`），无版本概念时为 null。
 */
data class PluginManifest(
    val id: String,
    val name: String,
    val description: String,
    val iconGlyph: String,
    val accent: PluginAccent,
    val version: String? = null,
    val source: PluginSource = PluginSource.BUILTIN,
    val cards: List<CardSpec>
)
