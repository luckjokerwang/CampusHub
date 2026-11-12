---
name: campushub-plugin-developer
description: Use whenever the user wants to add a new campus service to CampusHub, integrate a school or campus API, add a home card, login/config form, or detail page to the app, or asks how to extend the plugin shell. Covers the CampusService contract, the Protocol/Api/Service three-layer structure, error classification, credential storage, and what the host cannot do. Trigger this even when the user only says "加一个 XX 的卡片", "接入学校 XX 服务", "帮我写个插件", or names a school service they want in the app.
---

# CampusHub plugin developer

CampusHub 是一个 Android 宿主壳：它提供 Bento 卡片网格、统一的登录页和详情页，
**插件只提供数据**。你的工作是把一个新校园服务接进这套契约里，而不是写新的界面。

先读这两份仓库文档，它们是事实来源：

- `docs/plugin-development.md` — 接线手册（分层、字段、错误分类、测试）
- `docs/architecture.md` — 分层方向、职责归属、禁止事项、安全红线

本 Skill 只讲**工作流**：按什么顺序做、在哪一步停下来问用户、做完怎么自证。
不要在这里维护第二份契约说明。

两条前提，贯穿全流程：

- **插件是编译期内置的**。加插件 = 改代码 + 重新编译 + 重装 APK；没有热加载、
  没有动态安装、没有插件市场。用户问「能不能不重装就加上」时，答案是「不能」。
- **微件不是插件自带能力**。`CardSpec` 只描述首页网格里的一张卡；桌面微件是宿主侧的
  独立表面，插件无法声明、无法实现。

## 第一步：先问清楚三件事

在写任何代码之前，确认这三点。缺哪一条都会导致返工。

**1. 数据从哪来，你有权访问吗？**

问清楚：接口地址是什么、鉴权方式（cookie / token / 学号密码）、有没有官方文档或授权。
如果用户只有一个「逆向出来的接口」，那不是不能用，但要明确告知：这属于非官方接口，
可能随时失效，且**绝不能把用户凭据转发给第三方**。如果唯一跑通的路径是「把学号密码发给
某个中转站」，停下来，告诉用户这条红线，让他决定——不要自己判断"应该没问题"。

**2. 这个服务需要哪些能力？**

| 用户的需求 | 实现哪个接口 |
|---|---|
| 需要登录 / 填配置 | `ConfigurablePlugin` |
| 首页要有一张显示数据的卡 | `GlanceSource` |
| 点进去要有页面 | `DetailSource` |
| 只是后台任务，不展示 | 只实现 `CampusService` |

没有登录的服务（比如公开的空教室查询）就不要实现 `ConfigurablePlugin`——
多一个空的登录页是噪音。

**3. 用户想要的是不是宿主还没有的能力？**

有些需求看起来像插件，其实是宿主工作。先判断，再动手：

| 需求 | 归属 |
|---|---|
| 卡片上的按钮直接触发动作 | 宿主（`GlanceTarget.Command` 在公开壳里没有实现） |
| 新的卡片尺寸、新的详情段落类型 | 宿主（要改契约） |
| 持续计时的会话（出水、跑任务） | 宿主状态机 |
| 桌面微件、扫码、通知、蓝牙 | 宿主 |
| 动态加载 / 插件市场 / 热更新 | **不存在**，不要实现，也不要假装有 |

遇到这类需求，先跟用户说清楚归属，再问是「先按现有能力做简化版」还是「扩宿主」。
不要为了让卡片能点就偷偷往 `HomeScreen` 里塞一个私有分支——那正是这套架构要避免的。

判据可以压缩成一句：**需要 Activity、Service、Receiver、运行时权限或新 Compose 的，
都是宿主改动。** 插件能做的只有「给宿主数据」。

确认是宿主改动后，**不要伪造**。典型错误是照着插件模板写一个
`MyWidget : GlanceAppWidget` 放在 `core/service/<name>/` 里，然后说「微件做好了」——
它不会被注册、不会上桌、也不会刷新，用户看到的是"做完了但没效果"。
正确的产出是一份宿主改动清单，让用户决定谁来改、什么时候改。

### 宿主改动清单模板

写这份清单时，四栏都要有内容；某一栏确实无事可做就写「无」，不要省略——
省略会让用户以为你漏想了。

```markdown
## 宿主改动：<能力名>（<为什么要改宿主>）

| 栏 | 内容 |
|---|---|
| 新增接口 | <新类型 / 新方法 / 复用的现有接口，写清路径> |
| 注册 | <manifest 组件、路由、ServiceRegistry、Glance 微件 receiver 等> |
| 权限 | <运行时权限 / manifest 权限 / 前台服务类型；无则写「无」> |
| 测试 | <哪些能 JVM 单测，哪些必须真机走；不要写「已测」除非真的跑过> |

### 分步
1. <最小改动的第一步>
2. ...
### 不改的部分
<哪些现有逻辑复用、不要复制（例如"复用 core/session 的唯一状态机，不要写第二套">）
```

具体每项能力的接法（微件 / 通知 / 扫码 / BLE 各自的实现要点与坑）在
`docs/plugin-development.md` §8，**去读那里，不要在本 Skill 里重写一遍**。
Skill 负责让你在正确的时机停下来并产出清单；文档负责事实细节。

清单交付时明确说清：这是**宿主改动**，插件侧代码无法单独完成；并给出「现在只做插件能做的部分」
与「一起改宿主」两种排期，让用户选。

## 第二步：建三个文件

固定放在 `core/service/<name>/`，职责不能混：

```
XxxProtocol.kt   常量 + URL + 纯解析（无 Android 依赖）
XxxApi.kt        HTTP + 把响应映射成 ServiceResult
XxxService.kt    业务编排 + 实现契约接口
```

**直接照抄 `core/service/example/` 里的三个文件**，它们就是标准样板：
`ExampleProtocol` / `ExampleApi` / `ExampleService`。改字段、改 URL、改业务判断即可，
结构不用动。

为什么要分三层：Protocol 是纯函数，能用普通 JVM 单测覆盖解析逻辑，不需要 mock 网络；
Api 是唯一知道 HTTP 长什么样的地方，错误映射在这里收敛；Service 是唯一知道业务规则的地方。
混在一起以后，改一个字段要动三个关注点。

协议常量集中放在 Protocol 的一个 `object` 里——那是唯一事实源，别散落到 Api 和 Service。

### 复用宿主组件前，先校验它的假设

契约里的字段类型和接口看起来是通用的，但**宿主实现里可能写死了某个具体服务的措辞和假设**。
你接的是电表，界面上却写着「扫描附近水表」，用户会以为装错了 App。

已知的坑（截至现在）：

| 复用的东西 | 写死的假设 | 位置 |
|---|---|---|
| `ConfigFieldType.BLE_DEVICE` | 文案全部是水表：「扫描附近水表」「选择水表」「需要蓝牙权限才能扫描水表」 | `ui/login/ConfigFormRenderer.kt` |
| `searchOptions()` 结果弹窗 | 同上，标题按 `BLE_DEVICE` 硬编码 | 同上 |

所以复用之前，**去读一遍宿主的渲染代码**（`ConfigFormRenderer`、`SectionRenderers`、
`GlanceCard`），确认它对这个字段/段落做了什么、文案是什么。发现写死文案时只有两条路：

- **插件侧规避**：不用 `BLE_DEVICE`，改用 `TEXT` 让用户手填 MAC（诚实但体验差）；
- **列为宿主待改项**：照常用，但在交付说明里明确写「宿主 `ConfigFormRenderer` 的 BLE 文案
  是水表专用，接非水表设备需要宿主改成通用措辞」，让用户知道这不是插件的问题。

**不能默默沿用**——用户看到错字只会认为插件写坏了。判断标准很简单：这句文案如果出现在
你的服务里，是错的吗？是错的就要说。

## 第三步：写对失败

这是最容易写错、也最影响体验的一点。**必须区分三种失败**：

```kotlin
sealed interface ServiceResult<out T> {
    data class Ok<T>(val value: T)
    data class NetworkError(val message: String, val httpStatus: Int? = null)  // 超时 / DNS / 非 2xx
    data class Unauthorized(val message: String)                               // 401/403 或 token 失效
    data class BusinessError(val code: Int, val message: String)               // 200 但业务码 ≠ 0
}
```

为什么不能统一返回 `null`：三种失败在 UI 上要做完全不同的事——
`NetworkError` 引导重试、`Unauthorized` 引导重新登录、`BusinessError` 显示服务端原因。
全塌成 `null` 以后，用户只看到"没数据"，不知道该干什么。这也是仓库里明确禁止的写法。

在 `GlanceSource.glance()` 里也要体现：`Unauthorized` 要映射成 `CardState.UNBOUND` +
`GlanceTarget.Login`，让卡片直接变成"去登录"，而不是一个空壳。

**但这条只对有登录页的插件成立。** 如果你的服务不实现 `ConfigurablePlugin`，
宿主就没有登录页可去，`GlanceTarget.Login` 会变成一个点了没反应的死胡同。
这种服务不该出现 `Unauthorized`（没有凭据可失效）；万一接口仍然返回 401，
映射成 `CardState.ERROR` + 可读的原因，而不是 `UNBOUND`——`UNBOUND` 的语义是
"这个插件还没配置"，对一个无需配置的插件是误导。

`glance()` **不允许抛异常**。失败就用 `CardState.ERROR` 表达。

## 第四步：凭据只走 SecureStorage

```kotlin
class MyService(private val storage: StorageBridge?) : CampusService { ... }
// 注册时：MyService(storage = secureStorage.forPlugin(MyService.ID))
```

禁止裸 `SharedPreferences`、禁止硬编码、禁止写进日志。
`CampusService.id` 同时是存储命名空间，所以它必须全局唯一且不随版本变——
改了 id 等于清空用户的登录状态。

诊断日志如果确实需要，默认关闭，开启时脱敏：不打 token、不打手机号、不打设备号。

## 第五步：注册

在 `CampusApplication.onCreate` 里加一行：

```kotlin
ServiceRegistry.registerAll(
    ExampleService(storage = secureStorage.forPlugin(ExampleService.ID)),
    MyService(storage = secureStorage.forPlugin(MyService.ID))
)
```

注册完，首页卡片、插件页条目、登录页、详情页全部自动出现——因为宿主读的是
`manifest.cards` 和 `configGroups`，不认识具体服务。

**不要**为了让它出现去改 `DashboardViewModel`。那个类的职责只有三件事：
维护布局、汇总登录状态、拉 glance。往里加业务函数会让它变成第二个上帝对象。

## 第六步：测试三条路径

每个插件至少三个用例，不联网：

1. 成功 → 映射出正确字段
2. HTTP 401 → `Unauthorized`
3. 业务码 ≠ 0 → `BusinessError` 且 code 正确

网络错误（`status == -1`）建议加第四个。

用现成的 `app/src/test/java/com/campushub/app/core/bridge/FakeHttp.kt`（按 URL 路由的假
`HttpBridge`，可断言请求头）和内存 `StorageBridge`（照抄 `ExampleServiceTest` 里的
`FakeStorage`）。不要用 Mockito，不要起真网络。

## 第七步：自证

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

两条都绿才算完成。没跑编译就报告完成，是这个仓库明确禁止的。

如果用户要的是真机验证（真实登录、真实数据），说清楚哪些你能验、哪些不能：
单测绿 ≠ 接口通 ≠ 真机正常。

## 停下来问用户的情况

- 唯一可行路径是把凭据转发给第三方 → **必须**先问
- 需要新增依赖 → 先说明现有依赖为什么不够
- 需要扩契约（新段落类型、新字段类型、新卡片尺寸）→ 先给 2~3 个方案
- 宿主组件写死了别的服务的文案（如 BLE 字段写着「水表」）→ 说明是宿主问题，
  给出「插件侧规避」与「列为宿主待改项」两条路，让用户选
- 接口只有逆向结论、没有文档 → 说明风险，让用户确认
- 用户要求的卡片视觉/交互是主观设计 → 先给方案，不要直接写

## 交付检查表

- [ ] 三层分离，Protocol 无 Android 依赖
- [ ] 协议常量集中在一个 object
- [ ] 复用宿主字段/组件前读过其渲染代码，写死文案已规避或列为宿主待改项
- [ ] 凭据走 `SecureStorage.forPlugin(id)`
- [ ] 失败区分 NetworkError / Unauthorized / BusinessError
- [ ] `glance()` 不抛异常；有登录页时未登录返回 UNBOUND + Login，无登录页时用 ERROR
- [ ] 没有伪造数据兜底（宁可空态，不要假数字）
- [ ] 三个单测：成功 / 未授权 / 业务错误
- [ ] `compileDebugKotlin` 与 `testDebugUnitTest` 都绿
- [ ] 没往 `DashboardViewModel` 加业务函数
- [ ] 没有声称实现了不存在的动态插件能力
- [ ] 涉及微件 / 通知 / 扫码 / BLE / 新页面时，产出的是四栏宿主改动清单，而不是伪造的插件侧实现
