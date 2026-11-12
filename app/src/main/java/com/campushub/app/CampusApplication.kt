package com.campushub.app

import android.app.Application
import com.campushub.app.core.bridge.HttpBridgeImpl
import com.campushub.app.core.security.SecureStorage
import com.campushub.app.core.settings.AppSettingsRepository
import com.campushub.app.core.service.ServiceRegistry
import com.campushub.app.core.service.example.ExampleService

/**
 * 公开壳的 Application：只注册示例服务。
 *
 * 私有完整版在这里注册真实校园服务；公开壳只注册离线示例。
 * 新增插件时只改这一处 + 新建 `core/service/<name>/`，**不要**往 ViewModel 里加函数。
 */
class CampusApplication : Application() {

    lateinit var secureStorage: SecureStorage
        private set

    lateinit var settings: AppSettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()

        secureStorage = SecureStorage.getInstance(this)

        ServiceRegistry.registerAll(
            ExampleService(storage = secureStorage.forPlugin(ExampleService.ID))
        )

        settings = AppSettingsRepository(this)
    }
}
