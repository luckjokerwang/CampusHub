package com.campushub.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 插件业务类别（严格对齐 @campushub/sdk PluginCategory）
 */
@Serializable
enum class PluginCategory {
    @SerialName("water_cloud")
    WATER_CLOUD,

    @SerialName("water_ble")
    WATER_BLE,

    @SerialName("attendance")
    ATTENDANCE,

    @SerialName("campus_card")
    CAMPUS_CARD,

    @SerialName("utility")
    UTILITY
}
/**
 * 蓝牙扫描到的外部近场设备
 */
@Serializable
data class BleDevice(
    val id: String,
    val name: String? = null,
    val rssi: Int = 0
)
