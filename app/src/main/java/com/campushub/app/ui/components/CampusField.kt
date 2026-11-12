package com.campushub.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.campushub.app.ui.theme.CampusTheme
import com.campushub.app.ui.theme.CampusTokens

/** 输入框状态（设计 `.fld` / `.fld.focus` / `.fld.err` / `[readonly]`）。 */
enum class FieldState { DEFAULT, FOCUS, ERROR, READONLY }

/**
 * 单行输入框（高 44dp、圆角 12、1dp 描边）。
 *
 * 用 [BasicTextField] 而不是 `OutlinedTextField`：后者自带 56dp 高与浮动标签，
 * 与本设计的 44dp 紧凑行不一致。
 */
@Composable
fun CampusField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    state: FieldState = FieldState.DEFAULT,
    placeholder: String? = null,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (RowScope.() -> Unit)? = null
) {
    val palette = CampusTheme.palette
    val borderColor = when (state) {
        FieldState.ERROR -> palette.danger
        FieldState.FOCUS -> palette.water
        else -> palette.line
    }
    val glow = when (state) {
        FieldState.ERROR -> palette.dangerSoft
        FieldState.FOCUS -> palette.waterSoft
        else -> null
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .background(
                palette.surface,
                CampusTokens.Shapes.small
            )
            .then(
                if (glow != null) {
                    Modifier.border(3.dp, glow, CampusTokens.Shapes.small)
                } else {
                    Modifier
                }
            )
            .border(1.dp, borderColor, CampusTokens.Shapes.small)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder ?: label,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = LocalTextStyle.current.merge(
                    MaterialTheme.typography.bodyMedium.copy(color = palette.ink)
                ),
                cursorBrush = SolidColor(palette.water),
                visualTransformation = visualTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth()
            )
        }
        trailing?.let { it() }
    }
}

/** 只读字段（用于搜索选择、扫码结果这类点击后弹窗的输入）。 */
@Composable
fun CampusReadonlyField(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null
) {
    Box(modifier = modifier.clickableNoIndication(onClick)) {
        CampusField(
            value = value,
            onValueChange = {},
            label = label,
            placeholder = placeholder,
            enabled = false,
            state = FieldState.READONLY
        )
    }
}
