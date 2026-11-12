# 架构与边界

这份文档说清楚三件事：宿主负责什么、插件负责什么、哪些事不要做。

## 分层

```
ui/                    Compose 界面（只认契约，不认具体服务）
  home/                首页：便当盒网格 + 卡片
  login/               登录/配置页：按 ConfigGroup 渲染
  detail/              详情页：按 DetailPage 渲染
  plugins/             插件页：显隐与顺序
core/plugin/           契约：PluginManifest / CardSpec / GlanceModel / ConfigGroup / DetailPage
core/service/          CampusService + ServiceResult + ServiceRegistry
core/service/<name>/   具体插件（Protocol / Api / Service 三层）
core/bridge/           HttpBridge / StorageBridge / ToastBridge 规范与实现
core/security/         SecureStorage
core/settings/         DataStore + 便当盒布局编解码
```

依赖方向只有一个：`ui → core/plugin → core/service`。反向依赖（core 引用 ui）是错的。

## 数据流

```
CampusApplication.onCreate
  └─ ServiceRegistry.registerAll(...)            注册内置服务

DashboardViewModel.init
  ├─ settings.cardLayout → CardLayoutCodec.resolve(saved, allCardSpecs())
  │                        布局持久化与卡片声明对齐（新卡追加、旧卡丢弃、尺寸纠正）
  ├─ refreshConfiguredGroups()                    汇总 ConfigurablePlugin.isConfigured
  └─ refreshSourceGlances()                       GlanceSource.glance(cardId) 逐卡拉取

HomeScreen
  ├─ 已登录卡 → InteractiveBentoSection → GlanceCard
  ├─ 未登录卡 → UnboundStrip（点击进登录页）
  └─ dispatch(cardId, target)                     Page → 详情页；Login → 登录页
```

宿主不认识任何具体服务。新增服务只改自己的 manifest 和实现，
首页、插件页、登录页、详情页全部自动适配。

## 谁负责什么

| 职责 | 归属 |
|---|---|
| 卡片档位、槽位可见性、字体缩放 | 宿主（`GlanceLayoutRules`） |
| 布局持久化与迁移 | 宿主（`CardLayoutCodec`） |
| 表单渲染、验证码/短信码/搜索的交互 | 宿主（`ConfigFormRenderer`） |
| 详情页段落渲染、命令分发 | 宿主（`SectionRenderers` / `DetailScreen`） |
| 取数据、解析、错误分类 | 插件 |
| 凭据存储 | 宿主提供 `SecureStorage`，插件只用 `forPlugin(id)` |
| 会话状态机（持续出水计时等） | 宿主（私有完整版） |

**插件只提供数据，不写 Compose。** 这条是硬约束：一旦插件自带 UI，
「新增服务不增加屏幕数」这个前提就没了，宿主的统一视觉也保不住。

## 契约清单

新增插件时你会碰到的全部类型：

| 类型 | 位置 | 作用 |
|---|---|---|
| `CampusService` | `core/service/CampusService.kt` | 服务身份：id / displayName / category / manifest |
| `ServiceResult` | 同上 | `Ok` / `NetworkError` / `Unauthorized` / `BusinessError` |
| `ConfigurablePlugin` | `core/plugin/Capabilities.kt` | 登录/配置表单 |
| `GlanceSource` | 同上 | 首页卡片数据 |
| `DetailSource` | 同上 | 详情页数据与命令 |
| `PluginManifest` / `CardSpec` | `core/plugin/PluginManifest.kt` | 服务与卡片声明 |
| `BentoCardSize` | `core/plugin/BentoCardSize.kt` | 四档：1×1 / 2×1 / 1×2 / 2×2 |
| `GlanceModel` / `CardState` / `GlanceTarget` | `core/plugin/Glance.kt` | 卡片内容与点击意图 |
| `ConfigGroup` / `ConfigField` | `core/plugin/Config.kt` | 表单声明（九种字段） |
| `DetailPage` / `DetailSection` | `core/plugin/Detail.kt` | 详情页（四种段落） |

`ServiceResult` 有 `map` / `flatMap`，用来在成功链上做转换，失败原样透传并保留错误类型。

## 明确不做的事

这些不是「还没做」，是**不该做**：

- **不往 `DashboardViewModel` 加业务函数**。服务多了以后它会变成第二个上帝对象。
  新能力加在服务里，宿主只做状态编排。
- **不伪造数据兜底**。取不到就 `UNBOUND` 或空态。假数字比空白更有害——
  用户会以为服务通了。
- **不统一返回 `null`**。`catch (e: Exception) { null }` 让三种完全不同的失败
  在 UI 上长得一模一样，无法引导用户（重试？重登？联系管理员？）。
- **不声称有动态插件**。没有 DexClassLoader、没有插件市场、没有热更新。
  `PluginSource.USER` 是预留枚举，零实现。
- **不给插件开 Compose 口子**。见上。
- **不引入 DI 框架**。`ServiceRegistry` 就够；引入 Hilt/Koin 会增加编译时间和
  认知成本，收益不抵。

## 安全红线

- 凭据只进 `SecureStorage.forPlugin(id)`，禁止裸 `SharedPreferences`、禁止硬编码、
  禁止写日志。
- **不要把用户凭据转发给第三方**。如果需要中转才能跑通某个服务，
  先停下来问用户——这类集成需要明确授权，不是技术选择。
- 全站只允许 HTTPS。不要加明文 HTTP 放行配置。
- 诊断日志默认关闭，开启时脱敏（不打 token、不打手机号、不打设备号）。

## 扩展契约的正确姿势

需要新东西时，改契约而不是绕过它：

1. 新的详情段落类型 → `DetailSection` 加子类 + `SectionRenderers` 加分支 + 单测
2. 新的表单字段类型 → `ConfigFieldType` 加枚举 + `ConfigFormRenderer` 加分支
3. 新的卡片点击目标 → `GlanceTarget` 加子类 + 每个 `when` 加分支（编译器会逼你改全）
4. 新的卡片档位 → `BentoCardSize` + `GlanceLayoutRules` + 网格布局 + 单测

第 3 条要注意：`GlanceTarget` 是 sealed，加子类会让所有 `when` 编译失败。
这是好事——它保证没有遗漏的分发点。公开壳里 `Wallet` 和 `Command` 就是空分支，
因为公开壳没有对应能力；私有完整版才有。

**复用契约时先读宿主的渲染实现。** 字段类型和段落类型是通用契约，但渲染它们的
`ConfigFormRenderer` / `SectionRenderers` / `GlanceCard` 可能写死了某个具体服务的措辞。
当前已知一处：`ConfigFieldType.BLE_DEVICE` 的文案全部是水表（「扫描附近水表」
「选择水表」），接电表 / 门禁等设备时会显示错字。插件的正确做法见
[plugin-development.md](plugin-development.md) §4.2.1——要么插件侧规避，要么把
它列为宿主待改项，不能默默沿用。

## 版本与发布

- 日常修改不升版本。发版时显式改 `versionName` / `versionCode`（严格 +1）。
- 构建不自动升版本，APK 文件名不代表包内版本。
- 公开壳没有签名配置，贡献者本地构建即未签名 APK。
