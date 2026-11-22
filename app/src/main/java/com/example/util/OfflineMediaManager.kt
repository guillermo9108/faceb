package com.example.util

import android.content.Context
import android.net.Uri
import com.example.data.remote.NasApiClient
import com.example.model.MediaType
import com.example.model.Post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class OfflineCacheStats(
    val fileCount: Int,
    val totalBytes: Long,
    val formattedSize: String
)

/**
 * Gestor del almacenamiento multimedia offline en carpeta oculta (.offline_media).
 * Las miniaturas de vídeo nunca se descargan ni generan en segundo plano al listar publicaciones:
 * - Si ya existe miniatura generada en .offline_media/thumbs, se muestra de inmediato.
 * - Si no existe, se utiliza la miniatura imagen explícita o la imagen por defecto de la carpeta del NAS.
 * - Solo se extrae el fotograma real cuando el usuario abre/reproduce el vídeo.
 */
object OfflineMediaManager {

    private const val HIDDEN_DIR_NAME = ".offline_media"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadingSet = ConcurrentHashMap.newKeySet<String>()
    private val thumbGenerationMutex = Mutex()

    private val _thumbnailTick = MutableStateFlow(0)
    val thumbnailTick: StateFlow<Int> = _thumbnailTick.asStateFlow()

    private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "mkv", "webm", "3gp", "avi", "m4v", "ts", "flv")

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun getOfflineDir(context: Context): File {
        val dir = File(context.filesDir, HIDDEN_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val noMedia = File(dir, ".nomedia")
        if (!noMedia.exists()) {
            try {
                noMedia.createNewFile()
            } catch (_: Exception) {}
        }
        return dir
    }

    private fun hashUrl(url: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(url.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }.take(24)
        } catch (_: Exception) {
            url.hashCode().toString()
        }
    }

    private fun extractCandidatePath(url: String): String {
        val qIdx = url.indexOf('?')
        if (qIdx >= 0) {
            val query = url.substring(qIdx + 1).substringBefore('#')
            val pathPair = query.split('&').firstOrNull { it.startsWith("path=") }
            if (pathPair != null) {
                val raw = pathPair.removePrefix("path=")
                val decoded = try {
                    URLDecoder.decode(raw, "UTF-8")
                } catch (_: Exception) {
                    raw
                }
                return decoded.substringBefore('?').substringBefore('#')
            }
        }
        return url.substringBefore('?').substringBefore('#')
    }

    private fun getExtensionFromUrl(url: String): String {
        val candidate = extractCandidatePath(url)
        val ext = candidate.substringAfterLast('.', "")
        return if (ext.isNotEmpty() && ext.length <= 5 && ext.all { it.isLetterOrDigit() }) {
            ext.lowercase()
        } else {
            "bin"
        }
    }

    internal fun isVideoExtension(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return getExtensionFromUrl(url) in VIDEO_EXTENSIONS
    }

    /**
     * Resuelve de forma pura qué URL imagen utilizar para miniatura sin provocar descargas de vídeo.
     */
    internal fun resolveThumbnailCandidate(
        isVideo: Boolean,
        thumbnailUrl: String?,
        mediaUrl: String?,
        fallbackFolderImageUrl: String? = null
    ): String? {
        if (!isVideo) {
            return thumbnailUrl?.takeIf { it.isNotBlank() && !isVideoExtension(it) }
                ?: mediaUrl?.takeIf { it.isNotBlank() && !isVideoExtension(it) }
        }
        val explicitThumb = thumbnailUrl?.takeIf {
            it.isNotBlank() && it != mediaUrl && !isVideoExtension(it)
        }
        if (explicitThumb != null) return explicitThumb

        return fallbackFolderImageUrl?.takeIf {
            it.isNotBlank() && !isVideoExtension(it)
        }
    }

    private fun resolveFullUrl(baseUrl: String, raw: String): String? {
        if (raw.isBlank()) return null
        if (raw.startsWith("http://") || raw.startsWith("https://") ||
            raw.startsWith("file://") || raw.startsWith("content://")
        ) {
            return raw
        }
        val cleanBase = baseUrl.trimEnd('/')
        if (cleanBase.isBlank()) return null
        return "$cleanBase/${raw.trimStart('/')}"
    }

    fun getLocalCacheFile(context: Context, url: String?): File? {
        if (url.isNullOrBlank()) return null
        if (url.startsWith("file://") || url.startsWith("content://")) {
            return null
        }
        val dir = getOfflineDir(context)
        val ext = getExtensionFromUrl(url)
        val filename = "cached_${hashUrl(url)}.$ext"
        val file = File(dir, filename)
        if (file.exists() && file.length() > 0) {
            return file
        }
        if (ext != "php") {
            val legacyPhp = File(dir, "cached_${hashUrl(url)}.php")
            if (legacyPhp.exists() && legacyPhp.length() > 0) return legacyPhp
        }
        return null
    }

    fun isCached(context: Context, url: String?): Boolean {
        return getLocalCacheFile(context, url) != null
    }

    /**
     * Retorna el URI local si el archivo ya fue descargado en la carpeta oculta (.offline_media),
     * o el URI remoto original si aún no ha sido descargado.
     */
    fun getPlayableMediaUri(context: Context, url: String?): Uri {
        if (url.isNullOrBlank()) return Uri.EMPTY
        if (url.startsWith("file://") || url.startsWith("content://")) {
            return Uri.parse(url)
        }
        val cached = getLocalCacheFile(context, url)
        return if (cached != null) {
            Uri.fromFile(cached)
        } else {
            Uri.parse(url)
        }
    }

    fun getThumbsDir(context: Context): File {
        val dir = File(getOfflineDir(context), "thumbs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Retorna el archivo de miniatura local generado para un vídeo si ya existe en .offline_media/thumbs.
     */
    fun getVideoThumbnailFile(context: Context, videoUrl: String?): File? {
        if (videoUrl.isNullOrBlank()) return null
        val key = hashUrl(videoUrl)
        val thumbFile = File(getThumbsDir(context), "vthumb_$key.jpg")
        return if (thumbFile.exists() && thumbFile.length() > 0) thumbFile else null
    }

    /**
     * Genera bajo demanda una miniatura a partir del vídeo SOLO cuando el usuario lo reproduce.
     * Evita saturar el ancho de banda al desplazarse por listas. Si se proporciona la configuración
     * del NAS y el postId, sube también el póster generado al NAS para otros dispositivos familiares.
     */
    suspend fun generateVideoThumbnailOnPlay(
        context: Context,
        videoUrl: String?,
        fallbackChunkUrl: String? = null,
        postId: Int? = null,
        nasIp: String? = null,
        nasPort: Int = 80,
        nasPath: String = "/family_api",
        apiClient: NasApiClient? = null
    ): File? = withContext(Dispatchers.IO) {
        if (videoUrl.isNullOrBlank()) return@withContext null
        val appContext = context.applicationContext

        thumbGenerationMutex.withLock {
            val existing = getVideoThumbnailFile(appContext, videoUrl)
            if (existing != null) return@withLock existing

            val key = hashUrl(videoUrl)
            val thumbsDir = getThumbsDir(appContext)
            val thumbFile = File(thumbsDir, "vthumb_$key.jpg")
            val partFile = File(thumbsDir, "vthumb_$key.jpg.part")

            val sourceUrl = fallbackChunkUrl?.takeIf { it.isNotBlank() } ?: videoUrl
            val localVideo = getLocalCacheFile(appContext, sourceUrl) ?: getLocalCacheFile(appContext, videoUrl)

            val retriever = android.media.MediaMetadataRetriever()
            try {
                if (localVideo != null && localVideo.exists()) {
                    retriever.setDataSource(localVideo.absolutePath)
                } else if (sourceUrl.startsWith("http://") || sourceUrl.startsWith("https://")) {
                    retriever.setDataSource(sourceUrl, mapOf("User-Agent" to "FamilyFeed/1.0"))
                } else if (sourceUrl.startsWith("content://") || sourceUrl.startsWith("file://")) {
                    retriever.setDataSource(appContext, Uri.parse(sourceUrl))
                } else {
                    return@withLock null
                }

                val bitmap = retriever.getFrameAtTime(1_000_000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(0)

                if (bitmap != null) {
                    FileOutputStream(partFile).use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                    }
                    if (partFile.exists() && partFile.length() > 0) {
                        if (thumbFile.exists()) thumbFile.delete()
                        val renamed = partFile.renameTo(thumbFile)
                        val saved = if (renamed) thumbFile else {
                            partFile.copyTo(thumbFile, overwrite = true)
                            partFile.delete()
                            thumbFile
                        }
                        _thumbnailTick.update { it + 1 }

                        if (postId != null && postId > 0 && !nasIp.isNullOrBlank() && apiClient != null) {
                            scope.launch {
                                try {
                                    apiClient.uploadVideoThumbnail(nasIp, nasPort, nasPath, postId, saved)
                                } catch (_: Exception) {}
                            }
                        }
                        return@withLock saved
                    }
                }
            } catch (_: Exception) {
                if (partFile.exists()) partFile.delete()
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {}
            }
            null
        }
    }

    /**
     * Retorna el modelo de imagen ligero para Coil:
     * 1. Para vídeos: devuelve la miniatura local generada si existe en disco; si no, una miniatura imagen válida
     *    o la imagen por defecto de la carpeta del grupo. Si no hay ninguna, devuelve null (sin descargar vídeos).
     * 2. Para imágenes: devuelve el File cacheado si existe, o la URL remota de la imagen.
     */
    fun getDisplayModel(
        context: Context,
        url: String?,
        isVideo: Boolean = false,
        fallbackFolderImageUrl: String? = null,
        videoUrl: String? = null
    ): Any? {
        val actualVideoKey = videoUrl?.takeIf { it.isNotBlank() } ?: url
        if (isVideo || isVideoExtension(url) || isVideoExtension(videoUrl)) {
            val localThumb = getVideoThumbnailFile(context, actualVideoKey)
                ?: getVideoThumbnailFile(context, url)
            if (localThumb != null) return localThumb

            val candidateImage = resolveThumbnailCandidate(
                isVideo = true,
                thumbnailUrl = url,
                mediaUrl = actualVideoKey,
                fallbackFolderImageUrl = fallbackFolderImageUrl
            ) ?: return null

            return getLocalCacheFile(context, candidateImage) ?: candidateImage
        }

        if (url.isNullOrBlank()) return null
        return getLocalCacheFile(context, url) ?: url
    }

    /**
     * Descarga y almacena un archivo multimedia en la carpeta oculta del dispositivo.
     */
    suspend fun downloadAndCache(context: Context, url: String, maxBytesLimit: Long = 0L): File? = withContext(Dispatchers.IO) {
        if (url.isBlank() || url.startsWith("file://") || url.startsWith("content://")) {
            return@withContext null
        }

        val existing = getLocalCacheFile(context, url)
        if (existing != null) {
            existing.setLastModified(System.currentTimeMillis())
            return@withContext existing
        }

        val key = hashUrl(url)
        if (!downloadingSet.add(key)) {
            return@withContext null
        }

        val dir = getOfflineDir(context)
        val ext = getExtensionFromUrl(url)
        val finalFile = File(dir, "cached_$key.$ext")
        val partFile = File(dir, "cached_$key.$ext.part")

        try {
            val request = Request.Builder().url(url).build()
            val response = downloadClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext null
            }

            val body = response.body ?: return@withContext null
            val contentLength = body.contentLength()
            if (maxBytesLimit > 0 && contentLength > maxBytesLimit) {
                body.close()
                return@withContext null
            }
            body.byteStream().use { input ->
                FileOutputStream(partFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (partFile.exists() && partFile.length() > 0) {
                if (finalFile.exists()) finalFile.delete()
                val renamed = partFile.renameTo(finalFile)
                val out = if (renamed) {
                    finalFile
                } else {
                    partFile.copyTo(finalFile, overwrite = true)
                    partFile.delete()
                    finalFile
                }
                out.setLastModified(System.currentTimeMillis())
                out
            } else {
                partFile.delete()
                null
            }
        } catch (_: Exception) {
            if (partFile.exists()) partFile.delete()
            null
        } finally {
            downloadingSet.remove(key)
        }
    }

    fun cacheUrlAsync(context: Context, url: String) {
        if (url.isBlank() || isCached(context, url)) return
        val appContext = context.applicationContext
        scope.launch {
            downloadAndCache(appContext, url)
        }
    }

    /**
     * Precarga progresiva por página (10 publicaciones):
     * Descarga únicamente las miniaturas imagen y fotografías del lote recibido para lectura offline,
     * SIN descargar ni generar miniaturas de vídeos en segundo plano.
     */
    fun prefetchPageMedia(
        context: Context,
        baseUrl: String,
        pagePosts: List<Post>,
        maxCacheBytes: Long = 1024L * 1024L * 1024L,
        protectedUrls: Set<String> = emptySet()
    ) {
        val appContext = context.applicationContext
        scope.launch {
            val urls = mutableListOf<String>()
            pagePosts.forEach { post ->
                post.mediaItems.forEach { media ->
                    val isVideo = media.type == MediaType.VIDEO

                    // 1. Miniatura (solo si es imagen real, nunca archivos de vídeo)
                    resolveFullUrl(baseUrl, media.thumbnailUrl)?.let { thumb ->
                        if (!isVideoExtension(thumb) && thumb != resolveFullUrl(baseUrl, media.url)) {
                            urls.add(thumb)
                        }
                    }

                    // 2. Fotos completas del mosaico
                    if (!isVideo) {
                        resolveFullUrl(baseUrl, media.url)?.let { urls.add(it) }
                    }
                }
            }

            urls.distinct().forEach { url ->
                if (!isCached(appContext, url)) {
                    downloadAndCache(appContext, url, maxBytesLimit = 25 * 1024 * 1024L)
                }
            }

            enforceCacheLimit(appContext, maxCacheBytes, protectedUrls)
        }
    }

    fun prefetchPostsMedia(context: Context, baseUrl: String, posts: List<Post>) {
        prefetchPageMedia(context, baseUrl, posts)
    }

    fun collectPostMediaUrls(baseUrl: String, post: Post): Set<String> {
        val urls = linkedSetOf<String>()
        post.mediaItems.forEach { media ->
            resolveFullUrl(baseUrl, media.thumbnailUrl)?.let { thumb ->
                if (!isVideoExtension(thumb)) urls.add(thumb)
            }
            if (media.chunks.isNotEmpty()) {
                media.chunks.forEach { chunkUrl ->
                    resolveFullUrl(baseUrl, chunkUrl)?.let { urls.add(it) }
                }
            }
            if (media.url.isNotBlank()) {
                resolveFullUrl(baseUrl, media.url)?.let { urls.add(it) }
            }
        }
        return urls
    }

    /**
     * Descarga explícita de toda la publicación (fotos, miniaturas y todos los fragmentos de vídeo)
     * cuando el usuario pulsa "Guardar offline".
     */
    suspend fun downloadFullPostMedia(
        context: Context,
        baseUrl: String,
        post: Post,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val urls = collectPostMediaUrls(baseUrl, post).toList()
        if (urls.isEmpty()) return@withContext true

        var completed = 0
        var allSucceeded = true
        urls.forEach { url ->
            val ok = isCached(appContext, url) || downloadAndCache(appContext, url) != null
            if (!ok) allSucceeded = false
            completed++
            onProgress(completed, urls.size)
        }
        allSucceeded
    }

    /**
     * Mantiene la caché bajo el límite máximo (1 GB por defecto) eliminando los archivos más antiguos
     * mediante política LRU, protegiendo siempre los archivos pertenecientes a publicaciones guardadas offline.
     */
    fun enforceCacheLimit(
        context: Context,
        maxBytes: Long = 1024L * 1024L * 1024L,
        protectedUrls: Set<String> = emptySet()
    ) {
        try {
            val dir = getOfflineDir(context)
            val protectedHashes = protectedUrls.map { hashUrl(it) }.toSet()

            val mainFiles = dir.listFiles { f ->
                f.isFile && !f.name.equals(".nomedia") && !f.name.endsWith(".part")
            } ?: emptyArray()
            val thumbFiles = getThumbsDir(context).listFiles { f ->
                f.isFile && !f.name.endsWith(".part")
            } ?: emptyArray()

            val allFiles = (mainFiles + thumbFiles).toList()
            var totalSize = allFiles.sumOf { it.length() }
            if (totalSize <= maxBytes) return

            val evictable = allFiles.filter { file ->
                val hashMatch = protectedHashes.any { h -> file.name.contains(h) }
                !hashMatch
            }.sortedBy { it.lastModified() }

            for (file in evictable) {
                if (totalSize <= maxBytes) break
                val len = file.length()
                if (file.delete()) {
                    totalSize -= len
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Obtiene estadísticas de ocupación de la carpeta oculta (.offline_media).
     */
    fun getCacheStats(context: Context): OfflineCacheStats {
        val dir = getOfflineDir(context)
        val mainFiles = dir.listFiles { f -> f.isFile && !f.name.equals(".nomedia") && !f.name.endsWith(".part") } ?: emptyArray()
        val thumbFiles = getThumbsDir(context).listFiles { f -> f.isFile && !f.name.endsWith(".part") } ?: emptyArray()
        val files = mainFiles + thumbFiles
        val totalBytes = files.sumOf { it.length() }
        val mb = totalBytes / (1024.0 * 1024.0)
        val formatted = if (mb >= 1.0) "%.1f MB".format(mb) else "%.0f KB".format(totalBytes / 1024.0)
        return OfflineCacheStats(
            fileCount = files.size,
            totalBytes = totalBytes,
            formattedSize = formatted
        )
    }

    /**
     * Vacía el contenido de la carpeta oculta (.offline_media).
     */
    fun clearCache(context: Context): Boolean {
        val dir = getOfflineDir(context)
        var allOk = true
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                file.listFiles()?.forEach { sub -> if (!sub.delete()) allOk = false }
            } else if (file.name != ".nomedia") {
                if (!file.delete()) allOk = false
            }
        }
        return allOk
    }
}
