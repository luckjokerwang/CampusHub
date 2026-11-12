package com.campushub.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import com.campushub.app.R
import com.campushub.app.core.plugin.PageAction
import com.campushub.app.ui.theme.CampusTheme

/**
 * 标签页顶部栏（设计 `.top`，高 62dp）。
 *
 * @param brand true 时显示应用图标（仅首页）。
 */
@Composable
fun CampusTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    brand: Boolean = false,
    actions: @Composable (RowScope.() -> Unit)? = null
) {
    val palette = CampusTheme.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(62.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (brand) {
            // 与右侧主题按钮同一套样式：纯色 surface 底 + 圆角方形，没有描边、没有水色底
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(palette.surface, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        actions?.let { it() }
    }
}

/**
 * 二级页页头（设计 `.dt`，高 56dp）：返回键 + 标题 + 右侧动作。
 *
 * @param action 右侧动作（如「添加」「重试」）；[onAction] 收到 commandId。
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    action: PageAction? = null,
    onAction: (PageAction) -> Unit = {},
    actionLabelOverride: String? = null,
    actionEnabled: Boolean = true
) {
    val palette = CampusTheme.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clickableNoIndication(onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = palette.ink2,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = palette.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        val label = actionLabelOverride ?: action?.label
        if (label != null && (action != null || actionLabelOverride != null)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (actionEnabled) palette.water else palette.ink3,
                maxLines = 1,
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 8.dp)
                    .clickableNoIndication { if (action != null && actionEnabled) onAction(action) }
            )
        }
    }
}
