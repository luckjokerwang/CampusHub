package com.campushub.app.ui.home

import java.util.Locale

/**
 * 卡片主读数的「可动画」解析。
 *
 * 卡片上的大数字（`0.00`、`+410`、`500`）如果直接整串替换，看起来就是硬切；
 * 拆成「数值 + 小数位 + 前后缀」之后可以用 [androidx.compose.animation.core.Animatable]
 * 滚到新值，读数才会有「在动」的感觉。
 *
 * 纯函数、无 Compose 依赖，方便单测。解析不出来（`—`、`未接入`、带千分位等）就返回 null，
 * 调用方原样显示，绝不为了动画把内容显示错。
 */
object ReadoutFormat {

    /**
     * @param value 带符号的数值
     * @param decimals 小数位数（`0.00` → 2，`500` → 0）
     * @param explicitPlus 原文是否带显式 `+`（积分卡是 `+410`，要保留）
     * @param suffix 数字后面的非数字部分（如 `%`、`L`），可能为空
     */
    data class Parsed(
        val value: Float,
        val decimals: Int,
        val explicitPlus: Boolean,
        val suffix: String
    )

    fun parse(text: String?): Parsed? {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty()) return null

        var i = 0
        var explicitPlus = false
        var negative = false
        when (raw[0]) {
            '+' -> {
                explicitPlus = true
                i = 1
            }
            '-' -> {
                negative = true
                i = 1
            }
        }

        val start = i
        var dotAt = -1
        while (i < raw.length) {
            val c = raw[i]
            if (c in '0'..'9') {
                i++
                continue
            }
            if (c == '.' && dotAt < 0) {
                dotAt = i
                i++
                continue
            }
            break
        }

        val number = raw.substring(start, i)
        // 至少一位数字；`.5` 也接受（number 形如 `.5`），但单独一个 `.` 或结尾是 `.` 都不算
        if (number.isEmpty() || number.all { it == '.' } || number.endsWith('.')) return null

        val suffix = raw.substring(i)
        // 后缀里出现数字或小数点说明这不是「一个数字 + 单位」，不做动画
        if (suffix.any { it in '0'..'9' || it == '.' }) return null

        val magnitude = number.toFloatOrNull() ?: return null
        val decimals = if (dotAt < 0) 0 else number.length - number.indexOf('.') - 1
        return Parsed(
            value = if (negative) -magnitude else magnitude,
            decimals = decimals,
            explicitPlus = explicitPlus,
            suffix = suffix
        )
    }

    /** 按 [Parsed] 的格式把动画中的数值写回字符串；负号由这里统一加，避免出现 `+-5`。 */
    fun format(parsed: Parsed, value: Float): String {
        val magnitude = kotlin.math.abs(value)
        val body = if (parsed.decimals <= 0) {
            Math.round(magnitude).toString()
        } else {
            String.format(Locale.US, "%.${parsed.decimals}f", magnitude)
        }
        val sign = when {
            value < 0f && body.any { it in '1'..'9' } -> "-"
            parsed.explicitPlus -> "+"
            else -> ""
        }
        return sign + body + parsed.suffix
    }
}
