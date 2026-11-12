package com.campushub.app.core.bridge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 基于 OkHttp 的 [HttpBridge] 默认实现。
 *
 * 公开壳只提供通用实现：超时、请求头注入、统一错误封装。
 * 私有主仓在 `SafeCampusDns` 里为特定校园网关做了固定节点与强制直连，
 * 那部分属于具体学校的网络环境适配，不随公开壳发布；插件作者如确有需要，
 * 在自己的服务层里构造专用 [OkHttpClient] 即可。
 */
class HttpBridgeImpl(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) : HttpBridge {

    override suspend fun request(
        method: String,
        url: String,
        body: String?,
        headers: Map<String, String>?,
        timeoutSeconds: Long?
    ): BridgeHttpResponse = withContext(Dispatchers.IO) {
        val activeClient = if (timeoutSeconds != null && timeoutSeconds > 0) {
            client.newBuilder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build()
        } else {
            client
        }

        val requestBuilder = Request.Builder().url(url)
        headers?.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

        val uppercaseMethod = method.uppercase()
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        when (uppercaseMethod) {
            "GET" -> requestBuilder.get()
            "POST" -> requestBuilder.post((body ?: "").toRequestBody(mediaType))
            "PUT" -> requestBuilder.put((body ?: "").toRequestBody(mediaType))
            "DELETE" -> requestBuilder.delete(body?.toRequestBody(mediaType))
            else -> requestBuilder.method(uppercaseMethod, body?.toRequestBody(mediaType))
        }

        try {
            val response = activeClient.newCall(requestBuilder.build()).execute()
            val responseBody = response.body?.string().orEmpty()
            val headerMap = mutableMapOf<String, String>()
            val respHeaders: Headers = response.headers
            for (i in 0 until respHeaders.size) {
                headerMap[respHeaders.name(i)] = respHeaders.value(i)
            }
            BridgeHttpResponse(status = response.code, headers = headerMap, data = responseBody)
        } catch (e: IOException) {
            // 连不上时用 -1 表示，由服务层翻译成 ServiceResult.NetworkError
            BridgeHttpResponse(
                status = -1,
                headers = emptyMap(),
                data = "{\"error\":\"${e.message?.replace("\"", "\\\"")}\"}"
            )
        }
    }

    override suspend fun requestBytes(url: String, headers: Map<String, String>): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val builder = Request.Builder().url(url)
                headers.forEach { (k, v) -> builder.addHeader(k, v) }
                val response = client.newCall(builder.build()).execute()
                if (!response.isSuccessful) return@withContext null
                response.body?.bytes()?.takeIf { it.isNotEmpty() }
            } catch (e: IOException) {
                null
            }
        }
}
