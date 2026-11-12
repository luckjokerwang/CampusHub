package com.campushub.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campushub.app.core.plugin.BentoCardSize
import com.campushub.app.core.plugin.CardSpec
import com.campushub.app.core.plugin.ConfigurablePlugin
import com.campushub.app.core.plugin.GlanceSource
import com.campushub.app.core.service.ServiceRegistry
import com.campushub.app.core.settings.AppSettingsRepository
import com.campushub.app.core.settings.CardLayoutCodec
import com.campushub.app.core.settings.CardLayoutEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 公开壳的控制台 ViewModel。
 *
 * 它只做三件事：维护便当盒布局、汇总"哪些分组已登录"、拉取非 live 卡的 glance。
 * **不要往这里加业务函数**——新增服务请在 `core/service/<name>/` 里实现契约，
 * 宿主会自动把它声明的卡片、配置与详情渲染出来。
 *
 * 私有完整版比这里多出水控会话、账单与积分任务链路，那些属于具体服务。
 */
class DashboardViewModel(
    private val settings: AppSettingsRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        // 布局：DataStore 订阅 → 与已注册插件的卡片声明对齐
        val repository = settings
        if (repository != null) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.cardLayout.collect { saved ->
                    val resolved = CardLayoutCodec.resolve(saved, allCardSpecs())
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(cardLayout = resolved) }
                    }
                }
            }
        } else {
            _uiState.update { it.copy(cardLayout = CardLayoutCodec.resolve(null, allCardSpecs())) }
        }
        refreshConfiguredGroups()
        refreshSourceGlances()
    }

    /** 刷新首页数据：分组状态 + 非 live 卡 glance。 */
    fun loadAccountAndData(showRefreshing: Boolean = false) {
        _uiState.update { it.copy(isRefreshing = showRefreshing, errorMessage = null) }
        refreshConfiguredGroups()
        refreshSourceGlances()
    }

    // ---------------------------------------------------------------- 插件契约

    /** 所有已注册插件声明的卡片，顺序即注册顺序。 */
    fun allCardSpecs(): List<CardSpec> = ServiceRegistry.all().flatMap { it.manifest.cards }

    /** 卡片 → 所属插件 id。 */
    fun pluginIdOfCard(cardId: String): String? =
        ServiceRegistry.all().firstOrNull { service ->
            service.manifest.cards.any { it.id == cardId }
        }?.id

    /** 重新计算"哪些分组已登录"，登录页与插件页据此显示状态。 */
    fun refreshConfiguredGroups() {
        val groups = buildSet {
            ServiceRegistry.all().filterIsInstance<ConfigurablePlugin>().forEach { plugin ->
                plugin.configGroups.forEach { group ->
                    if (runCatching { plugin.isConfigured(group.id) }.getOrDefault(false)) {
                        add("${plugin.id}/${group.id}")
                    }
                }
            }
        }
        _uiState.update { it.copy(configuredGroups = groups) }
    }

    /** 拉取非 live 卡的 glance。 */
    fun refreshSourceGlances() {
        viewModelScope.launch(Dispatchers.IO) {
            val results = mutableMapOf<String, com.campushub.app.core.plugin.GlanceModel>()
            ServiceRegistry.all().filterIsInstance<GlanceSource>().forEach { source ->
                source.manifest.cards.filter { !it.live }.forEach { card ->
                    val model = runCatching { source.glance(card.id) }.getOrNull()
                    if (model != null) results[card.id] = model
                }
            }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(sourceGlances = results) }
            }
        }
    }

    /** 登录/退出后刷新：分组状态 + 非 live 卡 glance。 */
    fun refreshAfterLogin() {
        loadAccountAndData()
    }

    /** 退出所有服务账号（保留卡片布局）。 */
    fun logoutAll() {
        viewModelScope.launch(Dispatchers.IO) {
            ServiceRegistry.all().filterIsInstance<ConfigurablePlugin>()
                .forEach { plugin -> runCatching { plugin.logout() } }
            withContext(Dispatchers.Main) { refreshAfterLogin() }
        }
    }

    /** 清除一次性错误提示（Snackbar 展示后调用）。 */
    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // ---------------------------------------------------------------- 便当盒布局

    fun toggleEditBentoMode() {
        _uiState.update { it.copy(isEditBentoMode = !it.isEditBentoMode) }
    }

    fun enterEditBentoMode() {
        _uiState.update { it.copy(isEditBentoMode = true) }
    }

    /** 卡片显隐（插件页的开关 / 加回）。 */
    fun setCardVisible(cardId: String, visible: Boolean) {
        updateLayout { entries ->
            entries.map { if (it.id == cardId) it.copy(visible = visible) else it }
        }
    }

    /** 卡片尺寸；不在该卡支持范围内时忽略。 */
    fun setCardSize(cardId: String, size: BentoCardSize) {
        val spec = allCardSpecs().firstOrNull { it.id == cardId } ?: return
        if (size !in spec.supportedSizes) return
        updateLayout { entries ->
            val updated = entries.map { if (it.id == cardId) it.copy(size = size) else it }
            CardLayoutCodec.normalizeFirstCard(updated, allCardSpecs())
        }
    }

    /** 交换两张卡在布局中的位置（拖动换位）。 */
    fun swapBentoCards(fromId: String, toId: String) {
        if (fromId == toId) return
        updateLayout { entries ->
            val list = entries.toMutableList()
            val fromIdx = list.indexOfFirst { it.id == fromId }
            val toIdx = list.indexOfFirst { it.id == toId }
            if (fromIdx < 0 || toIdx < 0) return@updateLayout entries
            val item = list.removeAt(fromIdx)
            list.add(toIdx, item)
            CardLayoutCodec.normalizeFirstCard(list, allCardSpecs())
        }
    }

    /** 直接更新便当盒完整布局（换位 + 调整尺寸组合操作）。 */
    fun updateCardLayout(newBoundLayout: List<CardLayoutEntry>) {
        updateLayout { entries ->
            val boundIds = newBoundLayout.map { it.id }.toSet()
            val remaining = entries.filter { it.id !in boundIds }
            val merged = newBoundLayout + remaining
            CardLayoutCodec.normalizeFirstCard(merged, allCardSpecs())
        }
    }

    private fun updateLayout(transform: (List<CardLayoutEntry>) -> List<CardLayoutEntry>) {
        val current = _uiState.value.cardLayout
        val next = transform(current)
        if (next == current) return
        _uiState.update { it.copy(cardLayout = next) }
        val repository = settings ?: return
        viewModelScope.launch(Dispatchers.IO) { repository.setCardLayout(next) }
    }
}
