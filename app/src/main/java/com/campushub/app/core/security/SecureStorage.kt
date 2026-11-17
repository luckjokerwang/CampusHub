package com.campushub.app.core.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.campushub.app.core.bridge.StorageBridge
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap

/**
 * 硬件级安全存储层 (Android KeyStore + AES-256 GCM)
 *
 * 为每个插件提供严格隔离的命名空间沙箱，避免插件越权窃取凭据；
 * 当检测到硬件密钥失效（[KeyPermanentlyInvalidatedException]）或 Tink Keyset 损坏时，
 * 自动熔断清空失效 KeyStore 别名与密文文件并重建，防止冷启动闪退。
 */
class SecureStorage private constructor(private val context: Context) {

    private val storageLock = Any()
    @Volatile
    private var cachedMasterKey: MasterKey? = null
    private val prefCache = ConcurrentHashMap<String, SharedPreferences>()

    private fun getOrCreateMasterKey(): MasterKey {
        cachedMasterKey?.let { return it }
        return synchronized(storageLock) {
            cachedMasterKey ?: buildMasterKey().also { cachedMasterKey = it }
        }
    }

    private fun buildMasterKey(): MasterKey =
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

    /**
     * 获取指定插件命名空间下的安全存储接口
     */
    fun forPlugin(pluginId: String): PluginStorage {
        val safeNamespace = pluginId.replace(Regex("[^a-zA-Z0-9_]"), "_")
        prefCache[safeNamespace]?.let { return PluginStorage(it) }
        val prefs = synchronized(storageLock) {
            prefCache[safeNamespace] ?: createEncryptedPrefsLocked(safeNamespace).also {
                prefCache[safeNamespace] = it
            }
        }
        return PluginStorage(prefs)
    }

    private fun createEncryptedPrefsLocked(namespace: String): SharedPreferences {
        val fileName = "campushub_sec_${namespace}.pref"
        return try {
            openEncryptedPrefs(fileName, getOrCreateMasterKey())
        } catch (e: Exception) {
            if (!isRecoverableCryptoError(e)) throw e
            Log.w(TAG, "EncryptedPrefs corrupted for ns=$namespace (${e.javaClass.simpleName}), resetting prefs")
            deletePrefsFiles(fileName)
            try {
                openEncryptedPrefs(fileName, getOrCreateMasterKey())
            } catch (second: Exception) {
                if (!isRecoverableCryptoError(second)) throw second
                Log.w(TAG, "MasterKey invalidated for ns=$namespace, resetting KeyStore alias")
                resetMasterKeyAliasLocked()
                deletePrefsFiles(fileName)
                openEncryptedPrefs(fileName, getOrCreateMasterKey())
            }
        }
    }

    private fun openEncryptedPrefs(fileName: String, masterKey: MasterKey): SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    private fun deletePrefsFiles(fileName: String) {
        runCatching { context.deleteSharedPreferences(fileName) }
        val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        listOf("$fileName.xml", "$fileName.bak", fileName).forEach { name ->
            runCatching { File(prefsDir, name).takeIf(File::exists)?.delete() }
        }
    }

    private fun resetMasterKeyAliasLocked() {
        cachedMasterKey = null
        prefCache.clear()
        runCatching {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (ks.containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS)) {
                ks.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            }
        }.onFailure { err ->
            Log.w(TAG, "Failed to delete AndroidKeyStore alias", err)
        }
    }

    companion object {
        private const val TAG = "SecureStorage"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"

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
 * 判断异常链是否由 Android KeyStore 密钥失效或 Tink 密文/Keyset 损坏导致，以便触发熔断重置。
 */
internal fun isRecoverableCryptoError(error: Throwable): Boolean =
    generateSequence(error) { it.cause }.any { cause ->
        cause is KeyPermanentlyInvalidatedException ||
            cause is GeneralSecurityException ||
            cause is IOException
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
