package com.campushub.app.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.campushub.app.core.plugin.ConfigurablePlugin
import com.campushub.app.ui.components.CampusButton
import com.campushub.app.ui.components.ButtonSize
import com.campushub.app.ui.components.IconGlyph
import com.campushub.app.ui.components.PageHeader
import com.campushub.app.ui.components.SegmentedControl
import com.campushub.app.ui.theme.CampusTheme
import com.campushub.app.ui.theme.CampusTokens
import kotlinx.coroutines.delay

/**
 * 通用登录/配置页（所有插件共用一份）。
 *
 * 页面结构对照 `design/screens/login.html`：页头 → 服务信息 → 分组切换 →
 * 声明式字段 → 错误行 → 主按钮 → 提示。成功 1.5s 后自动返回。
 */
@Composable
fun LoginScreen(
    plugin: ConfigurablePlugin,
    addAccount: Boolean = false,
    initialGroupId: String? = null,
    viewModelKey: String,
    onBack: () -> Unit,
    onLoggedIn: () -> Unit
) {
    val palette = CampusTheme.palette
    val viewModel: LoginViewModel = viewModel(
        key = viewModelKey,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LoginViewModel(plugin, addAccount, initialGroupId) as T
        }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val group = viewModel.groups.getOrNull(state.selectedGroupIndex) ?: return

    // 成功：1.5 秒后自动返回
    LaunchedEffect(state.phase) {
        if (state.phase == LoginPhase.SUCCESS) {
            delay(1_500)
            onLoggedIn()
            onBack()
        }
    }

    Column(modifier = Modifier
                .fillMaxSize()
                .background(CampusTheme.palette.canvas)) {
        PageHeader(
            title = if (addAccount) "添加账号" else "登录",
            onBack = onBack,
            action = if (state.phase == LoginPhase.SUBMITTING) {
                com.campushub.app.core.plugin.PageAction("取消", "cancel")
            } else {
                null
            },
            onAction = { viewModel.cancelSubmit() }
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CampusTokens.Space.pageMargin)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 服务信息
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconGlyph(glyph = plugin.manifest.iconGlyph, accent = plugin.manifest.accent, size = 34.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plugin.manifest.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.ink,
                        maxLines = 1
                    )
                    Text(
                        text = plugin.manifest.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.ink2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                plugin.manifest.version?.let { version ->
                    Text(
                        text = version,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.ink3
                    )
                }
            }

            if (viewModel.groups.size > 1) {
                SegmentedControl(
                    items = viewModel.groups.map { it.label },
                    selectedIndex = state.selectedGroupIndex,
                    onSelect = viewModel::selectGroup
                )
            }

            when (state.phase) {
                LoginPhase.SUCCESS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.waterSoft, CampusTokens.Shapes.medium)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (addAccount) "✓ 账号已添加" else "✓ 绑定成功",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.water
                        )
                        Text(
                            text = state.successMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.ink2
                        )
                        Text(
                            text = "正在返回…",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.ink3
                        )
                    }
                    CampusButton(
                        label = "立即返回",
                        onClick = {
                            onLoggedIn()
                            onBack()
                        },
                        size = ButtonSize.LG,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                else -> {
                    ConfigFormRenderer(
                        plugin = plugin,
                        group = group,
                        values = state.values,
                        fieldErrors = state.fieldErrors,
                        captcha = state.captcha,
                        captchaLoading = state.captchaLoading,
                        smsCooldown = state.smsCooldown,
                        smsSending = state.smsSending,
                        enabled = state.phase != LoginPhase.SUBMITTING,
                        onChange = viewModel::onFieldChange,
                        onRefreshCaptcha = viewModel::refreshCaptcha,
                        onRequestSms = viewModel::requestSmsCode
                    )

                    if (state.phase == LoginPhase.ERROR && !state.errorText.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(palette.dangerSoft, CampusTokens.Shapes.small)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "!",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.danger
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = state.errorText.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.danger
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    CampusButton(
                        label = if (state.phase == LoginPhase.SUBMITTING) "正在登录…" else group.submitLabel,
                        onClick = viewModel::submit,
                        size = ButtonSize.LG,
                        loading = state.phase == LoginPhase.SUBMITTING,
                        enabled = viewModel.missingFields().isEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (state.phase == LoginPhase.SUBMITTING) {
                        CampusButton(
                            label = "取消",
                            onClick = viewModel::cancelSubmit,
                            style = com.campushub.app.core.plugin.ActionStyle.SECONDARY,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    group.hint?.let { hint ->
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.ink3,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
