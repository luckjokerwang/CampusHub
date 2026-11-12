package com.campushub.app.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 主题模式：跟随系统 / 强制浅色 / 强制深色。 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val Context.campusSettingsStore by preferencesDataStore(name = "campushub_settings")

/**
 * 应用设置与布局持久化。
 *
 * 凭据走 `SecureStorage`（加密），界面偏好不加密，因此放在 DataStore：
 * 布局与主题不属于敏感信息，放进 KeyStore 保护的存储只会让读写变慢。
 *
 * 公开壳没有旧版 `bento_layout_v1`（那是某个具体插件的历史遗留），
 * 因此不提供迁移入口；私有主仓的版本多一个 `migrateLegacyLayout`。
 */
class AppSettingsRepository(private val context: Context) {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val cardLayout = stringPreferencesKey("card_layout_v2")
    }

    val themeMode: Flow<ThemeMode> = context.campusSettingsStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[Keys.themeMode].orEmpty()) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    val cardLayout: Flow<List<CardLayoutEntry>?> = context.campusSettingsStore.data.map { prefs ->
        CardLayoutCodec.decode(prefs[Keys.cardLayout])
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.campusSettingsStore.edit { prefs ->
            prefs[Keys.themeMode] = mode.name
        }
    }

    suspend fun setCardLayout(entries: List<CardLayoutEntry>) {
        context.campusSettingsStore.edit { prefs ->
            prefs[Keys.cardLayout] = CardLayoutCodec.encode(entries)
        }
    }
}
