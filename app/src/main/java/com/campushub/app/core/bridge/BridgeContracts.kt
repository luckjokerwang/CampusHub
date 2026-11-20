package com.campushub.app.core.bridge

import com.campushub.app.core.model.BleDevice
import kotlinx.serialization.Serializable

@Serializable
data class BridgeHttpResponse(
    val status: Int,
    val headers: Map<String, String> = emptyMap(),
    val data: String = "",
    /** 原始 Set-Cookie 列表；同名响应头可能重复，不能压进 [headers]。 */
    val setCookies: List<String> = emptyList(),
    /**
     * 传输层失败原因（连接被拒、TLS 握手失败、超时…）。
     *
     * 非空时 [data] 一定是空串：**错误文本不能伪装成响应体**，
     * 否则调用方会把 `{"error":"..."}` 当成网关返回的 JSON 去解析，
     * 真实原因（DNS / TLS / 超时）就丢了，用户只看到「网关响应错误 HTTP -1」。
     */
    val transportError: String? = null
)

/**
 * HTTP 通信桥梁规范（对齐 @campushub/sdk HttpClient）
 */
interface HttpBridge {
    suspend fun request(
        method: String,
        url: String,
        body: String? = null,
        headers: Map<String, String>? = null,
        timeoutSeconds: Long? = 15L
    ): BridgeHttpResponse

    /** 手动跟随 SSO 重定向；默认保持旧行为，测试替身只需实现 [request]。 */
    suspend fun requestWithoutRedirects(
        method: String,
        url: String,
        body: String? = null,
        headers: Map<String, String>? = null,
        timeoutSeconds: Long? = 15L
    ): BridgeHttpResponse = request(method, url, body, headers, timeoutSeconds)

    /**
     * 取二进制响应（如图形验证码）。失败返回 null，由调用方转成带类型的错误。
     * 默认实现返回 null：测试替身只需实现 [request] 即可继续工作。
     */
    suspend fun requestBytes(url: String, headers: Map<String, String>): ByteArray? = null

    /**
     * 把 [url] 流式下载到 [target]，返回是否成功。
     *
     * 大文件（应用内更新的 10 MB 安装包）必须走这里而不是 [requestBytes]：
     * 后者把整包读进内存，弱网下既没有进度也容易 OOM。
     * [onProgress] 收到 (已写字节, 总字节)，总字节未知时为 -1。
     *
     * 默认实现用 [requestBytes] 兜底，所以测试替身不必实现它。
     */
    suspend fun downloadTo(
        url: String,
        target: java.io.File,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Boolean {
        val bytes = requestBytes(url, emptyMap()) ?: return false
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
        onProgress(bytes.size.toLong(), bytes.size.toLong())
        return true
    }
}

/**
 * 插件级受限 HTTP 桥梁包装器：
 * 1. 默认强制 `https://`（拦截明文 HTTP 与协议降级重定向）；
 * 2. 强制校验请求域名及每次 3xx 重定向目标域名必须命中 [allowedHosts]（支持精确域名或 `*.example.com` 子域通配，严禁子串匹配）。
 */
class ScopedHttpBridge(
    private val delegate: HttpBridge,
    allowedHosts: Set<String>,
    private val enforceHttps: Boolean = true,
    private val maxRedirects: Int = 5
) : HttpBridge {
    private val normalizedAllowed: List<String> = allowedHosts
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }

    private fun validateUrl(url: String): String? {
        val uri = runCatching { java.net.URI(url) }.getOrNull()
            ?: return "安全策略拦截：非法 URL"
        val scheme = uri.scheme?.lowercase()
            ?: return "安全策略拦截：缺少协议 scheme"
        if (enforceHttps && scheme != "https") {
            return "安全策略拦截：仅允许 HTTPS 请求 ($scheme)"
        }
        if (scheme != "https" && scheme != "http") {
            return "安全策略拦截：不支持的协议 ($scheme)"
        }
        val host = uri.host?.lowercase()?.takeIf { it.isNotBlank() }
            ?: return "安全策略拦截：缺少有效主机名"
        if (!isHostAllowed(host)) {
            return "安全策略拦截：域名 $host 未在插件白名单中"
        }
        return null
    }

    private fun isHostAllowed(host: String): Boolean =
        normalizedAllowed.any { pattern ->
            if (pattern.startsWith("*.")) {
                val suffix = pattern.removePrefix("*") // ".example.com"
                host.endsWith(suffix) && host.length > suffix.length
            } else {
                host == pattern
            }
        }

    override suspend fun request(
        method: String,
        url: String,
        body: String?,
        headers: Map<String, String>?,
        timeoutSeconds: Long?
    ): BridgeHttpResponse {
        var currentMethod = method.uppercase()
        var currentUrl = url
        var currentBody = body
        var hops = 0
        while (true) {
            validateUrl(currentUrl)?.let { reason ->
                return BridgeHttpResponse(status = -1, transportError = reason)
            }
            val response = delegate.requestWithoutRedirects(
                method = currentMethod,
                url = currentUrl,
                body = currentBody,
                headers = headers,
                timeoutSeconds = timeoutSeconds
            )
            if (response.status !in REDIRECT_STATUS_CODES) {
                return response
            }
            val location = response.headers.entries
                .firstOrNull { it.key.equals("Location", ignoreCase = true) }
                ?.value
                ?.takeIf { it.isNotBlank() }
                ?: return response
            if (hops >= maxRedirects) {
                return BridgeHttpResponse(
                    status = -1,
                    transportError = "安全策略拦截：重定向次数超过上限 ($maxRedirects)"
                )
            }
            val nextUrl = runCatching {
                java.net.URI(currentUrl).resolve(location).toString()
            }.getOrNull() ?: return BridgeHttpResponse(
                status = -1,
                transportError = "安全策略拦截：非法重定向地址"
            )
            validateUrl(nextUrl)?.let { reason ->
                return BridgeHttpResponse(status = -1, transportError = reason)
            }
            if (response.status == 303 ||
                ((response.status == 301 || response.status == 302) && currentMethod == "POST")
            ) {
                currentMethod = "GET"
                currentBody = null
            }
            currentUrl = nextUrl
            hops++
        }
    }

    override suspend fun requestWithoutRedirects(
        method: String,
        url: String,
        body: String?,
        headers: Map<String, String>?,
        timeoutSeconds: Long?
    ): BridgeHttpResponse {
        validateUrl(url)?.let { reason ->
            return BridgeHttpResponse(status = -1, transportError = reason)
        }
        return delegate.requestWithoutRedirects(method, url, body, headers, timeoutSeconds)
    }

    override suspend fun requestBytes(url: String, headers: Map<String, String>): ByteArray? {
        if (validateUrl(url) != null) return null
        return delegate.requestBytes(url, headers)
    }

    override suspend fun downloadTo(
        url: String,
        target: java.io.File,
        onProgress: (Long, Long) -> Unit
    ): Boolean {
        if (validateUrl(url) != null) return false
        return delegate.downloadTo(url, target, onProgress)
    }

    private companion object {
        val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
    }
}

/**
 * 隔离持久化键值桥梁规范（对齐 @campushub/sdk KeyValueStorage）
 */
interface StorageBridge {
    fun getItem(key: String): String?
    fun setItem(key: String, value: String)
    fun removeItem(key: String)
    fun clear()
    fun getAll(): Map<String, String>
}

/**
 * 低功耗蓝牙近场通讯桥梁规范（对齐 @campushub/sdk BleClient）
 */
interface BleBridge {
    suspend fun startScan(serviceUuids: List<String>? = null, timeoutMs: Long = 5000L): List<BleDevice>
    fun stopScan()
    suspend fun connect(deviceId: String): Boolean
    suspend fun disconnect(deviceId: String)
    suspend fun write(
        deviceId: String,
        serviceUuid: String,
        characteristicUuid: String,
        dataHex: String
    ): Boolean
    suspend fun read(
        deviceId: String,
        serviceUuid: String,
        characteristicUuid: String
    ): String?
    suspend fun startNotify(
        deviceId: String,
        serviceUuid: String,
        characteristicUuid: String,
        onNotification: (dataHex: String) -> Unit
    ): Boolean
}

/**
 * 系统吐司与进度通知桥梁规范（对齐 @campushub/sdk ToastUtils）
 */
interface ToastBridge {
    fun show(message: String, durationLong: Boolean = false)
    fun notify(title: String, content: String, progress: Int? = null)
}
