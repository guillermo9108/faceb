package com.example.util

import android.content.Context
import android.net.Uri
import com.example.model.Post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
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
 * Permite que los vídeos de publicaciones y Shorts, así como las fotos y miniaturas,
 * queden guardados en el dispositivo para reproducción instantánea y uso sin conexión a la red.
 */
object OfflineMediaManager {

    private const val HIDDEN_DIR_NAME = ".offline_media"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadingSet = ConcurrentHashMap.newKeySet<String>()

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

    private fun getExtensionFromUrl(url: String): String {
        val clean = url.substringBefore('?').substringBefore('#')
        val ext = clean.substringAfterLast('.', "")
        return if (ext.isNotEmpty() && ext.length <= 5) ext.lowercase() else "bin"
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
        return if (file.exists() && file.length() > 0) file else null
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
            // Reproducir en streaming directo sin descargar archivos pesados automáticamente
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
     * Genera bajo demanda una miniatura ligera a partir del vídeo local o remoto usando MediaMetadataRetriever
     * y la guarda en la carpeta oculta (.offline_media/thumbs).
     */
    fun getOrGenerateVideoThumbnail(context: Context, videoUrl: String?): File? {
        if (videoUrl.isNullOrBlank()) return null
        val existing = getVideoThumbnailFile(context, videoUrl)
        if (existing != null) return existing

        val key = hashUrl(videoUrl)
        val thumbsDir = getThumbsDir(context)
        val thumbFile = File(thumbsDir, "vthumb_$key.jpg")
        val partFile = File(thumbsDir, "vthumb_$key.jpg.part")

        val localVideo = getLocalCacheFile(context, videoUrl)
        val retriever = android.media.MediaMetadataRetriever()
        try {
            if (localVideo != null && localVideo.exists()) {
                retriever.setDataSource(localVideo.absolutePath)
            } else if (videoUrl.startsWith("http://") || videoUrl.startsWith("https://")) {
                retriever.setDataSource(videoUrl, mapOf("User-Agent" to "FamilyFeed/1.0"))
            } else if (videoUrl.startsWith("content://") || videoUrl.startsWith("file://")) {
                retriever.setDataSource(context, Uri.parse(videoUrl))
            } else {
                return null
            }

            val bitmap = retriever.getFrameAtTime(1_000_000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0)

            if (bitmap != null) {
                FileOutputStream(partFile).use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                }
                if (partFile.exists() && partFile.length() > 0) {
                    if (thumbFile.exists()) thumbFile.delete()
                    partFile.renameTo(thumbFile)
                    return thumbFile
                }
            }
        } catch (_: Exception) {
            if (partFile.exists()) partFile.delete()
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Retorna el objeto adecuado para Coil (File si está en caché offline local, String si es remoto).
     * Si es un vídeo sin miniatura previa, prioriza la miniatura local generada o desencadena su extracción perezosa en segundo plano.
     */
    fun getDisplayModel(context: Context, url: String?, isVideo: Boolean = false): Any? {
        if (url.isNullOrBlank()) return null

        // 1. Si es vídeo, comprobar primero si ya existe la miniatura local generada en .offline_media/thumbs
        if (isVideo) {
            val vThumb = getVideoThumbnailFile(context, url)
            if (vThumb != null) return vThumb

            // Desencadenar la extracción perezosa en segundo plano a medida que el usuario hace scroll
            val appContext = context.applicationContext
            scope.launch {
                getOrGenerateVideoThumbnail(appContext, url)
            }
        }

        // 2. Si es imagen o miniatura descargada previamente en la carpeta oculta (.offline_media)
        val cached = getLocalCacheFile(context, url)
        return cached ?: url
    }

    /**
     * Descarga y almacena un archivo multimedia en la carpeta oculta del dispositivo.
     */
    suspend fun downloadAndCache(context: Context, url: String, maxBytesLimit: Long = 0L): File? = withContext(Dispatchers.IO) {
        if (url.isBlank() || url.startsWith("file://") || url.startsWith("content://")) {
            return@withContext null
        }

        val existing = getLocalCacheFile(context, url)
        if (existing != null) return@withContext existing

        val key = hashUrl(url)
        if (!downloadingSet.add(key)) {
            // Ya hay una descarga en curso para este archivo
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
                // Abortar descarga: archivo demasiado pesado para el modo offline automático
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
                if (renamed) {
                    finalFile
                } else {
                    partFile.copyTo(finalFile, overwrite = true)
                    partFile.delete()
                    finalFile
                }
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
     * Precarga ligera en segundo plano protegida contra saturación de red.
     * Siguiendo la política de optimización: SOLO descarga miniaturas y el primer fragmento (preview chunk)
     * de los vídeos en publicaciones y grupos, permitiendo inicio instantáneo sin saturar la red ni la memoria.
     */
    fun prefetchPostsMedia(context: Context, baseUrl: String, posts: List<Post>) {
        val appContext = context.applicationContext
        scope.launch {
            val urls = mutableListOf<String>()
            posts.forEach { post ->
                post.mediaItems.forEach { media ->
                    val isVideo = media.type == com.example.model.MediaType.VIDEO

                    // 1. Siempre precargar la miniatura
                    val thumbUrl = if (media.thumbnailUrl.startsWith("http://") || media.thumbnailUrl.startsWith("https://")) {
                        media.thumbnailUrl
                    } else if (media.thumbnailUrl.isNotBlank() && baseUrl.isNotBlank()) {
                        "$baseUrl/${media.thumbnailUrl.trimStart('/')}"
                    } else null
                    thumbUrl?.let { urls.add(it) }

                    if (!isVideo) {
                        // Para fotos familiares normales: precargar imagen completa
                        val imgUrl = if (media.url.startsWith("http://") || media.url.startsWith("https://")) {
                            media.url
                        } else if (media.url.isNotBlank() && baseUrl.isNotBlank()) {
                            "$baseUrl/${media.url.trimStart('/')}"
                        } else null
                        imgUrl?.let { urls.add(it) }
                    } else {
                        // Para VÍDEOS: Solo descargar el PRIMER FRAGMENTO si está fragmentado (trozos ligeros de 2-4MB)
                        // NUNCA descargar archivos de vídeo completos ni pesados no fragmentados en segundo plano
                        if (media.isChunked && media.chunks.isNotEmpty()) {
                            val firstChunkUrl = media.chunks.first()
                            val fullFirstChunk = if (firstChunkUrl.startsWith("http://") || firstChunkUrl.startsWith("https://")) {
                                firstChunkUrl
                            } else if (baseUrl.isNotBlank()) {
                                "$baseUrl/${firstChunkUrl.trimStart('/')}"
                            } else null
                            fullFirstChunk?.let { urls.add(it) }
                        }
                    }
                }
            }

            // Descargar progresivamente solo estos elementos ligeros
            urls.distinct().forEach { url ->
                if (!isCached(appContext, url)) {
                    downloadAndCache(appContext, url, maxBytesLimit = 25 * 1024 * 1024L) // Límite de seguridad 25MB
                }
            }
        }
    }

    /**
     * Descarga bajo demanda la totalidad de fragmentos de un vídeo cuando el usuario
     * solicita explícitamente guardar la publicación completa para modo offline sin restricciones.
     */
    fun downloadFullPostMedia(context: Context, baseUrl: String, post: Post) {
        val appContext = context.applicationContext
        scope.launch {
            val urls = mutableListOf<String>()
            post.mediaItems.forEach { media ->
                if (media.chunks.isNotEmpty()) {
                    media.chunks.forEach { chunkUrl ->
                        val fullChunk = if (chunkUrl.startsWith("http://") || chunkUrl.startsWith("https://")) {
                            chunkUrl
                        } else if (chunkUrl.isNotBlank() && baseUrl.isNotBlank()) {
                            "$baseUrl/${chunkUrl.trimStart('/')}"
                        } else null
                        fullChunk?.let { urls.add(it) }
                    }
                } else if (media.url.isNotBlank()) {
                    val fullUrl = if (media.url.startsWith("http://") || media.url.startsWith("https://")) {
                        media.url
                    } else if (baseUrl.isNotBlank()) {
                        "$baseUrl/${media.url.trimStart('/')}"
                    } else null
                    fullUrl?.let { urls.add(it) }
                }
            }

            urls.distinct().forEach { url ->
                if (!isCached(appContext, url)) {
                    downloadAndCache(appContext, url)
                }
            }
        }
    }

    /**
     * Obtiene estadísticas de ocupación de la carpeta oculta (.offline_media).
     */
    fun getCacheStats(context: Context): OfflineCacheStats {
        val dir = getOfflineDir(context)
        val files = dir.listFiles { f -> f.isFile && !f.name.equals(".nomedia") && !f.name.endsWith(".part") } ?: emptyArray()
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
            if (file.name != ".nomedia") {
                if (!file.delete()) allOk = false
            }
        }
        return allOk
    }
}
