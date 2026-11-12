package com.campushub.app.core.bridge

/**
 * 单测用的假 HTTP 桥：按请求返回预置响应，不发真实请求。
 *
 * 用法：
 * ```
 * val http = FakeHttp(BridgeHttpResponse(status = 200, data = """{"code":0}"""))
 * ```
 *
 * 需要按 URL 区分响应时，用 [routes] 构造。
 */
class FakeHttp(
    private val response: BridgeHttpResponse = BridgeHttpResponse(status = 200),
    private val routes: Map<String, BridgeHttpResponse> = emptyMap()
) : HttpBridge {

    /** 记录收到的请求，便于断言「带没带鉴权头」。 */
    val requests = mutableListOf<Request>()

    data class Request(
        val method: String,
        val url: String,
        val body: String?,
        val headers: Map<String, String>?
    )

    override suspend fun request(
        method: String,
        url: String,
        body: String?,
        headers: Map<String, String>?,
        timeoutSeconds: Long?
    ): BridgeHttpResponse {
        requests += Request(method, url, body, headers)
        val matched = routes.entries.firstOrNull { url.contains(it.key) }
        return matched?.value ?: response
    }
}
