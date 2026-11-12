package com.campushub.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.campushub.app.ui.theme.CampusTheme
import com.campushub.app.ui.theme.CampusTokens

/** 空态（设计 `plugins.html` 05 帧）：虚线方块 + 标题 + 说明。 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    symbol: String = "＋"
) {
    val palette = CampusTheme.palette
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(palette.fill, CampusTokens.Shapes.extraLarge),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = symbol,
                style = MaterialTheme.typography.headlineSmall,
                color = palette.ink3
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.ink,
            textAlign = TextAlign.Center
        )
        Text(
            text = body,
            style = MaterialTheme.typography.labelSmall,
            color = palette.ink3,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}
