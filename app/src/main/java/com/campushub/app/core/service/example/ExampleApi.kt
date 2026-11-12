package com.campushub.app.core.service.example

import com.campushub.app.core.bridge.BridgeHttpResponse
import com.campushub.app.core.bridge.HttpBridge
import com.campushub.app.core.service.ServiceResult

/**
 * 示例服务的 HTTP 层。
 *
 * 职责只有两件：拼请求、把 HTTP 结果翻译成 [ServiceResult]。
 * 业务判断（业务码、字段映射）留给 [ExampleProtocol]，编排留给 [ExampleService]。
 *
 * 三类失败必须分开，否则 UI 只能笼统显示「没数据」：
 * - 连不上 / 非 2xx → [ServiceResult.NetworkError]
 * - 401 / 403 → [ServiceResult.Unauthorized]，引导重新登录
 * - 2xx 但业务码非 0 → [ServiceResult.BusinessError]
 */
class ExampleApi(
    private val http: HttpBridge,
    private val baseUrl: String = ExampleProtocol.BASE_URL
) {

    suspend fun overview(token: String?): ServiceResult<ExampleProtocol.Overview> {
        val headers = buildMap {
            put(ExampleProtocol.HEADER_ACCEPT, ExampleProtocol.ACCEPT_JSON)
            if (!token.isNullOrBlank()) put("Authorization", "Bearer $token")
        }
        val response: BridgeHttpResponse = http.request(
            method = "GET",
            url = baseUrl + ExampleProtocol.OVERVIEW_PATH,
            headers = headers
        )
        return when {
            response.status == -1 ->
                ServiceResult.NetworkError("无法连接服务器")
            response.status == 401 || response.status == 403 ->
                ServiceResult.Unauthorized("登录已失效，请重新登录")
            response.status !in 200..299 ->
                ServiceResult.NetworkError("服务器返回 HTTP ${response.status}", response.status)
            else -> ExampleProtocol.parseOverview(response.data)
        }
    }
}
