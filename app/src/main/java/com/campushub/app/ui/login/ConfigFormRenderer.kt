package com.campushub.app.ui.login

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.campushub.app.core.plugin.ConfigField
import com.campushub.app.core.plugin.ConfigFieldType
import com.campushub.app.core.plugin.ConfigGroup
import com.campushub.app.core.plugin.ConfigOption
import com.campushub.app.core.plugin.ConfigurablePlugin
import com.campushub.app.core.service.ServiceResult
import com.campushub.app.ui.components.CampusField
import com.campushub.app.ui.components.CampusReadonlyField
import com.campushub.app.ui.components.CampusSwitch
import com.campushub.app.ui.components.FieldState
import com.campushub.app.ui.components.ListGroup
import com.campushub.app.ui.components.ListRow
import com.campushub.app.ui.components.ListRowDivider
import com.campushub.app.ui.components.clickableNoIndication
import com.campushub.app.ui.theme.CampusTheme
import com.campushub.app.ui.theme.CampusTokens
import kotlinx.coroutines.launch

/**
 * 按插件声明的字段渲染表单（所有插件共用这一份，不按插件复制页面）。
 *
 * 九种字段类型：文本 / 手机号 / 密码 / 下拉 / 搜索选择 / 图形码 / 短信码 / 蓝牙设备 / 开关。
 */
@Composable
fun ConfigFormRenderer(
    plugin: ConfigurablePlugin,
    group: ConfigGroup,
    values: Map<String, String>,
    fieldErrors: Set<String>,
    captcha: ImageBitmap?,
    captchaLoading: Boolean,
    smsCooldown: Int,
    smsSending: Boolean,
    enabled: Boolean,
    onChange: (String, String) -> Unit,
    onRefreshCaptcha: () -> Unit,
    onRequestSms: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = CampusTheme.palette
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectField by remember { mutableStateOf<ConfigField?>(null) }
    var searchField by remember { mutableStateOf<ConfigField?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchLoading by remember { mutableStateOf(false) }
    var searchOptions by remember { mutableStateOf<List<ConfigOption>>(emptyList()) }
    var searchMessage by remember { mutableStateOf<String?>(null) }
    var pendingScanField by remember { mutableStateOf<ConfigField?>(null) }

    fun runSearch(field: ConfigField, query: String) {
        scope.launch {
            searchLoading = true
            searchMessage = null
            when (val result = plugin.searchOptions(group.id, field.key, query)) {
                is ServiceResult.Ok -> searchOptions = result.value
                is ServiceResult.NetworkError -> {
                    searchOptions = emptyList()
                    searchMessage = result.message
                }
                is ServiceResult.Unauthorized -> {
                    searchOptions = emptyList()
                    searchMessage = result.message
                }
                is ServiceResult.BusinessError -> {
                    searchOptions = emptyList()
                    searchMessage = result.message
                }
            }
            searchLoading = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val field = pendingScanField
        pendingScanField = null
        if (field != null) {
            if (granted.isNotEmpty() && granted.values.all { it }) {
                searchField = field
                searchQuery = ""
                runSearch(field, "")
            } else {
                searchMessage = "需要蓝牙权限才能扫描水表"
                searchField = field
                searchOptions = emptyList()
            }
        }
    }

    fun startScan(field: ConfigField) {
        val missing = field.requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            searchField = field
            searchQuery = ""
            runSearch(field, "")
        } else {
            pendingScanField = field
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        group.fields.forEach { field ->
            val value = values[field.key].orEmpty()
            val errorState = if (field.key in fieldErrors) FieldState.ERROR else FieldState.DEFAULT

            when (field.type) {
                ConfigFieldType.SWITCH -> {
                    ListGroup {
                        ListRow(title = field.label, subtitle = field.subtitle) {
                            CampusSwitch(
                                checked = value == "true",
                                onCheckedChange = { onChange(field.key, it.toString()) },
                                enabled = enabled
                            )
                        }
                    }
                }

                ConfigFieldType.CAPTCHA_IMAGE -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CampusField(
                            value = value,
                            onValueChange = { onChange(field.key, it) },
                            label = field.label,
                            state = errorState,
                            enabled = enabled,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(width = 74.dp, height = 34.dp)
                                .background(palette.surface, CampusTokens.Shapes.extraSmall)
                                .clickableNoIndication { if (enabled) onRefreshCaptcha() },
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                captchaLoading -> CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                captcha != null -> Image(
                                    bitmap = captcha,
                                    contentDescription = "图形验证码，点击刷新",
                                    modifier = Modifier.size(width = 74.dp, height = 34.dp)
                                )
                                else -> Text(
                                    text = "点击刷新",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.water
                                )
                            }
                        }
                    }
                }

                ConfigFieldType.SMS_CODE -> {
                    CampusField(
                        value = value,
                        onValueChange = { onChange(field.key, it) },
                        label = field.label,
                        state = errorState,
                        enabled = enabled,
                        keyboardType = KeyboardType.Number
                    ) {
                        Text(
                            text = when {
                                smsSending -> "发送中…"
                                smsCooldown > 0 -> "${smsCooldown}s"
                                else -> "获取验证码"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (enabled && smsCooldown == 0 && !smsSending) palette.water else palette.ink3,
                            modifier = Modifier.clickableNoIndication {
                                if (enabled && smsCooldown == 0 && !smsSending) onRequestSms()
                            }
                        )
                    }
                }

                ConfigFieldType.SELECT -> {
                    CampusReadonlyField(
                        value = optionsLabel(field, value),
                        label = field.label,
                        placeholder = field.placeholder,
                        onClick = { if (enabled) selectField = field }
                    )
                }

                ConfigFieldType.SEARCH_SELECT -> {
                    CampusReadonlyField(
                        value = value.ifBlank { "" },
                        label = field.label,
                        placeholder = field.placeholder ?: "点击选择",
                        onClick = {
                            if (enabled) {
                                searchField = field
                                searchQuery = ""
                                searchOptions = emptyList()
                                runSearch(field, "")
                            }
                        }
                    )
                }

                ConfigFieldType.BLE_DEVICE -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CampusField(
                            value = value,
                            onValueChange = { onChange(field.key, it) },
                            label = field.label,
                            placeholder = field.placeholder,
                            state = errorState,
                            enabled = enabled
                        )
                        Text(
                            text = if (searchLoading) "正在扫描…" else "扫描附近水表",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (enabled && !searchLoading) palette.water else palette.ink3,
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .clickableNoIndication { if (enabled && !searchLoading) startScan(field) }
                        )
                        searchMessage?.takeIf { searchField?.key == field.key }?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.ink3
                            )
                        }
                    }
                }

                ConfigFieldType.PASSWORD -> CampusField(
                    value = value,
                    onValueChange = { onChange(field.key, it) },
                    label = field.label,
                    placeholder = field.placeholder,
                    state = errorState,
                    enabled = enabled,
                    visualTransformation = PasswordVisualTransformation()
                )

                ConfigFieldType.PHONE -> CampusField(
                    value = value,
                    onValueChange = { onChange(field.key, it) },
                    label = field.label,
                    placeholder = field.placeholder,
                    state = errorState,
                    enabled = enabled,
                    keyboardType = KeyboardType.Phone
                )

                ConfigFieldType.TEXT -> CampusField(
                    value = value,
                    onValueChange = { onChange(field.key, it) },
                    label = field.label,
                    placeholder = field.placeholder,
                    state = errorState,
                    enabled = enabled
                )
            }
        }
    }

    // 下拉（静态选项）
    selectField?.let { field ->
        AlertDialog(
            onDismissRequest = { selectField = null },
            title = { Text(field.label) },
            text = {
                Column {
                    field.options.forEach { option ->
                        ListRow(
                            title = option.label,
                            onClick = {
                                onChange(field.key, option.value)
                                onChange("${field.key}.label", option.label)
                                selectField = null
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectField = null }) { Text("取消") }
            },
            containerColor = palette.surface
        )
    }

    // 搜索选择 / 蓝牙扫描结果
    searchField?.let { field ->
        AlertDialog(
            onDismissRequest = { searchField = null },
            title = { Text(if (field.type == ConfigFieldType.BLE_DEVICE) "选择水表" else field.label) },
            text = {
                Column {
                    CampusField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            runSearch(field, it)
                        },
                        label = "搜索",
                        placeholder = "输入关键词"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (searchLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("加载中…", style = MaterialTheme.typography.labelSmall)
                        }
                    } else if (searchOptions.isEmpty()) {
                        Text(
                            text = searchMessage ?: "没有匹配结果",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.ink3
                        )
                    } else {
                        LazyColumn(modifier = Modifier.height(220.dp)) {
                            items(searchOptions) { option ->
                                ListRow(
                                    title = option.label,
                                    onClick = {
                                        onChange(field.key, option.value)
                                        if (field.type == ConfigFieldType.SEARCH_SELECT) {
                                            onChange("${field.key}.label", option.label)
                                        }
                                        searchField = null
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { searchField = null }) { Text("取消") }
            },
            containerColor = palette.surface
        )
    }
}

private fun optionsLabel(field: ConfigField, value: String): String =
    field.options.firstOrNull { it.value == value }?.label ?: value

/** 供外部复用的权限常量（蓝牙扫描）。 */
val BLE_PERMISSIONS: List<String>
    get() = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        emptyList()
    }
