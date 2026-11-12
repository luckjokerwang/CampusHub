# AGENTS.md — CampusHub 公开壳

本文件是**公开壳**的事实来源，只放硬约束与索引。私有完整仓有另一份，
内容不同：那个仓包含真实校园服务实现，本仓不含。

## 项目是什么

Android 校园服务聚合客户端的宿主壳。Kotlin + Jetpack Compose + Material 3。
Bento 卡片网格、统一登录/配置页、统一详情页渲染、插件契约层。

**本仓只有宿主 + 一个离线示例插件**（`ExampleService`）。真实校园服务适配器
不在本仓，只在 Releases 的签名 APK 里。

## 状态矩阵

### ✅ 真实可用

| 能力 | 入口 |
|---|---|
| Bento 卡片网格（拖拽重排 / 边缘 resize / 布局持久化） | `ui/home/bento/BentoGrid.kt`、`core/settings/CardLayoutCodec.kt` |
| 统一登录/配置页（九种字段） | `ui/login/LoginScreen.kt` + `ui/login/ConfigFormRenderer.kt` |
| 统一详情页（KPI / 图表 / 记录 / 表单四种段落） | `ui/detail/DetailScreen.kt` + `ui/detail/SectionRenderers.kt` |
| 插件页（已启用 / 可添加 + 卡片显隐） | `ui/plugins/PluginsScreen.kt` |
| 主题（深浅色 + 揭示转场） | `ui/theme/`、`ui/app/ThemeReveal.kt` |
| 离线示例插件 | `core/service/example/` |
| 单元测试 27 例 / 3 文件 | `app/src/test/` |

> `ConfigFieldType.BLE_DEVICE` 的**表单渲染**是真实可用的，但公开壳里没有任何扫描器
> 实现（`core/bridge/` 只有 `BleBridge` 接口），且文案写死为水表。接非水表设备前先读
> `docs/plugin-development.md` §4.2.1。

### ❌ 未实现（不要假装有）

| 模块 | 真相 |
|---|---|
| 动态插件 / 热安装 / 插件市场 | 零实现。`PluginSource.USER` 只是预留枚举 |
| 卡片命令按钮（`GlanceTarget.Command`） | `HomeScreen.dispatch` 里是空分支 |
| 钱包页（`GlanceTarget.Wallet`） | 空分支，公开壳没有钱包页 |
| 桌面微件 / QS 磁贴 / 扫码 / 通知 / BLE | 全部未包含，属于私有完整版 |
| 水控会话状态机 / 积分任务引擎 | 未包含，属于私有完整版 |

## 目录

```
app/src/main/java/com/campushub/app/
  core/plugin/       契约：manifest / 卡片 / glance / 配置 / 详情 / 尺寸
  core/service/      CampusService / ServiceResult / ServiceRegistry
  core/service/example/  离线示例（Protocol / Api / Service）
  core/bridge/       HttpBridge / StorageBridge / ToastBridge
  core/security/     SecureStorage
  core/settings/     AppSettingsRepository + CardLayoutCodec
  ui/                界面层
app/src/test/        JVM 单测
docs/                plugin-development.md / architecture.md
```

## 构建与验证

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:compileDebugKotlin      # 改完先跑这个
./gradlew :app:testDebugUnitTest       # 27 例，不联网
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

需要 JDK 17。`compileSdk 36` / `minSdk 26` / `targetSdk 35`，
AGP 8.9.1 / Gradle 9.5.0 / Kotlin 2.0.21。升级任一版本前先验证编译。

**测试基线**：3 文件 / 27 例 / 0 failures / 0 skipped。
- `GlanceLayoutRulesTest` 7（槽位可见性与明细上限）
- `CardLayoutCodecTest` 10（编解码、旧版迁移、尺寸纠正、首位卡升级）
- `ExampleServiceTest` 10（未登录 / 登录 / 命令 / 无存储 / 未知卡）

## 红线

- **不要**往 `DashboardViewModel` 加业务函数。新能力加在服务里。
- **不要**让插件写 Compose。插件只提供数据。
- **不要**用 `null` 表示失败。区分 `NetworkError` / `Unauthorized` / `BusinessError`。
- **不要**伪造数据兜底。宁可 `UNBOUND` 或空态。
- **不要**声称有动态卸载或热安装——所有服务都是编译期内置的。
- **不要**把用户凭据转发给第三方。这类集成需要用户明确授权。
- **不要**新增依赖，除非说明现有依赖为什么不够。
- **不要**提交 `local.properties`、`*.jks`、`*.keystore`、APK。
- **不要**用 `git add -A` / `git add .`。
- **不要**在没跑编译的情况下声称改动完成。

## 新增一个插件的步骤

1. 在 `core/service/<name>/` 建 `XxxProtocol.kt`（常量 + 纯解析）、`XxxApi.kt`（HTTP + 错误映射）、
   `XxxService.kt`（实现 `CampusService`，按需加 `ConfigurablePlugin` / `GlanceSource` / `DetailSource`）。
2. 在 `CampusApplication.onCreate` 里 `ServiceRegistry.registerAll(...)`。
3. 配三个单测：成功 / 未授权 / 业务错误（假 `HttpBridge` + 内存 `StorageBridge`）。
4. 跑 `:app:compileDebugKotlin :app:testDebugUnitTest`。

细节见 [docs/plugin-development.md](docs/plugin-development.md)，
能力边界见 [docs/architecture.md](docs/architecture.md)。

## 文档索引

| 文档 | 内容 |
|---|---|
| `README.md` | 快速开始、限制说明 |
| `docs/plugin-development.md` | 插件接线手册（三层结构、字段、错误分类、测试、宿主限制） |
| `docs/architecture.md` | 分层、数据流、职责归属、禁止事项、安全红线 |
| `THIRD-PARTY-NOTICES.md` | 依赖与素材许可证 |

同一事实只写一处，其它地方放链接。
