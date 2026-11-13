package com.campushub.app.ui.components

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.campushub.app.ui.theme.CampusTheme

/**
 * 分组内的开关。开启态用品牌水色，关闭态用中性色。
 *
 * 关闭态的轨道与滑块必须显式指定：深色下 M3 默认 unchecked 轨道 `surfaceVariant`(#232B33)
 * 与滑块 `surfaceContainerHighest`(#3A444F) 落在分组白底 `surface`(#171C22) 上对比极低，
 * 看起来就是一个空胶囊、没有滑块。
 */
@Composable
fun CampusSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val palette = CampusTheme.palette
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = palette.surface,
            checkedTrackColor = palette.water,
            uncheckedThumbColor = palette.lineStrong,
            uncheckedTrackColor = palette.fill,
            uncheckedBorderColor = palette.lineStrong
        )
    )
}
