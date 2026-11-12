package com.campushub.app.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.campushub.app.core.bridge.StorageBridge
import java.io.File
import java.security.GeneralSecurityException
import java.util.concurrent.ConcurrentHashMap

/**
 * 硬件级安全存储层 (Android KeyStore + AES-256 GCM)
 *
 * 为每个插件提供严格隔离的命名空间沙箱，避免插件越权窃取凭据。
 */
class SecureStorage private constructor(private val context: Context) {

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefCache = ConcurrentHashMap<String, SharedPreferences>()

    /**
     * 获取指定插件命名空间下的安全存储接口
     */
    fun forPlugin(pluginId: String): PluginStorage {
        val safeNamespace = pluginId.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val prefs = prefCache.computeIfAbsent(safeNamespace) { ns ->
            createEncryptedPrefs(ns)
        }
        return PluginStorage(prefs)
    }

    private fun createEncryptedPrefs(namespace: String): SharedPreferences {
        val fileName = "campushub_sec_${namespace}.pref"
        return try {
            EncryptedSharedPreferences.create(
                context,
                fileName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: GeneralSecurityException) {
            // 当硬件安全密钥库失效（如系统异常锁屏重置）时防御性降级恢复
            val file = File(context.filesDir.parent, "shared_prefs/$fileName")
            if (file.exists()) {
                file.delete()
            }
            EncryptedSharedPreferences.create(
                context,
                fileName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    companion object {
        @Volatile
        private var instance: SecureStorage? = null

        fun getInstance(context: Context): SecureStorage {
            return instance ?: synchronized(this) {
                instance ?: SecureStorage(context.applicationContext).also { instance = it }
            }
        }
    }
}

/**
 * 单个插件专属的隔离存储门面
 */
class PluginStorage internal constructor(private val prefs: SharedPreferences) : StorageBridge {

    override fun getItem(key: String): String? {
        return prefs.getString(key, null)
    }

    override fun setItem(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun removeItem(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    override fun getAll(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for ((k, v) in prefs.all) {
            if (v is String) {
                result[k] = v
            }
        }
        return result
    }
}
