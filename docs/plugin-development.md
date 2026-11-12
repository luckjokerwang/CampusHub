# 写一个 CampusHub 插件

本文是**接线手册**：从零写一个内置插件，接到宿主上，跑通配置、卡片、详情和命令。
宿主的能力边界与禁止事项见 [architecture.md](architecture.md)。

## 0. 先判断这是不是插件

插件只能做「展示数据 + 提交表单 + 执行无副作用或服务端幂等的命令」。

| 你想做的事 | 走插件吗 |
|---|---|
| 拉一个只读接口，用卡片和详情展示 | ✅ |
| 登录后查询余额 / 成绩 / 积分 | ✅ |
| 需要一个宿主持有的会话状态机（如持续出水计时） | ❌ 属于宿主能力，见 §8 |
| 需要新的 Compose 页面、新标签页、新卡片段落类型 | ❌ 先扩契约，见 §7 |
| 想把学号密码转发给第三方服务 | ❌ 见 [architecture.md](architecture.md) 的红线 |

## 1. 三层结构

每个插件放在 `core/service/<name>/`，三个文件，职责不能混：

```
core/service/<name>/
  XxxProtocol.kt   常量 + URL + 纯解析（无 Android 依赖，可单测）
  XxxApi.kt        HTTP 调用 + 把响应映射成 ServiceResult
  XxxService.kt    业务编排 + 实现 CampusService 与能力接口
```

`ExampleProtocol` / `ExampleApi` / `ExampleService` 就是这套结构的样板，直接读它们。

**不要**把 HTTP 细节写进 Service，也**不要**把业务判断写进 Api。
协议常量集中放在 Protocol 的一个 `object` 里——那是唯一事实源。

## 2. Protocol：常量与纯解析

```kotlin
object MyProtocol {
    const val BASE_URL = "https://api.example.edu"
    const val OVERVIEW_PATH = "/v1/overview"

    data class Overview(val name: String, val balance: String)

    fun parseOverview(body: String): ServiceResult<Overview> {
        val json = runCatching { JSONObject(body) }
            .getOrElse { return ServiceResult.NetworkError("响应不是合法 JSON") }
        val code = json.optInt("code", -1)
        if (code != 0) {
            return ServiceResult.BusinessError(code, json.optString("msg", "未知错误"))
        }
        // …字段映射
        return ServiceResult.Ok(Overview(name, balance))
    }
}
```

解析函数只做「字符串 → 数据或带类型的错误」，不碰网络、不碰 Android。
这样它能被普通 JVM 单测直接覆盖，不需要 Robolectric。

## 3. Api：网络与错误分类

```kotlin
class MyApi(
    private val http: HttpBridge,
    private val baseUrl: String = MyProtocol.BASE_URL
) {
    suspend fun overview(token: String?): ServiceResult<MyProtocol.Overview> {
        val headers = buildMap {
            put("Accept", "application/json")
            token?.let { put("Authorization", "Bearer $it") }
        }
        val response = try {
            http.request("GET", baseUrl + MyProtocol.OVERVIEW_PATH, headers = headers)
        } catch (e: IOException) {
            return ServiceResult.NetworkError(e.message ?: "网络不可用")
        }
        return when {
            response.status == -1 -> ServiceResult.NetworkError("网络不可用")
            response.status == 401 || response.status == 403 ->
                ServiceResult.Unauthorized("登录已失效，请重新登录")
            response.status !in 200..299 ->
                ServiceResult.NetworkError("HTTP ${response.status}", response.status)
            else -> MyProtocol.parseOverview(response.data)
        }
    }
}
```

**错误必须分类**，这是硬要求：

| 情况 | 返回 |
|---|---|
| 超时、DNS 失败、连接被拒 | `NetworkError` |
| HTTP 401/403，或业务层判定 token 失效 | `Unauthorized` |
| HTTP 200 但业务 code ≠ 0 | `BusinessError(code, msg)` |

统一返回 `null` 或空列表会让 UI 无法区分「没数据」和「没登录」，这是明确禁止的写法。

## 4. Service：实现契约

```kotlin
class MyService(private val storage: StorageBridge?) :
    CampusService, ConfigurablePlugin, GlanceSource, DetailSource {

    companion object {
        const val ID = "cn.edu.example.my"      // 全局唯一，同时是 SecureStorage 命名空间
        private const val KEY_TOKEN = "token"
    }

    override val id = ID
    override val displayName = "我的服务"
    override val category = PluginCategory.UTILITY

    override val manifest = PluginManifest(
        id = ID,
        name = displayName,
        description = "一句话说明",
        iconGlyph = "我",                       // 单字
        accent = PluginAccent.NEUTRAL,          // 同屏最多 4 个彩色
        cards = listOf(
            CardSpec(
                id = "my_overview",             // 布局持久化的键，改了就丢用户布局
                title = "我的概览",
                iconGlyph = "我",
                accent = PluginAccent.NEUTRAL,
                defaultSize = BentoCardSize.EXPANDED_2X1
            )
        )
    )
    // …见下方各接口
}
```

### 4.1 凭据

一律走 `SecureStorage.forPlugin(id)`，也就是构造时传进来的 `storage`：

```kotlin
private fun token(): String? = storage?.getItem(KEY_TOKEN)
```

禁止裸 `SharedPreferences`、禁止硬编码、禁止写进日志。

### 4.2 ConfigurablePlugin：登录表单

宿主按你声明的 `ConfigGroup` 渲染表单，九种字段类型：
`TEXT` / `PHONE` / `PASSWORD` / `SELECT` / `SEARCH_SELECT` / `CAPTCHA_IMAGE` / `SMS_CODE` / `BLE_DEVICE` / `SWITCH`。

```kotlin
override val configGroups = listOf(
    ConfigGroup(
        id = GROUP_ACCOUNT,
        label = "账号登录",
        fields = listOf(
            ConfigField("account", "学号", ConfigFieldType.TEXT),
            ConfigField("password", "密码", ConfigFieldType.PASSWORD)
        ),
        hint = "凭据只保存在本机加密存储里。"
    )
)

override fun isConfigured(groupId: String) = token() != null
override fun prefill(groupId: String) = mapOf("account" to (storage?.getItem("account") ?: ""))
override suspend fun submit(groupId: String, values: Map<String, String>): ServiceResult<String>
override suspend fun logout() { storage?.removeItem(KEY_TOKEN) }
```

如果服务有图形验证码 / 短信码 / 学校搜索，覆盖对应方法：

```kotlin
override suspend fun loadCaptcha(groupId: String, values: Map<String, String>) =
    api.captcha(values["account"].orEmpty())      // 返回 ByteArray

override suspend fun requestSmsCode(groupId: String, values: Map<String, String>) =
    api.sendSms(values["phone"].orEmpty())        // 返回冷却秒数

override suspend fun searchOptions(groupId: String, fieldKey: String, query: String) =
    api.searchSchools(query)                      // 返回 List<ConfigOption>
```

这些方法有默认实现（返回「该服务不支持此操作」），不需要就别覆盖。

### 4.2.1 复用字段前先读宿主的渲染代码

字段类型是通用契约，但**宿主渲染它时可能写死了别的服务的文案**。当前已知：

| 复用的东西 | 写死的假设 | 位置 |
|---|---|---|
| `ConfigFieldType.BLE_DEVICE` | 全部文案都是水表：「扫描附近水表」「选择水表」「需要蓝牙权限才能扫描水表」 | `ConfigFormRenderer.kt` |

接非水表设备（电表、门禁、体脂秤）时，照用这个字段会让界面显示「扫描附近水表」——
用户会以为插件写坏了。两种处理方式：

- **插件侧规避**：改用 `TEXT` 让用户手填 MAC 地址，牺牲体验换取文案正确；
- **列为宿主待改项**：照常用，但在交付说明里写明这是宿主文案，需要宿主把措辞改成通用
  （例如按 `field.label` 拼接「扫描附近${field.label}」）。

不要默默沿用。复用任何宿主组件（`ConfigFormRenderer` / `SectionRenderers` / `GlanceCard`）
之前，先读它的实现，确认文案与默认值对你的服务是否成立。

### 4.3 GlanceSource：首页卡片

`glance(cardId)` 必须**不抛异常**，失败用 `CardState.ERROR` 表达：

```kotlin
override suspend fun glance(cardId: String): GlanceModel {
    if (!isConfigured(GROUP_ACCOUNT)) {
        return GlanceModel(
            cardId = cardId,
            state = CardState.UNBOUND,
            title = "我的服务",
            sub = "未登录",
            tapTarget = GlanceTarget.Login
        )
    }
    return when (val result = api.overview(token())) {
        is ServiceResult.Ok -> GlanceModel(
            cardId = cardId,
            state = CardState.IDLE,
            title = result.value.name,
            serviceLabel = displayName,
            readout = result.value.balance,
            readoutUnit = "元",
            tapTarget = GlanceTarget.Page("overview")
        )
        is ServiceResult.Unauthorized -> GlanceModel(
            cardId = cardId, state = CardState.UNBOUND, title = displayName,
            sub = "登录已失效", tapTarget = GlanceTarget.Login
        )
        else -> GlanceModel(
            cardId = cardId, state = CardState.ERROR, title = displayName,
            sub = "暂时取不到数据", tapTarget = GlanceTarget.Page("overview")
        )
    }
}
```

**六个状态是固定的**：`IDLE` / `RUNNING` / `DONE` / `EMPTY` / `ERROR` / `UNBOUND`。
不要发明第七个，也不要用 `RUNNING` 表示「加载中」——它表示「正在持续进行」（出水、跑任务）。

**槽位由宿主决定**：你填了 `details`，1×1 档位也不会显示（`GlanceLayoutRules` 里
1×1 只有图标 + 标题 + 读数 + 一行副说明）。内容放不下就整块隐藏，不会压缩换行，
所以不要试图用超长文案「挤」进小卡片。

### 4.4 DetailSource：详情页

```kotlin
override suspend fun detailPages(): ServiceResult<List<DetailPage>> {
    if (!isConfigured(GROUP_ACCOUNT)) return ServiceResult.Unauthorized("请先登录")
    return api.detail(token()).map { data ->
        listOf(
            DetailPage(
                id = "overview",
                title = "概览",
                navLabel = "概览",
                navIcon = DetailNavIcon.OVERVIEW,
                sections = listOf(
                    DetailSection.Kpi(listOf(KpiItem("余额", data.balance, "元"))),
                    DetailSection.Records(
                        title = "最近记录",
                        emptyText = "还没有记录",
                        rows = data.rows.map { RowItem(it.id, it.title, sub = it.time) }
                    )
                ),
                actions = listOf(PageAction("刷新", "my.refresh")),
                note = "数据来自学校官方接口。"
            )
        )
    }
}

override suspend fun onDetailCommand(
    commandId: String, rowId: String?, input: String?
): ServiceResult<String> = when (commandId) {
    "my.refresh" -> ServiceResult.Ok("已刷新")
    else -> ServiceResult.BusinessError(-1, "不支持的命令：$commandId")
}
```

段落只有四种：`Kpi` / `Chart` / `Records` / `Form`。需要第五种时，先改契约（§7）。

`RowActionKind` 决定交互：`COMMAND` 直接执行、`CONFIRM` 先确认、`PROMPT_TEXT` 先收集一行输入
（输入通过 `input` 参数回传）。

## 5. 注册

在 `CampusApplication.onCreate` 里注册，宿主会自动发现它的卡片与能力：

```kotlin
ServiceRegistry.registerAll(
    ExampleService(storage = secureStorage.forPlugin(ExampleService.ID)),
    MyService(storage = secureStorage.forPlugin(MyService.ID))
)
```

`ServiceRegistry` 是进程内单例，没有 `unregister`——服务是编译期内置的，
生命周期与进程相同。

## 6. 测试

每个插件至少三个用例，用假 `HttpBridge`，不联网：

```kotlin
private class FakeHttp(private val response: BridgeHttpResponse) : HttpBridge {
    override suspend fun request(
        method: String, url: String, body: String?, headers: Map<String, String>?, timeoutSeconds: Long?
    ) = response
}

@Test
fun `成功时映射出数据`() = runTest {
    val api = MyApi(FakeHttp(BridgeHttpResponse(200, data = """{"code":0,...}""")))
    assertTrue(api.overview("t") is ServiceResult.Ok)
}

@Test
fun `401 映射为 Unauthorized`() = runTest {
    val api = MyApi(FakeHttp(BridgeHttpResponse(401)))
    assertTrue(api.overview("t") is ServiceResult.Unauthorized)
}

@Test
fun `业务码非零映射为 BusinessError`() = runTest {
    val api = MyApi(FakeHttp(BridgeHttpResponse(200, data = """{"code":7,"msg":"x"}""")))
    assertEquals(7, (api.overview("t") as ServiceResult.BusinessError).code)
}
```

存储用内存实现（见 `ExampleServiceTest` 里的 `FakeStorage`）。

跑测试：

```bash
./gradlew :app:testDebugUnitTest
```

## 7. 宿主限制：这些事插件做不到

| 你想要 | 现状 | 怎么办 |
|---|---|---|
| 卡片上的命令按钮（`GlanceTarget.Command`） | 公开壳的 `HomeScreen.dispatch` 里是空分支 | 用 `GlanceTarget.Page` 进详情，在详情里放 `PageAction` |
| 钱包页（`GlanceTarget.Wallet`） | 公开壳没有钱包页 | 同上 |
| 新的 `live` 卡片（宿主状态机驱动） | 需要宿主持有会话状态机 | 属于宿主开发，不是插件 |
| 新的 `DetailSection` 类型 | 契约只有四种 | 先扩 `core/plugin/Detail.kt` + `ui/detail/SectionRenderers.kt` |
| 新的标签页 / 独立页面 | 插件不提供 Compose | 不要做，改契约 |
| 桌面微件 / 扫码 / 通知 / 蓝牙 | 公开壳未包含 | 属于宿主能力，接法与四栏清单见 §8 |
| 动态加载（DexClassLoader、插件市场） | 零实现 | 不要假装存在 |

`CardSpec.live = true` 是留给宿主会话状态机的（水控、积分任务）。普通插件**不要**设 `live = true`
——那样宿主会去找一个不存在的状态机，卡片永远显示占位。

## 8. 宿主能力扩展

有些需求**必须改宿主**，插件侧无论怎么写都做不到。这不是「暂时不支持」，
而是能力位置决定的：插件只产出数据，任何「App 之外的表面」都要宿主自己接线。

先明确两条前提，后面所有小节都建立在这上面：

- **插件是编译期内置的**。`ServiceRegistry` 没有 `unregister`，没有 DexClassLoader、
  没有插件市场、没有热更新。所谓「加一个插件」= 改代码 + 重新编译 + 重新安装 APK。
- **微件不是插件自带能力**。`CardSpec` 只描述首页网格里的一张卡；桌面微件是独立的
  `GlanceAppWidget`，由宿主注册、宿主投影、宿主刷新。插件不能声明「我也有个微件」。

下面四项都按「宿主改动清单」的四栏格式写：**新增接口 / 注册 / 权限 / 测试**。
公开壳不包含这四项的任何实现，私有完整版才有；照抄之前先确认你要的能力确实存在。

### 8.1 桌面微件

微件是会话状态机的**纯观察者**，不持有业务逻辑，也不发网络请求：

1. 声明一个 `GlanceAppWidget` + 对应的 `GlanceAppWidgetReceiver`，在 manifest 里
   `android:exported="true"` 并挂 `APPWIDGET_UPDATE` intent-filter；
2. 微件只读宿主写进 Glance state 的字段（当前是否出水、水量、状态文案、按钮文案），
   自己不调 `startWater`；
3. 点击按钮 → `ActionCallback` → 调**已有的会话协调器**（`core/session/` 下那个唯一状态机），
   不要新写第二套开停逻辑；
4. 状态变化时由协调器的观察者调 `WidgetSync.publishWater(...)` / `publishShower(...)`
   写 state 并刷新，微件不轮询。

新增微件 = 新增一个**表面**，不是新增一个插件。想让某个插件也有微件，先问：
它有没有持续状态？如果只是「查一次数据」，微件的价值很低（Android 上微件刷新频率受限），
卡片已经够了。

| 栏 | 内容 |
|---|---|
| 新增接口 | 无（复用 `DrinkingWaterSessionCoordinator` / `ShowerWaterSessionCoordinator` 与 `WidgetSync`） |
| 注册 | `AndroidManifest.xml` 加 `<receiver>`；`res/xml/*_widget_info.xml`；新类 `XxxGlanceWidget` + `XxxWidgetReceiver` |
| 权限 | 无额外运行时权限 |
| 测试 | 纯函数部分（state → 展示文案）可 JVM 单测；真实上桌与点击必须真机走 |

### 8.2 通知

两类通知，接入点不同：

- **前台服务通知**（`ScoreTaskService` 这类长任务）：服务 `startForeground(id, notification)`，
  manifest 声明 `FOREGROUND_SERVICE` + 对应的类型权限（如 `FOREGROUND_SERVICE_DATA_SYNC`）。
  Android 14+ 必须声明服务类型，缺了会直接崩。
- **实况 / 常驻通知**（`WaterLiveUpdateNotifier`）：订阅会话状态机，出水时发 ongoing 通知。
  API 36 用 `requestPromotedOngoing`，低版本降级成普通常驻通知。

`POST_NOTIFICATIONS` 是运行时权限（Android 13+）。**没授权必须静默降级**：
出水流水照常进行，只是不弹通知——不能因为通知发不出去就阻断业务。

注意：Promoted ongoing 在部分国产 ROM（如 OriginOS）只产出通知栏置顶卡片，
**不进原子岛**。这是系统限制，不是代码 bug，不要在 Release Notes 里承诺原子岛。

| 栏 | 内容 |
|---|---|
| 新增接口 | 无（`WaterLiveUpdateNotifier` 是观察者，订阅已有状态机） |
| 注册 | 若新增前台服务：manifest `<service android:foregroundServiceType="...">`；通知渠道在首次 `start()` 里创建 |
| 权限 | `POST_NOTIFICATIONS`（运行时，未授予静默降级）；前台服务按类型加 `FOREGROUND_SERVICE_*` |
| 测试 | 渠道创建、文案映射可单测；通知是否真的出现、是否被 ROM 折叠必须真机走 |

### 8.3 扫码

宿主侧接线是 `QrScannerScreen`：CameraX 预览 + ZXing 解码，识别后回调字符串，
调用方自己决定怎么用（乐校通把二维码内容交给 `applyYxtMachineQr`）。

**插件不能自己开相机。** 插件没有 Activity，也没有 CameraX 依赖；它能做的只是
在详情页放一个 `PageAction`，由宿主导航到扫码页，扫完把字符串回传。

`CAMERA` 是运行时权限，宿主在进入扫码页时请求；拒绝时要有可读的降级文案，
不要白屏或闪退。

| 栏 | 内容 |
|---|---|
| 新增接口 | 若插件要接收扫码结果：`DetailSource.onDetailCommand` 复用，或新增宿主回调（需改契约） |
| 注册 | 无（扫码页是现成的路由） |
| 权限 | `CAMERA`（运行时） |
| 测试 | 解析逻辑（二维码字符串 → 设备参数）可纯函数单测；真机扫码必须用户走 |

### 8.4 蓝牙

`BleBridge` 是**接口与实现分离**的：接口在 `core/bridge/BridgeContracts.kt`，
实现在 `BleBridgeImpl.kt`。插件只依赖接口，拿到的是一次注入的实例。

为什么插件不能自己扫：

- 蓝牙扫描要 `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` 运行时权限，权限请求必须由
  Activity 发起，插件没有 Activity；
- 扫描、连接、GATT 读写要串行化，多插件各扫各的会互相打架；
- 广播水表的帧格式是设备协议，属于服务自己的 `XxxBleProtocol`，不是宿主知识。

所以边界是：**宿主负责扫描与连接**（`BleBridge`），**插件负责帧编解码**（`XxxBleProtocol`）。
接新 BLE 设备时，插件侧只加一个 Protocol 文件；只有「需要新的扫描交互 UI」时才动宿主。

BLE 帧未经真机 HCI 日志校准前，不要声称能开阀——占位帧（开/关相同）在真机上
只会静默失败，看起来像「点了没反应」。

| 栏 | 内容 |
|---|---|
| 新增接口 | 通常无（复用 `BleBridge`）；需要新交互时改 `ConfigFieldType.BLE_DEVICE` 的渲染 |
| 注册 | manifest 的 `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT`；`usesPermissionFlags="neverForLocation"` |
| 权限 | `BLUETOOTH_SCAN`、`BLUETOOTH_CONNECT`（运行时，Android 12+） |
| 测试 | 帧编解码 `XxxBleProtocol` 纯函数单测；扫描授权与真实连接必须真机走 |

### 8.5 判断标准：插件还是宿主？

| 你想要的 | 归属 | 依据 |
|---|---|---|
| 多展示一种数据 | 插件 | 加 `GlanceSource` / `DetailSource` 即可 |
| 多一个首页卡片 | 插件 | `manifest.cards` 加一条 |
| 卡片按钮直接触发动作 | 宿主 | `GlanceTarget.Command` 由 `HomeScreen` 分发 |
| 桌面微件 | 宿主 | 独立 `GlanceAppWidget` |
| 通知 / 前台服务 | 宿主 | 需要 Service + 权限 |
| 扫码 / 相机 | 宿主 | 需要 Activity + 权限 |
| 蓝牙扫描连接 | 宿主扫描，插件编解码 | 权限在 Activity，帧格式在插件 |
| 持续计时 / 会话状态机 | 宿主 | `core/session/` 唯一状态机 |
| 新标签页 / 新页面 | 宿主（先改契约） | 插件不提供 Compose |

判据一句话：**需要 Activity、Service、Receiver、权限或新 Compose 的，都是宿主改动。**
插件能做的只有「给宿主数据」。

## 9. 检查表

提交前逐条确认：

- [ ] 三层分离：Protocol 无 Android 依赖，Api 只做 HTTP + 错误映射，Service 只做编排
- [ ] 协议常量集中在一个 `object` 里
- [ ] 复用宿主字段/组件前读过其渲染代码；写死的文案已规避或列为宿主待改项
- [ ] 凭据走 `SecureStorage.forPlugin(id)`，没有裸 SharedPreferences
- [ ] 失败区分 `NetworkError` / `Unauthorized` / `BusinessError`，没有统一 `null`
- [ ] `glance()` 不抛异常；有登录页时未登录返回 `UNBOUND` + `GlanceTarget.Login`，
      没有登录页时用 `ERROR`（没有登录页可去时，`Login` 是死胡同）
- [ ] 没有伪造数据兜底（宁可 `UNBOUND` / 空态，也不要假数字）
- [ ] 三个单测：成功 / 未授权 / 业务错误
- [ ] `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` 绿
- [ ] 没有往 `DashboardViewModel` 里加业务函数
