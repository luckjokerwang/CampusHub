package com.campushub.app.core.plugin

import com.campushub.app.core.service.CampusService
import com.campushub.app.core.service.ServiceResult

/**
 * 插件能力接口。
 *
 * 只声明**数据**，不含 Compose；服务实现按需选择实现哪些接口：
 * - [ConfigurablePlugin]：有登录/配置表单（登录页由宿主统一渲染）
 * - [GlanceSource]：首页卡片的数据来源（`CardSpec.live == false` 时必须实现）
 * - [DetailSource]：详情页的数据来源
 *
 * 水控与积分任务的卡片是 `live` 的，由宿主的会话状态机直接产出 glance，
 * 不需要插件实现 [GlanceSource]。
 */
interface ConfigurablePlugin : CampusService {

    val configGroups: List<ConfigGroup>

    fun isConfigured(groupId: String): Boolean

    /** 字段初值（键与 [ConfigGroup.fields] 的 key 一致）。 */
    fun prefill(groupId: String): Map<String, String>

    /** 图形验证码图片字节（PNG/JPEG）。 */
    suspend fun loadCaptcha(groupId: String, values: Map<String, String>): ServiceResult<ByteArray> =
        unsupported()

    /** 请求短信验证码，成功返回冷却秒数。 */
    suspend fun requestSmsCode(groupId: String, values: Map<String, String>): ServiceResult<Int> =
        unsupported()

    /** 异步选项（学校搜索、蓝牙水表扫描）。 */
    suspend fun searchOptions(
        groupId: String,
        fieldKey: String,
        query: String
    ): ServiceResult<List<ConfigOption>> = unsupported()

    /** 提交本组表单，成功返回提示文案。 */
    suspend fun submit(groupId: String, values: Map<String, String>): ServiceResult<String>

    /** 清除本服务的凭据（保留设备绑定与别名）。 */
    suspend fun logout()
}

interface GlanceSource : CampusService {
    /** 不抛异常；失败用 [CardState.ERROR] 表达。 */
    suspend fun glance(cardId: String): GlanceModel
}

interface DetailSource : CampusService {
    suspend fun detailPages(): ServiceResult<List<DetailPage>>

    /**
     * 执行详情页命令。
     *
     * @param commandId 页面动作或行内动作的 id
     * @param rowId 行内动作所在行的 id
     * @param input PROMPT_TEXT 动作收集到的输入
     */
    suspend fun onDetailCommand(
        commandId: String,
        rowId: String?,
        input: String?
    ): ServiceResult<String>
}

/** 能力接口的默认实现：该服务不支持此操作。 */
internal fun unsupported(): ServiceResult<Nothing> =
    ServiceResult.BusinessError(-1, "该服务不支持此操作")
