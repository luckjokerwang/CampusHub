package com.campushub.app.ui.login

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campushub.app.core.plugin.ConfigFieldType
import com.campushub.app.core.plugin.ConfigurablePlugin
import com.campushub.app.core.service.ServiceResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 登录页阶段。 */
enum class LoginPhase { EDITING, SUBMITTING, ERROR, SUCCESS }

data class LoginUiState(
    val selectedGroupIndex: Int = 0,
    val values: Map<String, String> = emptyMap(),
    val captcha: ImageBitmap? = null,
    val captchaLoading: Boolean = false,
    val smsCooldown: Int = 0,
    val smsSending: Boolean = false,
    val phase: LoginPhase = LoginPhase.EDITING,
    val errorText: String? = null,
    val fieldErrors: Set<String> = emptySet(),
    val successMessage: String = ""
)

/**
 * 通用登录/配置页的状态机（一个插件一个实例，按 pluginId 做 key）。
 *
 * 校验规则：必填非空；手机号必须 11 位；图形码/短信码按 `dependsOn` 联动。
 */
class LoginViewModel(
    private val plugin: ConfigurablePlugin,
    private val addAccount: Boolean = false,
    private val initialGroupId: String? = null
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var submitJob: Job? = null

    val groups = plugin.configGroups

    init {
        val requestedIndex = initialGroupId
            ?.let { id -> groups.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
        val initialIndex = requestedIndex
            ?: if (addAccount) {
                0
            } else {
                groups.indexOfFirst { !plugin.isConfigured(it.id) }.takeIf { it >= 0 } ?: 0
            }
        selectGroup(initialIndex)
    }

    fun selectGroup(index: Int) {
        val group = groups.getOrNull(index) ?: return
        _state.update {
            it.copy(
                selectedGroupIndex = index,
                values = if (addAccount) emptyMap() else plugin.prefill(group.id),
                captcha = null,
                captchaLoading = false,
                smsCooldown = 0,
                phase = LoginPhase.EDITING,
                errorText = null,
                fieldErrors = emptySet()
            )
        }
        if (group.fields.any { field -> field.type == ConfigFieldType.CAPTCHA_IMAGE }) {
            refreshCaptcha()
        }
    }

    fun onFieldChange(key: String, value: String) {
        val group = groups.getOrNull(_state.value.selectedGroupIndex)
        val smsKey = group?.fields?.firstOrNull { it.type == ConfigFieldType.SMS_CODE }?.key
        val phoneChanged = key == "phone"
        _state.update {
            it.copy(
                values = it.values + (key to value) +
                    if (phoneChanged && smsKey != null) mapOf(smsKey to "") else emptyMap(),
                fieldErrors = it.fieldErrors - key,
                smsCooldown = if (phoneChanged) 0 else it.smsCooldown
                // 保留 errorText / phase：错误提示至少留到下一次提交或主动刷新，避免闪一下就消失。
            )
        }
        if (key == "phone" && value.trim().length == 11 && _state.value.captcha == null && !_state.value.captchaLoading) {
            refreshCaptcha()
        }
    }

    fun refreshCaptcha(clearError: Boolean = true) {
        val group = groups.getOrNull(_state.value.selectedGroupIndex) ?: return
        val captchaField = group.fields.find { it.type == ConfigFieldType.CAPTCHA_IMAGE } ?: return
        val smsKey = group.fields.firstOrNull { it.type == ConfigFieldType.SMS_CODE }?.key
        viewModelScope.launch {
            _state.update {
                it.copy(
                    captchaLoading = true,
                    captcha = null,
                    errorText = if (clearError) null else it.errorText,
                    phase = if (clearError && it.phase == LoginPhase.ERROR) {
                        LoginPhase.EDITING
                    } else {
                        it.phase
                    },
                    smsCooldown = 0,
                    values = it.values + (captchaField.key to "") +
                        if (smsKey != null) mapOf(smsKey to "") else emptyMap()
                )
            }
            when (val result = plugin.loadCaptcha(group.id, _state.value.values)) {
                is ServiceResult.Ok -> {
                    val bitmap = runCatching {
                        android.graphics.BitmapFactory
                            .decodeByteArray(result.value, 0, result.value.size)
                            ?.asImageBitmap()
                    }.getOrNull()
                    _state.update { it.copy(captcha = bitmap, captchaLoading = false, errorText = null) }
                }
                is ServiceResult.BusinessError -> {
                    _state.update { it.copy(captcha = null, captchaLoading = false, errorText = result.message) }
                }
                is ServiceResult.NetworkError -> {
                    _state.update { it.copy(captcha = null, captchaLoading = false, errorText = "网络异常：${result.message}") }
                }
                else -> _state.update { it.copy(captcha = null, captchaLoading = false) }
            }
        }
    }

    fun requestSmsCode() {
        val group = groups.getOrNull(_state.value.selectedGroupIndex) ?: return
        if (_state.value.smsCooldown > 0 || _state.value.smsSending) return
        val smsKey = group.fields.firstOrNull { it.type == ConfigFieldType.SMS_CODE }?.key
        viewModelScope.launch {
            _state.update {
                it.copy(
                    smsSending = true,
                    errorText = null,
                    phase = if (it.phase == LoginPhase.ERROR) LoginPhase.EDITING else it.phase
                )
            }
            when (val result = plugin.requestSmsCode(group.id, _state.value.values)) {
                is ServiceResult.Ok -> {
                    _state.update {
                        it.copy(
                            smsSending = false,
                            smsCooldown = result.value,
                            values = if (smsKey != null) it.values + (smsKey to "") else it.values
                        )
                    }
                    countdown()
                }
                is ServiceResult.NetworkError ->
                    _state.update { it.copy(smsSending = false, errorText = result.message) }
                is ServiceResult.Unauthorized ->
                    _state.update { it.copy(smsSending = false, errorText = result.message) }
                is ServiceResult.BusinessError ->
                    _state.update { it.copy(smsSending = false, errorText = result.message) }
            }
        }
    }

    private fun countdown() {
        viewModelScope.launch {
            while (_state.value.smsCooldown > 0) {
                delay(1_000)
                _state.update { it.copy(smsCooldown = (it.smsCooldown - 1).coerceAtLeast(0)) }
            }
        }
    }

    /** 必填校验：返回缺失字段的 key 集合。 */
    fun missingFields(): Set<String> {
        val group = groups.getOrNull(_state.value.selectedGroupIndex) ?: return emptySet()
        return group.fields.filter { field ->
            field.required && _state.value.values[field.key].orEmpty().isBlank()
        }.map { it.key }.toSet()
    }

    fun submit() {
        val group = groups.getOrNull(_state.value.selectedGroupIndex) ?: return
        val missing = missingFields()
        if (missing.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = missing) }
            return
        }
        submitJob = viewModelScope.launch {
            _state.update {
                it.copy(phase = LoginPhase.SUBMITTING, errorText = null, fieldErrors = emptySet())
            }
            when (val result = plugin.submit(group.id, _state.value.values)) {
                is ServiceResult.Ok -> _state.update {
                    it.copy(phase = LoginPhase.SUCCESS, successMessage = result.value)
                }
                is ServiceResult.NetworkError -> failWith(result.message, clearSms = false)
                is ServiceResult.Unauthorized -> failWith(result.message, clearSms = true)
                is ServiceResult.BusinessError -> failWith(result.message, clearSms = true)
            }
        }
    }

    private fun failWith(message: String, clearSms: Boolean = false) {
        val group = groups.getOrNull(_state.value.selectedGroupIndex)
        val captchaKey = group?.fields?.firstOrNull { it.type == ConfigFieldType.CAPTCHA_IMAGE }?.key
        val smsKey = group?.fields?.firstOrNull { it.type == ConfigFieldType.SMS_CODE }?.key
        // 注意：不能只匹配“验证码”，否则“短信验证码错误”会被误判成图形码错误。
        val errorKind = classifyLoginError(message)
        val isCaptchaError = errorKind == LoginErrorKind.CAPTCHA
        val isSmsError = errorKind == LoginErrorKind.SMS
        val clearCaptcha = isCaptchaError && captchaKey != null
        val clearSmsField = (clearSms || isSmsError) && smsKey != null
        val clearedValues = buildMap {
            if (clearCaptcha && captchaKey != null) put(captchaKey, "")
            if (clearSmsField && smsKey != null) put(smsKey, "")
        }
        val clearedFields = buildSet {
            if (clearCaptcha && captchaKey != null) add(captchaKey)
            if (clearSmsField && smsKey != null) add(smsKey)
        }
        _state.update {
            it.copy(
                phase = LoginPhase.ERROR,
                errorText = message,
                fieldErrors = clearedFields,
                values = it.values + clearedValues,
                smsCooldown = if (clearSmsField) 0 else it.smsCooldown
            )
        }
        if (clearCaptcha) refreshCaptcha(clearError = false)
    }

    fun cancelSubmit() {
        submitJob?.cancel()
        submitJob = null
        _state.update { it.copy(phase = LoginPhase.EDITING) }
    }
}
