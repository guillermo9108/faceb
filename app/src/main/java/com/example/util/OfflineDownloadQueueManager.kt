package com.example.util

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.model.Post
import com.example.service.OfflineDownloadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

enum class DownloadItemStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

data class QueuedPostDownload(
    val postId: Int,
    val postTitle: String,
    val authorName: String,
    val baseUrl: String,
    val urls: List<String>,
    val status: DownloadItemStatus = DownloadItemStatus.QUEUED,
    val progressPercent: Int = 0,
    val currentFileName: String = "",
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 1,
    val enqueuedAt: Long = System.currentTimeMillis()
)

data class OfflineQueueSnapshot(
    val items: List<QueuedPostDownload> = emptyList(),
    val isDownloading: Boolean = false
) {
    val activeItem: QueuedPostDownload? get() = items.firstOrNull { it.status == DownloadItemStatus.DOWNLOADING }
    val queuedCount: Int get() = items.count { it.status == DownloadItemStatus.QUEUED }
}

/**
 * Cola secuencial FIFO de descargas offline.
 * - El primer elemento pasa inmediatamente a DOWNLOADING (% en progreso).
 * - Los siguientes elementos quedan en estado QUEUED ("En espera").
 * - Al terminar un elemento, pasa automáticamente al siguiente de la cola.
 */
object OfflineDownloadQueueManager {

    private val queueLock = Any()
    private val orderedQueue = ArrayDeque<QueuedPostDownload>()
    private val postStore = ConcurrentHashMap<Int, Post>()

    private val _downloadStates = MutableStateFlow<Map<Int, QueuedPostDownload>>(emptyMap())
    val downloadStates: StateFlow<Map<Int, QueuedPostDownload>> = _downloadStates.asStateFlow()

    private val _completedCountInBatch = MutableStateFlow(0)
    val completedCountInBatch: StateFlow<Int> = _completedCountInBatch.asStateFlow()

    @Volatile
    var onPostDownloadCompleted: (suspend (Post, Boolean) -> Unit)? = null

    fun enqueuePost(context: Context, baseUrl: String, post: Post): QueuedPostDownload {
        val item = enqueueForQueueOnly(baseUrl, post)
        startForegroundQueueService(context)
        return item
    }

    internal fun enqueueForQueueOnly(baseUrl: String, post: Post): QueuedPostDownload {
        val urls = OfflineMediaManager.collectPostMediaUrls(baseUrl, post).toList()
        val firstFileName = urls.firstOrNull()?.let { OfflineMediaManager.extractDisplayFileName(it) }
            ?: "publicacion_${post.id}"
        val title = post.contentText.trim().ifBlank { "Publicación de ${post.author.name}" }.take(48)

        return synchronized(queueLock) {
            val existing = orderedQueue.firstOrNull { it.postId == post.id }
            if (existing != null && (existing.status == DownloadItemStatus.QUEUED || existing.status == DownloadItemStatus.DOWNLOADING)) {
                return existing
            }
            val hasActive = orderedQueue.any { it.status == DownloadItemStatus.DOWNLOADING }
            val newItem = QueuedPostDownload(
                postId = post.id,
                postTitle = title,
                authorName = post.author.name,
                baseUrl = baseUrl,
                urls = urls,
                status = if (hasActive) DownloadItemStatus.QUEUED else DownloadItemStatus.DOWNLOADING,
                progressPercent = 0,
                currentFileName = firstFileName,
                currentFileIndex = 0,
                totalFiles = urls.size.coerceAtLeast(1)
            )
            postStore[post.id] = post
            orderedQueue.addLast(newItem)
            publishStatesLocked()
            newItem
        }
    }

    fun cancelOrRemoveFromQueue(postId: Int) {
        synchronized(queueLock) {
            orderedQueue.removeAll { it.postId == postId }
            postStore.remove(postId)
            publishStatesLocked()
        }
    }

    fun clearPendingQueue() {
        synchronized(queueLock) {
            orderedQueue.clear()
            postStore.clear()
            _completedCountInBatch.value = 0
            publishStatesLocked()
        }
    }

    internal fun takeNextForDownload(): QueuedPostDownload? {
        return synchronized(queueLock) {
            val active = orderedQueue.firstOrNull { it.status == DownloadItemStatus.DOWNLOADING }
            if (active != null) return@synchronized active

            val nextQueuedIndex = orderedQueue.indexOfFirst { it.status == DownloadItemStatus.QUEUED }
            if (nextQueuedIndex < 0) return@synchronized null

            val list = orderedQueue.toMutableList()
            val promoted = list[nextQueuedIndex].copy(status = DownloadItemStatus.DOWNLOADING)
            list[nextQueuedIndex] = promoted
            orderedQueue.clear()
            orderedQueue.addAll(list)
            publishStatesLocked()
            promoted
        }
    }

    internal fun updateActiveProgress(
        postId: Int,
        progressPercent: Int,
        currentFileName: String,
        currentFileIndex: Int,
        totalFiles: Int
    ): QueuedPostDownload? {
        return synchronized(queueLock) {
            val idx = orderedQueue.indexOfFirst { it.postId == postId }
            if (idx < 0) return@synchronized null
            val list = orderedQueue.toMutableList()
            val updated = list[idx].copy(
                status = DownloadItemStatus.DOWNLOADING,
                progressPercent = progressPercent.coerceIn(0, 100),
                currentFileName = currentFileName,
                currentFileIndex = currentFileIndex,
                totalFiles = totalFiles.coerceAtLeast(1)
            )
            list[idx] = updated
            orderedQueue.clear()
            orderedQueue.addAll(list)
            publishStatesLocked()
            updated
        }
    }

    internal fun completeAndAdvance(postId: Int, success: Boolean): Pair<Post?, QueuedPostDownload?> {
        return synchronized(queueLock) {
            val post = postStore.remove(postId)
            orderedQueue.removeAll { it.postId == postId }
            if (success) {
                _completedCountInBatch.update { it + 1 }
            }
            val nextIdx = orderedQueue.indexOfFirst { it.status == DownloadItemStatus.QUEUED }
            val nextItem = if (nextIdx >= 0) {
                val list = orderedQueue.toMutableList()
                val promoted = list[nextIdx].copy(status = DownloadItemStatus.DOWNLOADING, progressPercent = 0)
                list[nextIdx] = promoted
                orderedQueue.clear()
                orderedQueue.addAll(list)
                promoted
            } else {
                null
            }
            publishStatesLocked()
            Pair(post, nextItem)
        }
    }

    fun getQueuedRemainingCount(excludingPostId: Int? = null): Int {
        return synchronized(queueLock) {
            orderedQueue.count {
                it.postId != excludingPostId &&
                    (it.status == DownloadItemStatus.QUEUED || it.status == DownloadItemStatus.DOWNLOADING)
            }
        }
    }

    fun resetBatchCounterIfIdle() {
        synchronized(queueLock) {
            if (orderedQueue.isEmpty()) {
                _completedCountInBatch.value = 0
            }
        }
    }

    private fun publishStatesLocked() {
        _downloadStates.value = orderedQueue.associateBy { it.postId }
    }

    private fun startForegroundQueueService(context: Context) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, OfflineDownloadService::class.java)
        try {
            ContextCompat.startForegroundService(appContext, intent)
        } catch (_: Exception) {
            try {
                appContext.startService(intent)
            } catch (_: Exception) {}
        }
    }
}
