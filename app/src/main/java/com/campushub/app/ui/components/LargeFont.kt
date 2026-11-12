package com.campushub.app.ui.components

import androidx.compose.runtime.compositionLocalOf

/**
 * 是否处于「大字号」模式（fontScale ≥ tokens.a11y.largeFontThreshold）。
 *
 * 由 `CampusApp` 根部按 `LocalDensity.current.fontScale` 提供；卡片与按钮据此
 * 隐藏次要信息、放大点击区。规则来源：`design/docs/impl-plan.md` §6.5。
 */
val LocalLargeFont = compositionLocalOf { false }
