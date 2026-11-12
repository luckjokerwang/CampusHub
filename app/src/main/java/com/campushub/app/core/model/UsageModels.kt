package com.campushub.app.core.model

/**
 * 用水相关的领域模型。
 *
 * 从 `ui/dashboard/DashboardUiState.kt` 上移，使 `core/service` 下的服务实现
 * 不必反向依赖 UI 层。字段保持原样（含已格式化的展示字段），以保证迁移过程
 * 行为零变化；后续如需纯领域模型再单独拆分。
 */
data class WaterBillingRecord(
    val id: String,
    val deviceName: String,
    val timeFormatted: String,
    val amountFormatted: String,
    val volumeText: String = "0 ml",
    val durationText: String = "0 秒",
    val promoText: String = "无抵扣",
    val realCostYuan: Double = 0.0,
    val realDiscountYuan: Double = 0.0,
    val realVolumeMl: Int = 0,
    val realDurationSecs: Double = 0.0,
    val timestampMillis: Long = 0L
)

data class BoundDevice(
    val did: String,
    val name: String,
    val location: String = "江西财经大学（麦庐园北区）",
    val isCurrent: Boolean = false
)

data class UsagePeriodRow(
    val periodLabel: String,
    val subLabel: String,
    val count: Int,
    val totalVolumeMl: Int,
    val totalPaidYuan: Double,
    val totalDiscountYuan: Double
)

data class UsageSummaryStats(
    val todayMl: Int = 0,
    val todayCost: Double = 0.0,
    val weekMl: Int = 0,
    val weekCost: Double = 0.0,
    val monthMl: Int = 0,
    val monthCost: Double = 0.0,
    val yearMl: Int = 0,
    val yearCost: Double = 0.0
)
