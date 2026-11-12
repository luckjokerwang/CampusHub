package com.campushub.app.core.service.example

import com.campushub.app.core.service.ServiceResult
import org.json.JSONObject

/**
 * 示例服务的协议常量与纯解析。
 *
 * 这一层**不依赖 Android**，只放 URL、请求头与 JSON 解析，便于 JVM 单测。
 * 接入真实校园服务时把这里换成你自己的网关地址与字段映射即可。
 */
object ExampleProtocol {

    /** 演示用地址：真实接入时替换成学校网关。 */
    const val BASE_URL = "https://example.invalid/campushub"

    const val OVERVIEW_PATH = "/v1/overview"

    const val HEADER_ACCEPT = "Accept"
    const val ACCEPT_JSON = "application/json"

    /** 网关信封里的业务码字段名。 */
    private const val KEY_CODE = "code"
    private const val KEY_MESSAGE = "message"
    private const val KEY_DATA = "data"

    /** 解析后的一条校园数据。 */
    data class Overview(
        val displayName: String,
        val balanceText: String,
        val items: List<Item>
    )

    data class Item(val label: String, val value: String)

    /**
     * 解析网关响应。
     *
     * 失败分类必须区分：HTTP 层由调用方处理，这里只处理业务码与结构。
     * 返回 [ServiceResult.BusinessError] 表示"网关答了但不是成功"，
     * 返回 [ServiceResult.Ok] 才是可以展示的数据。
     */
    fun parseOverview(body: String): ServiceResult<Overview> {
        val json = runCatching { JSONObject(body) }.getOrElse {
            return ServiceResult.BusinessError(-1, "响应不是合法 JSON")
        }
        val code = json.optInt(KEY_CODE, -1)
        if (code != 0) {
            val message = json.optString(KEY_MESSAGE).ifBlank { "业务错误 $code" }
            return ServiceResult.BusinessError(code, message)
        }
        val data = json.optJSONObject(KEY_DATA)
            ?: return ServiceResult.BusinessError(-1, "响应缺少 data 字段")

        val itemsJson = data.optJSONArray("items")
        val items = buildList {
            if (itemsJson != null) {
                for (i in 0 until itemsJson.length()) {
                    val item = itemsJson.optJSONObject(i) ?: continue
                    val label = item.optString("label")
                    if (label.isBlank()) continue
                    add(Item(label = label, value = item.optString("value")))
                }
            }
        }
        return ServiceResult.Ok(
            Overview(
                displayName = data.optString("displayName").ifBlank { "同学" },
                balanceText = data.optString("balanceText").ifBlank { "—" },
                items = items
            )
        )
    }
}
