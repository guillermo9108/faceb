package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.util.OfflineDownloadQueueManager
import com.example.util.OfflineMediaManager
import com.example.util.QueuedPostDownload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano (dataSync) que mantiene vivas las descargas offline en segundo plano,
 * publica el progreso en vivo en la barra de notificaciones del sistema y pasa automáticamente
 * al siguiente elemento en cola al finalizar cada publicación.
 */
class OfflineDownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var workerJob: Job? = null
    private lateinit var notificationManager: NotificationManager

    companion object {
        private const val CHANNEL_ID = "offline_downloads_channel"
        private const val FOREGROUND_NOTIFICATION_ID = 4102
        private const val SUMMARY_NOTIFICATION_ID = 4103
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannelIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val current = OfflineDownloadQueueManager.takeNextForDownload()
        val initialNotification = buildProgressNotification(
            item = current,
            remainingInQueue = OfflineDownloadQueueManager.getQueuedRemainingCount(current?.postId)
        )
        startForegroundCompat(initialNotification)

        if (workerJob?.isActive != true) {
            workerJob = serviceScope.launch {
                processQueueLoop()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun processQueueLoop() {
        try {
            while (true) {
                val item = OfflineDownloadQueueManager.takeNextForDownload() ?: break
                val urls = item.urls
                val totalFiles = urls.size.coerceAtLeast(1)
                var allSucceeded = true
                var lastNotifTimeMs = 0L
                var lastNotifPercent = -1

                if (urls.isNotEmpty()) {
                    for ((idx, url) in urls.withIndex()) {
                        val activeNow = OfflineDownloadQueueManager.downloadStates.value[item.postId]
                        if (activeNow == null) {
                            allSucceeded = false
                            break
                        }

                        val fileName = OfflineMediaManager.extractDisplayFileName(url)
                        val downloadedFile = OfflineMediaManager.downloadAndCacheWithProgress(
                            context = applicationContext,
                            url = url,
                            isCancelled = {
                                OfflineDownloadQueueManager.downloadStates.value[item.postId] == null
                            },
                            onBytesProgress = { bytesRead, totalBytes ->
                                val fileFrac = if (totalBytes > 0L) {
                                    (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                                val overallPct = (((idx + fileFrac) / totalFiles.toFloat()) * 100f)
                                    .toInt()
                                    .coerceIn(0, 99)

                                val updatedItem = OfflineDownloadQueueManager.updateActiveProgress(
                                    postId = item.postId,
                                    progressPercent = overallPct,
                                    currentFileName = fileName,
                                    currentFileIndex = idx,
                                    totalFiles = totalFiles
                                )

                                val now = System.currentTimeMillis()
                                if (updatedItem != null && (overallPct != lastNotifPercent || now - lastNotifTimeMs >= 350L)) {
                                    lastNotifPercent = overallPct
                                    lastNotifTimeMs = now
                                    notifyIfPermitted(
                                        FOREGROUND_NOTIFICATION_ID,
                                        buildProgressNotification(
                                            item = updatedItem,
                                            remainingInQueue = OfflineDownloadQueueManager.getQueuedRemainingCount(item.postId)
                                        )
                                    )
                                }
                            }
                        )

                        if (OfflineDownloadQueueManager.downloadStates.value[item.postId] == null) {
                            allSucceeded = false
                            break
                        }

                        if (downloadedFile == null) {
                            allSucceeded = false
                        } else {
                            val stepPct = (((idx + 1).toFloat() / totalFiles.toFloat()) * 100f)
                                .toInt()
                                .coerceIn(0, 100)
                            val updatedItem = OfflineDownloadQueueManager.updateActiveProgress(
                                postId = item.postId,
                                progressPercent = stepPct,
                                currentFileName = fileName,
                                currentFileIndex = idx,
                                totalFiles = totalFiles
                            )
                            if (updatedItem != null) {
                                notifyIfPermitted(
                                    FOREGROUND_NOTIFICATION_ID,
                                    buildProgressNotification(
                                        item = updatedItem,
                                        remainingInQueue = OfflineDownloadQueueManager.getQueuedRemainingCount(item.postId)
                                    )
                                )
                            }
                        }
                    }
                }

                val (finishedPost, nextQueued) = OfflineDownloadQueueManager.completeAndAdvance(
                    postId = item.postId,
                    success = allSucceeded
                )

                if (finishedPost != null) {
                    try {
                        OfflineDownloadQueueManager.onPostDownloadCompleted?.invoke(finishedPost, allSucceeded)
                    } catch (_: Exception) {}
                }

                if (nextQueued != null) {
                    notifyIfPermitted(
                        FOREGROUND_NOTIFICATION_ID,
                        buildProgressNotification(
                            item = nextQueued,
                            remainingInQueue = OfflineDownloadQueueManager.getQueuedRemainingCount(nextQueued.postId)
                        )
                    )
                }
            }

            val doneCount = OfflineDownloadQueueManager.completedCountInBatch.value
            if (doneCount > 0) {
                notifyIfPermitted(SUMMARY_NOTIFICATION_ID, buildCompletedNotification(doneCount))
            }
            OfflineDownloadQueueManager.resetBatchCounterIfIdle()
        } finally {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Descargas Offline LAN",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Muestra el progreso de descarga de fotos y vídeos para ver sin conexión"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildProgressNotification(
        item: QueuedPostDownload?,
        remainingInQueue: Int
    ): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (item != null) {
            "Descargando ${item.currentFileName} (${item.progressPercent}%)"
        } else {
            "Preparando descarga offline..."
        }
        val body = if (item != null) {
            buildString {
                append(item.postTitle)
                if (item.totalFiles > 1) {
                    append(" · Archivo ${item.currentFileIndex + 1}/${item.totalFiles}")
                }
                if (remainingInQueue > 0) {
                    append(" · $remainingInQueue en espera")
                }
            }
        } else {
            "Conectando con Synology NAS..."
        }

        val pct = item?.progressPercent ?: 0
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, pct, item == null)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun buildCompletedNotification(completedCount: Int): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (completedCount == 1) {
            "Publicación guardada offline"
        } else {
            "$completedCount publicaciones guardadas offline"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText("Disponible en tu dispositivo sin necesidad de red LAN.")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun notifyIfPermitted(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        notificationManager.notify(id, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
