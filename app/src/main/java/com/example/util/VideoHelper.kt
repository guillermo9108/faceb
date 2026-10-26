package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

data class VideoInfo(
    val durationMs: Long,
    val durationFormatted: String,
    val thumbnailFile: File?
)

object VideoHelper {

    /**
     * Extrae de forma asíncrona la duración formateada (mm:ss) y una miniatura en bitmap/archivo
     * a partir de la URI de un vídeo seleccionado en el dispositivo.
     */
    suspend fun extractVideoInfo(context: Context, videoUri: Uri): VideoInfo = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        var durationMs = 0L
        var thumbnailFile: File? = null

        try {
            retriever.setDataSource(context, videoUri)

            // 1. Extraer duración
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durationStr?.toLongOrNull() ?: 0L

            // 2. Extraer frame de miniatura al segundo 1 (o al 0 si es más corto)
            val timeMicros = if (durationMs > 1000L) 1_000_000L else 0L
            val frameBitmap = retriever.getFrameAtTime(timeMicros, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime

            if (frameBitmap != null) {
                // Escalar si es muy grande para optimizar rendimiento y carga rápida
                val scaled = if (frameBitmap.width > 960) {
                    val ratio = frameBitmap.height.toFloat() / frameBitmap.width.toFloat()
                    val targetW = 960
                    val targetH = (targetW * ratio).toInt()
                    Bitmap.createScaledBitmap(frameBitmap, targetW, targetH, true)
                } else {
                    frameBitmap
                }

                val tempThumb = File(context.cacheDir, "thumb_${System.currentTimeMillis()}.jpg")
                FileOutputStream(tempThumb).use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                thumbnailFile = tempThumb
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        val formattedDuration = formatDuration(durationMs)
        VideoInfo(
            durationMs = durationMs,
            durationFormatted = formattedDuration,
            thumbnailFile = thumbnailFile
        )
    }

    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "00:00"
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
