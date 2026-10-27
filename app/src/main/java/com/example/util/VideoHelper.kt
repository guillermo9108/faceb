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
    val thumbnailFile: File?,
    val isVertical: Boolean = false,
    val width: Int = 0,
    val height: Int = 0
)

object VideoHelper {

    /**
     * Extrae de forma asíncrona la duración formateada (mm:ss), dimensiones, orientación vertical y
     * una miniatura en bitmap/archivo evitando fotogramas negros al inicio del vídeo.
     */
    suspend fun extractVideoInfo(context: Context, videoUri: Uri): VideoInfo = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        var durationMs = 0L
        var thumbnailFile: File? = null
        var isVertical = false
        var finalW = 0
        var finalH = 0

        try {
            // Abrir FileDescriptor para máxima compatibilidad con Android Photo Picker / Scoped Storage
            val pfd = try {
                context.contentResolver.openFileDescriptor(videoUri, "r")
            } catch (_: Exception) {
                null
            }

            if (pfd != null) {
                pfd.use { descriptor ->
                    retriever.setDataSource(descriptor.fileDescriptor)
                }
            } else {
                retriever.setDataSource(context, videoUri)
            }

            // 1. Extraer duración y orientación/dimensiones
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durationStr?.toLongOrNull() ?: 0L

            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)

            val rawW = widthStr?.toIntOrNull() ?: 0
            val rawH = heightStr?.toIntOrNull() ?: 0
            val rotation = rotationStr?.toIntOrNull() ?: 0

            finalW = if (rotation == 90 || rotation == 270) rawH else rawW
            finalH = if (rotation == 90 || rotation == 270) rawW else rawH
            isVertical = (finalH > finalW && finalW > 0) || (rawH > rawW && rawW > 0)

            // 2. Extraer fotograma representativo evitando fotogramas negros iniciales
            val candidateTimestamps = mutableListOf<Long>()
            val durationMicros = durationMs * 1000L

            if (durationMs > 3000L) {
                // Probar a los 2 segundos, a 1/3 del vídeo y a la mitad
                candidateTimestamps.add(2_000_000L)
                candidateTimestamps.add(durationMicros / 3)
                candidateTimestamps.add(durationMicros / 2)
                candidateTimestamps.add(1_000_000L)
            } else if (durationMs > 1000L) {
                candidateTimestamps.add(durationMicros / 2)
                candidateTimestamps.add(1_000_000L)
                candidateTimestamps.add(500_000L)
            } else {
                candidateTimestamps.add(0L)
            }

            var chosenBitmap: Bitmap? = null

            for (ts in candidateTimestamps) {
                val frame = retriever.getFrameAtTime(ts, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(ts, MediaMetadataRetriever.OPTION_CLOSEST)
                if (frame != null) {
                    if (!isFrameBlack(frame)) {
                        chosenBitmap = frame
                        break
                    } else if (chosenBitmap == null) {
                        chosenBitmap = frame // Guardar como fallback si todas resultan oscuras
                    }
                }
            }

            if (chosenBitmap == null) {
                chosenBitmap = retriever.frameAtTime
            }

            if (chosenBitmap != null) {
                // Escalar a resolución estándar para miniaturas nítidas y ultraligeras
                val targetW = 854
                val ratio = chosenBitmap.height.toFloat() / chosenBitmap.width.toFloat()
                val targetH = (targetW * ratio).toInt().coerceAtLeast(480)
                val scaled = Bitmap.createScaledBitmap(chosenBitmap, targetW, targetH, true)

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
            thumbnailFile = thumbnailFile,
            isVertical = isVertical,
            width = finalW,
            height = finalH
        )
    }

    /**
     * Comprueba si el fotograma es completamente negro o con exposición cero.
     */
    private fun isFrameBlack(bitmap: Bitmap): Boolean {
        return try {
            val w = bitmap.width
            val h = bitmap.height
            if (w < 4 || h < 4) return false

            var totalLuma = 0L
            var count = 0
            val xPoints = intArrayOf(w / 4, w / 2, (w * 3) / 4)
            val yPoints = intArrayOf(h / 4, h / 2, (h * 3) / 4)

            for (x in xPoints) {
                for (y in yPoints) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    val luma = (r * 299 + g * 587 + b * 114) / 1000
                    totalLuma += luma
                    count++
                }
            }
            val avgLuma = if (count > 0) totalLuma / count else 0
            avgLuma < 14 // Umbral de oscuridad profunda (fotograma negro de fade-in)
        } catch (_: Exception) {
            false
        }
    }

    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "00:00"
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
