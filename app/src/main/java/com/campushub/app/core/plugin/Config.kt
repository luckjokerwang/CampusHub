package com.campushub.app.core.plugin

/**
 * 登录/配置表单的声明式描述。
 *
 * 一个插件可以有多个分组（如「账号登录 / 设备绑定 / 高级选项」），
 * 宿主用同一个页面渲染所有插件。
 * 前六种类型与 `packages/sdk/src/types.ts` 的 `ConfigFieldSchema.type` 对应。
 */
enum class ConfigFieldType {
    TEXT,
    PHONE,
    PASSWORD,
    SELECT,
    SEARCH_SELECT,
    CAPTCHA_IMAGE,
    SMS_CODE,
    BLE_DEVICE,
    SWITCH
}

data class ConfigOption(val label: String, val value: String)

/**
 * 一个输入字段。
 *
 * @param dependsOn 该字段可用的前置字段 key（如短信码依赖图形码已填）。
 * @param requiredPermissions 使用该字段前需要申请的 Android 运行时权限（如蓝牙扫描）。
 * @param subtitle 说明文字（SWITCH 行用作副标题）。
 */
data class ConfigField(
    val key: String,
    val label: String,
    val type: ConfigFieldType,
    val required: Boolean = true,
    val placeholder: String? = null,
    val options: List<ConfigOption> = emptyList(),
    val dependsOn: String? = null,
    val requiredPermissions: List<String> = emptyList(),
    val subtitle: String? = null
)

/**
 * 一组字段 + 一个提交按钮。
 *
 * @param submitLabel 主按钮文案（默认「登录并绑定」）。
 * @param hint 底部说明。
 */
data class ConfigGroup(
    val id: String,
    val label: String,
    val fields: List<ConfigField>,
    val submitLabel: String = "登录并绑定",
    val hint: String? = null
)
