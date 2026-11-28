package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.model.FamilyNotification
import com.example.model.NotificationType

/**
 * Emite notificaciones locales de Android cuando detecta nueva actividad familiar en el NAS
 * (publicaciones o comentarios nuevos de otros miembros).
 */
object FamilyActivityNotifier {

    private const val CHANNEL_ID = "family_activity_channel"
    const val EXTRA_TARGET_POST_ID = "extra_target_post_id"
    const val EXTRA_OPEN_COMMENTS = "extra_open_comments"
    const val EXTRA_NOTIF_TYPE = "extra_notif_type"

    private fun ensureChannel(context: Context): NotificationManager {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Actividad Familiar NAS",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Avisa cuando la familia publica fotos, vídeos o comentarios en el Synology NAS"
            }
            nm.createNotificationChannel(channel)
        }
        return nm
    }

    fun notifyNewItems(context: Context, newNotifications: List<FamilyNotification>) {
        if (newNotifications.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val nm = ensureChannel(context)
        val latest = newNotifications.first()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            latest.targetPostId?.let { putExtra(EXTRA_TARGET_POST_ID, it) }
            putExtra(EXTRA_OPEN_COMMENTS, latest.type == NotificationType.COMMENT)
            putExtra(EXTRA_NOTIF_TYPE, latest.type.name)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            latest.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (newNotifications.size == 1) {
            "${latest.author.avatarEmoji} ${latest.author.name}"
        } else {
            "${newNotifications.size} novedades familiares en el NAS"
        }
        val body = if (newNotifications.size == 1) {
            "${latest.actionText}${if (latest.detailText.isNotBlank()) " · ${latest.detailText}" else ""}"
        } else {
            "Última: ${latest.author.name} ${latest.actionText}"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(4200 + (latest.id.hashCode() and 0xFF), notification)
    }
}
