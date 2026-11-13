package com.campushub.app.core.service

import com.campushub.app.core.bridge.BridgeHttpResponse
import com.campushub.app.core.model.BoundDevice
import com.campushub.app.core.model.PluginCategory
import com.campushub.app.core.model.WaterBillingRecord
import com.campushub.app.core.plugin.PluginManifest

/**
 * 校园服务契约。
 *
 * 这是"一个 App 装下所有校园服务"的唯一扩展点：新增任何校园服务时，
 * 实现本接口并注册到 [ServiceRegistry]，**不要**再往
 * `DashboardViewModel` 里堆第 34 个函数。
 *
 * 与 `core/model/PluginModels.kt` 的关系：`PluginCategory` 是分类枚举，
 * 本接口是能力契约。QuickJS 动态插件未来落地时，同样需要适配到本接口。
 */
interface CampusService {
    /** 全局唯一标识，同时用作 SecureStorage 命名空间，例如 `cn.edu.example.campus`。 */
    val id: String

    /** 展示名称。 */
    val displayName: String

    /** 服务分类，用于 Bento 卡片归类与能力筛选。 */
    val category: PluginCategory

    /**
     * 插件声明：名称、强调色、以及本服务提供的卡片（[com.campushub.app.core.plugin.CardSpec]）。
     *
     * 宿主据此生成首页卡片、插件页列表与「未接入」条；新增服务只需改自己的 manifest，
     * 不需要改宿主 UI。
     */
    val manifest: PluginManifest
}

/**
 * 统一结果类型。
 *
 * 引入原因：迁移前所有失败路径都返回 `null`，导致「网络超时 / Token 过期 /
 * 业务 code≠0」在 UI 上表现完全一致（都是"没数据"）。新代码必须区分。
 */
sealed interface ServiceResult<out T> {

    data class Ok<T>(val value: T) : ServiceResult<T>

    /** 网络不可达、超时，或 HTTP 非 2xx。 */
    data class NetworkError(
        val message: String,
        val httpStatus: Int? = null
    ) : ServiceResult<Nothing>

    /** HTTP 401/403，或业务层判定 Token 失效。需要引导用户重新登录。 */
    data class Unauthorized(val message: String) : ServiceResult<Nothing>

    /** 网关返回了合法响应但业务 code != 0。 */
    data class BusinessError(
        val code: Int,
        val message: String
    ) : ServiceResult<Nothing>
}

/** 成功值转换；失败原样透传（保留错误类型）。 */
inline fun <T, R> ServiceResult<T>.map(transform: (T) -> R): ServiceResult<R> = when (this) {
    is ServiceResult.Ok -> ServiceResult.Ok(transform(value))
    is ServiceResult.NetworkError -> this
    is ServiceResult.Unauthorized -> this
    is ServiceResult.BusinessError -> this
}

/** 成功值继续展开；失败原样透传。用于「解析后发现数据缺失」这类二次失败。 */
inline fun <T, R> ServiceResult<T>.flatMap(transform: (T) -> ServiceResult<R>): ServiceResult<R> =
    when (this) {
        is ServiceResult.Ok -> transform(value)
        is ServiceResult.NetworkError -> this
        is ServiceResult.Unauthorized -> this
        is ServiceResult.BusinessError -> this
    }

/**
 * 非 2xx 响应的统一失败映射。
 *
 * 传输层失败（[BridgeHttpResponse.transportError] 非空）与网关返回错误状态码是两件事：
 * 前者要说清 DNS / TLS / 超时的真实原因，后者才该报 HTTP 码。
 * `status == -1` 是桥接层表示「没拿到响应」的哨兵值，不能当作 HTTP 状态码透给用户。
 */
fun BridgeHttpResponse.httpFailure(): ServiceResult.NetworkError =
    ServiceResult.NetworkError(
        message = transportError?.let { "网络不可达：$it" } ?: "网关响应错误 HTTP $status",
        httpStatus = status.takeIf { it > 0 }
    )

/** 一次出水会话的启动结果，携带结算所需的水表基线。 */
data class WaterSessionStart(
    val did: String,
    val startedAtMillis: Long,
    /** 各出水口的累计读数（升）。为空表示该机型不提供实时水表。 */
    val meterBaseline: List<Double>,
    /**
     * 已开阀但存在需要告知用户的情况（例如云端心跳刚断、指令可能延迟生效）。
     * UI 在 FLOWING 状态下把它作为 hint 展示，不当作失败。
     */
    val warning: String? = null
)

/** 单笔账单的结算明细。 */
data class BillDetail(
    val id: String,
    val paymentYuan: Double,
    val discountYuan: Double,
    val volumeMl: Int,
    val durationSecs: Double,
    val promoName: String?
)

/** 可查询积分余额的服务（首页积分展示用）。 */
interface ScoreQueryService : CampusService {
    /** 有效积分；请求失败时返回 [fallback]，不抛异常。 */
    suspend fun validScore(fallback: Int): Int
}

/**
 * 水控类服务能力集。
 *
 * Bento 卡片、桌面小部件、下拉磁贴都只依赖这组能力，因此任何新的水控服务
 * （云端 / 蓝牙等不同通道的水表）实现它即可复用全部 UI。
 */
interface WaterControlService : CampusService {

    /**
     * 该服务运行所需的 Android 运行时权限。
     *
     * 空表示不需要额外授权。UI 在触发动作前应检查并请求这些权限
     * （例如蓝牙水控需要 BLUETOOTH_SCAN / BLUETOOTH_CONNECT）。
     */
    val requiredAndroidPermissions: List<String> get() = emptyList()

    /**
     * 是否支持账单历史与"出账即结算"。
     *
     * 为 false 时调用方必须跳过结算等待（否则每次停水都要空等 45 秒），
     * 也不要展示账单/统计入口。默认 true，能力缺失的服务显式覆盖为 false。
     */
    val supportsBilling: Boolean get() = true

    /** 开阀。返回的 [WaterSessionStart] 含水表基线，供后续实时计量与结算使用。 */
    suspend fun startWater(did: String): ServiceResult<WaterSessionStart>

    /** 关阀。仅关闭，不负责出账等待。 */
    suspend fun stopWater(did: String): ServiceResult<Unit>

    /** 各出水口累计读数（升）；机型不支持实时水表时返回 null。 */
    suspend fun meterTotals(did: String): List<Double>?

    /** 绑定设备列表。[currentDid] 用于标记当前选中项。 */
    suspend fun devices(currentDid: String): ServiceResult<List<BoundDevice>>

    /** 历史账单，[limit] 为条数上限。 */
    suspend fun billingHistory(limit: Int): ServiceResult<List<WaterBillingRecord>>

    /** 查找 [sinceMillis] 之后新产生、且不在 [excludeIds] 内的已结算账单 id。 */
    suspend fun findSettledBill(sinceMillis: Long, excludeIds: Set<String>): String?

    /** 单笔账单详情，用于结算后展示真实水量与实付。 */
    suspend fun billDetail(billId: String): ServiceResult<BillDetail>

    /** 绑定 / 解绑设备（服务端收藏）。 */
    suspend fun bindDevice(did: String, bind: Boolean): ServiceResult<Unit>
}
