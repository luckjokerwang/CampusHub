package com.campushub.app.core.bridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat

/**
 * 原生 Toast 弹窗与状态通知栏桥梁
 */
class ToastBridgeImpl(
    private val context: Context
) : ToastBridge {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "campushub_status_channel"
        const val CHANNEL_NAME = "CampusHub 运行状态"
        const val NOTIFICATION_ID = 1001
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "展示插件后台出水及任务执行状态"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun show(message: String, durationLong: Boolean) {
        mainHandler.post {
            Toast.makeText(
                context,
                message,
                if (durationLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun notify(title: String, content: String, progress: Int?) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(progress != null)

        if (progress != null) {
            builder.setProgress(100, progress.coerceIn(0, 100), false)
        }

        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Android 13+ 未授予 POST_NOTIFICATIONS 权限时不抛异常
        }
    }
}
