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
            // Disparar descarga en segundo plano para que la próxima vez esté disponible offline
            cacheUrlAsync(context, url)
            Uri.parse(url)
        }
    }

    /**
     * Retorna el objeto adecuado para Coil (File si está en caché offline, String si no).
     */
    fun getDisplayModel(context: Context, url: String?): Any? {
        if (url.isNullOrBlank()) return null
        val cached = getLocalCacheFile(context, url)
        return cached ?: url
    }

    /**
     * Descarga y almacena un archivo multimedia en la carpeta oculta del dispositivo.
     */
    suspend fun downloadAndCache(context: Context, url: String): File? = withContext(Dispatchers.IO) {
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
     * Precarga en segundo plano todas las fotos, vídeos y miniaturas de una lista de publicaciones.
     */
    fun prefetchPostsMedia(context: Context, baseUrl: String, posts: List<Post>) {
        val appContext = context.applicationContext
        scope.launch {
            val urls = mutableListOf<String>()
            posts.forEach { post ->
                post.mediaItems.forEach { media ->
                    val fullUrl = if (media.url.startsWith("http://") || media.url.startsWith("https://")) {
                        media.url
                    } else if (media.url.isNotBlank() && baseUrl.isNotBlank()) {
                        "$baseUrl/${media.url.trimStart('/')}"
                    } else null

                    val thumbUrl = if (media.thumbnailUrl.startsWith("http://") || media.thumbnailUrl.startsWith("https://")) {
                        media.thumbnailUrl
                    } else if (media.thumbnailUrl.isNotBlank() && baseUrl.isNotBlank()) {
                        "$baseUrl/${media.thumbnailUrl.trimStart('/')}"
                    } else null

                    fullUrl?.let { urls.add(it) }
                    thumbUrl?.let { urls.add(it) }
                }
            }

            // Descargar progresivamente sin saturar
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
