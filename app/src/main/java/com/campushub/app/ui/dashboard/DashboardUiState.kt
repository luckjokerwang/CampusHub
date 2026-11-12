package com.campushub.app.ui.dashboard

import com.campushub.app.core.plugin.GlanceModel
import com.campushub.app.core.settings.CardLayoutEntry

/**
 * 公开壳的首页状态。
 *
 * 私有完整版还有水控会话、账单、积分任务等字段——那些属于具体服务，
 * 公开壳只保留与插件契约相关的通用部分，示例插件走 [sourceGlances]。
 */
data class DashboardUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val isEditBentoMode: Boolean = false,

    /** 便当盒布局（与已注册插件的卡片声明对齐后的结果）。 */
    val cardLayout: List<CardLayoutEntry> = emptyList(),

    /** 非 live 卡的 glance（由插件的 GlanceSource 产出）。 */
    val sourceGlances: Map<String, GlanceModel> = emptyMap(),

    /** 已登录的分组，元素形如 `pluginId/groupId`。 */
    val configuredGroups: Set<String> = emptySet()
)
