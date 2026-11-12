package com.campushub.app.core.service

import com.campushub.app.core.model.PluginCategory

/**
 * 校园服务注册表。
 *
 * 进程内单例，由 [com.campushub.app.CampusApplication] 在启动时注册内置服务。
 * UI 只从这里取服务，不直接 `new` 具体实现，因此新增服务不需要改 UI。
 */
object ServiceRegistry {

    private val byId = LinkedHashMap<String, CampusService>()

    @Volatile
    private var activeWaterService: WaterControlService? = null

    @Synchronized
    fun register(service: CampusService) {
        byId[service.id] = service
        if (service is WaterControlService && activeWaterService == null) {
            activeWaterService = service
        }
    }

    fun registerAll(vararg services: CampusService) {
        services.forEach(::register)
    }

    /** 当前激活的水控服务；未注册任何水控服务时为 null。 */
    fun water(): WaterControlService? = activeWaterService

    /** 手动切换激活的水控服务（未来同时安装多个水控服务时使用）。 */
    @Synchronized
    fun activateWater(id: String) {
        val service = byId[id]
        if (service is WaterControlService) activeWaterService = service
    }

    fun byCategory(category: PluginCategory): List<CampusService> =
        byId.values.filter { it.category == category }

    fun all(): List<CampusService> = byId.values.toList()

    /** 按 id 取服务；未注册时为 null。 */
    fun get(id: String): CampusService? = byId[id]

    @Synchronized
    fun clear() {
        byId.clear()
        activeWaterService = null
    }
}
