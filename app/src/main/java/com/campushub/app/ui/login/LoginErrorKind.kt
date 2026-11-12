package com.campushub.app.ui.login

/** 登录错误分类：决定清空/标红哪个字段。 */
enum class LoginErrorKind { CAPTCHA, SMS, OTHER }

/**
 * 注意：不能只匹配“验证码”，否则“短信验证码错误”会被误判成图形码错误，
 * 进而清空图形码并刷新图片，把真正的错误提示盖掉。
 */
fun classifyLoginError(message: String): LoginErrorKind {
    val lower = message.lowercase()
    if (message.contains("图形验证码") ||
        message.contains("图形码") ||
        lower.contains("captcha")
    ) {
        return LoginErrorKind.CAPTCHA
    }
    if (message.contains("短信验证码") ||
        message.contains("短信码") ||
        message.contains("短信")
    ) {
        return LoginErrorKind.SMS
    }
    return LoginErrorKind.OTHER
}
