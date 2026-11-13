# CampusHub

一个 Android 校园服务聚合客户端的**宿主壳**：Bento 卡片网格、统一的登录/配置页、
统一的详情页渲染、插件契约层。Kotlin + Jetpack Compose + Material 3。

这个仓库**只包含宿主与契约**，外加一个完全离线的示例插件（`ExampleService`）。
它演示一个插件从「未登录 → 登录 → 卡片 → 详情 → 命令」的完整接线，
所有数字都来自本地存储，不联网、不伪造任何真实校园数据。

> **关于完整版 APK**
> 项目作者在 Releases 里提供签名 APK。那个二进制**包含未公开的校园服务适配器**
> （真实网关地址、签名算法、会话状态机），与本仓库源码不等价。
> 本仓库的源码可以独立编译、独立运行，但只有示例插件可用。
>
> 完整版的更新清单是仓库根目录的 `update.json`，安装包放在 `releases/`。
> 客户端从这个文件读版本号与校验值，下载后比对 SHA-256 与签名证书才安装。
> **这个仓库的源码构建出的包签名不同，不能通过那条更新链路升级。**

## 快速开始

环境：JDK 17、Android SDK 36（`compileSdk 36` / `minSdk 26` / `targetSdk 35`）。

```bash
export ANDROID_HOME=/path/to/android-sdk      # 或写进 local.properties
./gradlew :app:compileDebugKotlin             # 先编译
./gradlew :app:testDebugUnitTest              # 30 个单测，不联网
./gradlew :app:assembleDebug                  # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

启动后应该看到「示例概览」卡片处于未登录状态。点它 → 随便填一个学号 →
回到首页，卡片变成可点状态 → 点进详情 → 点「演示打卡 +1」，数字会 +1。

## 写一个插件

一个插件就是一个实现 `CampusService` 的 Kotlin 类，按需再实现三个能力接口：

| 接口 | 什么时候实现 |
|---|---|
| `ConfigurablePlugin` | 需要登录 / 配置表单（宿主渲染，你只声明字段） |
| `GlanceSource` | 需要在首页卡片上展示数据（`CardSpec.live == false` 时必须实现） |
| `DetailSource` | 需要详情页 |

然后把它注册进去，就完事了——首页卡片、插件页条目、登录页、详情页都由宿主生成。

```kotlin
// CampusApplication.onCreate
ServiceRegistry.registerAll(
    MyService(storage = secureStorage.forPlugin(MyService.ID))
)
```

完整的分层、字段、错误处理和测试要求见 [`docs/plugin-development.md`](docs/plugin-development.md)，
宿主的能力边界与禁止事项见 [`docs/architecture.md`](docs/architecture.md)。

### 用 AI Agent 开发

仓库带一个官方 Skill：`.agents/skills/campushub-plugin-developer/`
（`.claude/skills/` 下是指向它的软链）。用 Claude Code 打开本仓后直接说
「帮我加一个 XX 插件」，它会按这个 Skill 走完七步：问清数据源与授权、
判断是不是宿主能力、建三层文件、分类错误、存凭据、注册、写测试、跑编译。

Skill 只讲工作流，契约事实仍在 `docs/` 里——避免两处维护同一份说明。


## 目录

```
app/src/main/java/com/campushub/app/
  core/plugin/        插件契约：manifest / 卡片 / glance / 配置 / 详情
  core/service/       CampusService、ServiceResult、ServiceRegistry
  core/service/example/  离线示例插件（Protocol / Api / Service 三层）
  core/bridge/        HttpBridge / StorageBridge / ToastBridge 规范与实现
  core/security/      SecureStorage（按 pluginId 分命名空间）
  core/settings/      DataStore 设置 + 便当盒布局编解码
  ui/                 Compose 界面：首页便当盒、登录、详情、插件页、主题
```

## 限制（说清楚，别猜）

- **插件是编译期内置的**。没有 DexClassLoader、没有热安装、没有插件市场；
  `PluginSource.USER` 只是预留枚举，当前没有任何加载器。
- **插件页只做显隐与顺序**，不是真正的装卸。
- **`GlanceTarget.Wallet` 与 `GlanceTarget.Command` 在公开壳里没有实现**，
  卡片上声明它们不会发生任何事（`HomeScreen.dispatch` 里是空分支）。
  水控会话、桌面微件、扫码、通知这些能力属于私有完整版。
- 公开壳不包含任何第三方校园服务实现，也不包含它们的协议常量。
- **`ConfigFieldType.BLE_DEVICE` 有渲染但没有扫描器**：`core/bridge/` 只有 `BleBridge` 接口，
  没有任何实现，且文案写死为水表。接非水表设备前先读
  [`docs/plugin-development.md`](docs/plugin-development.md) §4.2.1。

## 完整版 APK

Releases 里的 `releases/app-<X.Y.Z>.apk` 是作者在私有仓库构建的签名包，
包含真实校园服务适配器。安装：

```bash
adb install -r releases/app-1.2.7.apk
```

文件名与 `update.json` 的 `versionName` 一致；应用内「检查更新」下载的也是这个文件。

- 包名 `com.campushub.app`（与公开壳的 `com.campushub.shell` **不同**，两者可并存安装）
- `minSdk 26`（Android 8.0）/ `targetSdk 35`
- 从旧版本升级可直接覆盖（同一签名）；降级会失败，需先卸载

这个二进制与本仓库源码**不等价**：它多出真实网关地址、签名算法、会话状态机、
支付宝 SDK 等未公开实现。本仓库的源码可以独立编译运行，但只有示例插件可用。

## 许可证

通用宿主代码采用 [Apache License 2.0](LICENSE)。第三方依赖与其许可证见
[`THIRD-PARTY-NOTICES.md`](THIRD-PARTY-NOTICES.md)。

本项目与任何学校、任何校园服务提供方无隶属关系。自行接入服务时，
请先确认你有权访问该接口，并且不要把用户凭据转发给第三方。
