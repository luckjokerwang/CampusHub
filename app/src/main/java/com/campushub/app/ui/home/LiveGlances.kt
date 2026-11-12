package com.campushub.app.ui.home

import com.campushub.app.core.plugin.CardSpec
import com.campushub.app.core.plugin.CardState
import com.campushub.app.core.plugin.GlanceModel
import com.campushub.app.ui.dashboard.DashboardUiState

/**
 * 宿主侧的卡片映射（纯函数，无 Compose）。
 *
 * 公开壳里所有卡片都来自插件的 [com.campushub.app.core.plugin.GlanceSource]，
 * 因此这里只做"取不到就占位"。
 *
 * 私有完整版在这里还有 live 卡（水控会话、积分任务）——那些状态来自宿主自己的
 * 状态机，不属于插件契约，所以不在公开壳中。
 */
object LiveGlances {

    fun of(
        cardId: String,
        state: DashboardUiState,
        specs: Map<String, CardSpec>
    ): GlanceModel {
        val spec = specs[cardId]
        return when {
            // live 卡需要宿主状态机；公开壳没有内置 live 卡，声明了也只会显示占位
            spec != null && spec.live -> GlanceModel(
                cardId = cardId,
                state = CardState.EMPTY,
                title = spec.title,
                sub = "该卡片需要宿主状态机支持"
            )
            else -> state.sourceGlances[cardId] ?: GlanceModel(
                cardId = cardId,
                state = CardState.EMPTY,
                title = spec?.title ?: cardId,
                sub = "正在同步…"
            )
        }
    }
}
