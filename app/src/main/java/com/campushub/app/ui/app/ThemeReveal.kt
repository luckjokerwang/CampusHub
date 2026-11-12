package com.campushub.app.ui.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.hypot
import kotlin.math.max
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 主题切换时的「水滴扩散」过渡。
 *
 * 做法与系统深色模式切换一致：切换前先把当前界面抓成一张快照，切换后把快照盖在新主题之上，
 * 再用一个从按钮位置出发、不断变大的圆形把它「挖空」——旧界面像水面被点开一样向四周退去。
 *
 * 抓图失败时直接切换，不影响可用性。
 *
 * @param origin 扩散圆心（主题按钮中心，根坐标系）；null 时回落到右上角
 */
@Composable
fun ThemeReveal(
    darkTheme: Boolean,
    origin: Offset?,
    durationMillis: Int = 1200,
    content: @Composable (Boolean) -> Unit
) {
    val view = LocalView.current
    var renderedDark by remember { mutableStateOf(darkTheme) }
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var holeRadius by remember { mutableFloatStateOf(0f) }
    var holeCenter by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(darkTheme) {
        if (darkTheme == renderedDark) return@LaunchedEffect
        // 用 PixelCopy 从 GPU 抓当前帧（~10ms）。软件重绘整棵 Compose 树要 1s 以上，会明显卡顿。
        val bitmap = captureWindow(view)
        if (bitmap == null) {
            renderedDark = darkTheme
            return@LaunchedEffect
        }

        holeCenter = origin ?: Offset(view.width * 0.85f, view.height * 0.12f)
        val maxRadius = hypot(
            max(holeCenter.x, view.width - holeCenter.x),
            max(holeCenter.y, view.height - holeCenter.y)
        ) + 48f

        holeRadius = view.resources.displayMetrics.density * 18f
        snapshot = bitmap
        renderedDark = darkTheme
        animate(
            initialValue = 0f,
            targetValue = maxRadius,
            animationSpec = tween(durationMillis = durationMillis, easing = LinearEasing)
        ) { value, _ ->
            holeRadius = value
        }
        snapshot = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content(renderedDark)

        val bitmap = snapshot
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    // 过渡期间吞掉点击，避免点到正在被「挖掉」的旧界面
                    .pointerInput(Unit) { }
                    .clip(HoleShape(holeCenter, holeRadius))
            )
        }
    }
}

/**
 * 抓当前窗口的一帧（PixelCopy，API 26+）。
 *
 * 不用 `View.drawToBitmap()`：那是软件重绘，1080×2400 要一秒以上，主题切换会明显卡住。
 */
private suspend fun captureWindow(view: View): ImageBitmap? = suspendCancellableCoroutine { cont ->
    val activity = view.context.findActivity()
    val window = activity?.window
    val width = view.width
    val height = view.height
    if (window == null || width <= 0 || height <= 0) {
        cont.resume(null)
        return@suspendCancellableCoroutine
    }
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        PixelCopy.request(
            window,
            bitmap,
            { result ->
                if (result == PixelCopy.SUCCESS) {
                    cont.resume(bitmap.asImageBitmap())
                } else {
                    cont.resume(null)
                }
            },
            Handler(Looper.getMainLooper())
        )
    } catch (e: Exception) {
        android.util.Log.w("ThemeReveal", "pixelCopy 失败，直接切换主题", e)
        cont.resume(null)
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** 整屏减去一个圆：EvenOdd 填充，圆内不画，圆外画。 */
private class HoleShape(
    private val center: Offset,
    private val radius: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, size))
            if (radius > 0f) addOval(Rect(center = center, radius = radius))
        }
        return Outline.Generic(path)
    }
}
