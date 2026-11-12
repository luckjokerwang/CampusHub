# 第三方组件与许可证

本仓库源码采用 Apache License 2.0（见 [LICENSE](LICENSE)）。它依赖以下第三方库，
各自保留原有许可证。所有依赖均为 Maven Central / Google Maven 上的公开版本，
未做本地修改。

| 组件 | 版本 | 许可证 |
|---|---|---|
| AndroidX Core KTX | 1.15.0 | Apache-2.0 |
| AndroidX Lifecycle（runtime-ktx / viewmodel-compose / runtime-compose） | 2.8.7 | Apache-2.0 |
| AndroidX Activity Compose | 1.9.3 | Apache-2.0 |
| AndroidX Navigation Compose | 2.8.3 | Apache-2.0 |
| Jetpack Compose（BOM / ui / material3 / animation / icons-extended） | BOM 2024.10.01 | Apache-2.0 |
| AndroidX DataStore Preferences | 1.1.1 | Apache-2.0 |
| AndroidX Security Crypto | 1.1.0-alpha06 | Apache-2.0 |
| OkHttp | 4.12.0 | Apache-2.0 |
| Kotlin Coroutines（android / test） | 1.9.0 | Apache-2.0 |
| Kotlinx Serialization JSON | 1.7.3 | Apache-2.0 |
| JUnit 4 | 4.13.2 | Eclipse Public License 1.0 |
| org.json | 20240303 | JSON License |

## 素材

本仓库**不包含**任何第三方图片、动画、图标包或字体文件。

- 应用图标（`app/src/main/res/drawable/app_logo.xml`、`app/src/main/res/drawable/ic_launcher_foreground.xml`）
  与自适应图标配置是本项目自绘的矢量图形，随 Apache-2.0 一同授权。
- 界面里出现的字形图标来自 Compose Material Icons（Apache-2.0）。
- 没有 Lottie / Live2D / 视频开屏等素材。

如果你在本仓库的基础上添加第三方素材，请在该素材所在目录放一份来源与许可证说明，
并在这里登记；不要笼统地宣称整个仓库都是同一许可证。

## 关于完整版 APK

Releases 中的签名 APK 由项目作者在私有仓库构建，额外包含未公开的服务适配器与
若干第三方 SDK（例如支付 SDK）。它的许可证与依赖清单与本仓库不同，
以 Release Notes 里的说明为准。
