package com.campushub.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.campushub.app.core.plugin.ActionStyle
import com.campushub.app.ui.theme.CampusTheme
import com.campushub.app.ui.theme.CampusTokens

/** 按钮尺寸档（设计 `components.html` 的 `.b / .b.sm / .b.lg`）。 */
enum class ButtonSize(val height: Int) { SM(32), MD(40), LG(46) }

/**
 * 主按钮。
 *
 * 大字号下最小高度抬到 44dp（可点面积优先于紧凑）。
 * 颜色只用令牌：PRIMARY=water，DANGER=danger，SECONDARY=fill。
 */
@Composable
fun CampusButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ActionStyle = ActionStyle.PRIMARY,
    size: ButtonSize = ButtonSize.MD,
    loading: Boolean = false,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    val palette = CampusTheme.palette
    val largeFont = LocalLargeFont.current
    val height = if (largeFont) maxOf(size.height, 44) else size.height

    val container = when {
        !enabled -> palette.fill
        style == ActionStyle.DANGER -> palette.danger
        style == ActionStyle.SECONDARY -> palette.fill
        else -> palette.water
    }
    val contentColor = when {
        !enabled -> palette.ink3
        style == ActionStyle.DANGER -> palette.surface
        style == ActionStyle.SECONDARY -> palette.ink
        else -> palette.surface
    }

    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier
            .height(height.dp)
            .pressScale(interaction),
        enabled = enabled && !loading,
        interactionSource = interaction,
        shape = CampusTokens.Shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = contentColor,
            disabledContainerColor = palette.fill,
            disabledContentColor = palette.ink3
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = contentColor
                )
            } else if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 文字链按钮（如卡片上的「去领取 ›」）。 */
@Composable
fun CampusTextLink(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = CampusTheme.palette.water
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = CampusTokens.Space.minTouch, minHeight = CampusTokens.Space.minTouch)
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clickableNoIndication(onClick)
        )
    }
}
