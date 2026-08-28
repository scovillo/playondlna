package io.github.scovillo.playondlna.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat.getString
import androidx.core.content.ContextCompat.getSystemService
import io.github.scovillo.playondlna.AppLog
import io.github.scovillo.playondlna.R

class MediaServerNotification {
    val channelId = "http_channel"
    val id = 1
    val actionStopServer = "io.github.scovillo.playondlna.server.ACTION_STOP_SERVER"

    fun createNotificationChannel(context: Context) {
        AppLog.i("MediaServerService", "createNotificationChannel with channel id $channelId")
        getSystemService(context, NotificationManager::class.java)
            ?.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    getString(context, R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
    }

    fun build(
        context: Context,
        contentText: String? = null,
    ): Notification {
        AppLog.i("MediaServerService", "build notification with content text $contentText")
        val stopIntent =
            Intent(context, MediaServerService::class.java).apply {
                action = actionStopServer
            }
        val stopPendingIntent =
            PendingIntent.getService(
                context,
                0,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat.Builder(context, channelId)
            .setContentTitle(getString(context, R.string.notification_title))
            .setSmallIcon(R.drawable.playondlna_icon)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(context, R.string.stop),
                stopPendingIntent,
            )
            .apply { contentText?.let(::setContentText) }
            .build()
    }
}
