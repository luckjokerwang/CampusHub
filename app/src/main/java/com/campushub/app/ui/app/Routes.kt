package com.campushub.app.ui.app

import kotlinx.serialization.Serializable

/** 三个固定标签页。插件永远不新增 Tab 或页面。 */
@Serializable
data object HomeRoute

/** 二级页：某个插件的详情（可横向翻页）。 */
@Serializable
data class DetailRoute(val pluginId: String, val pageId: String)

/** 二级页：通用登录/配置页。[addAccount] 为 true 时用于新增账号而不是更新当前账号。 */
@Serializable
data class LoginRoute(
    val pluginId: String,
    val addAccount: Boolean = false,
    val groupId: String? = null
)
