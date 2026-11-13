package com.campushub.app.core.bridge

import com.campushub.app.core.model.BleDevice
import kotlinx.serialization.Serializable

@Serializable
data class BridgeHttpResponse(
    val status: Int,
    val headers: Map<String, String> = emptyMap(),
    val data: String = "",
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
