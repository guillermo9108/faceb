package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.UUID

data class VideoChunkResult(
    val sessionId: String,
    val chunkFiles: List<File>,
    val thumbnailFile: File?,
    val totalDurationMs: Long,
    val durationLabel: String
)

object VideoChunkingManager {

    private const val TAG = "VideoChunkingManager"
    private const val DEFAULT_TARGET_CHUNK_SEC = 8L // 8 segundos por fragmento

    /**
     * Divide un vídeo en fragmentos MP4 pequeños e independientes alineados por fotogramas clave (I-frames)
     * sin recodificación (Zero-Transcoding), lo que resulta extremadamente rápido y eficiente.
     * Emite progreso en tiempo real para visualizar la fase de fragmentación antes de la subida.
     */
    suspend fun sliceVideo(
        context: Context,
        videoUri: Uri,
        targetChunkDurationSec: Long = DEFAULT_TARGET_CHUNK_SEC,
        onProgress: (progress: Float, currentChunk: Int, estimatedTotal: Int) -> Unit = { _, _, _ -> }
    ): Result<VideoChunkResult> = withContext(Dispatchers.IO) {
        val sessionId = UUID.randomUUID().toString().replace("-", "").take(16)
        val tempDir = File(context.cacheDir, "chunks_$sessionId").apply { mkdirs() }

        var tempSourceFile: File? = null
        try {
            onProgress(0.05f, 1, 1)
            // 1. Obtener archivo local a partir del Uri
            tempSourceFile = File(tempDir, "source_temp.mp4")
            context.contentResolver.openInputStream(videoUri)?.use { input ->
                FileOutputStream(tempSourceFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("No se pudo leer el archivo de vídeo"))

            // 2. Extraer metadatos y miniatura principal
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(tempSourceFile.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val totalDurationMs = durationStr?.toLongOrNull() ?: 0L
            val durationLabel = VideoHelper.formatDuration(totalDurationMs)

            val thumbBitmap = retriever.getFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0)
            retriever.release()

            val thumbFile = File(tempDir, "poster.jpg")
            if (thumbBitmap != null) {
                FileOutputStream(thumbFile).use { out ->
                    thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
            }

            val targetChunkMs = targetChunkDurationSec * 1000L

            // 3. Si el vídeo es corto (menos de 10 segundos), no es necesario fragmentar
            if (totalDurationMs <= targetChunkMs * 1.3f) {
                val singleChunk = File(tempDir, "chunk_0000.mp4")
                tempSourceFile.copyTo(singleChunk, overwrite = true)
                onProgress(1.0f, 1, 1)
                return@withContext Result.success(
                    VideoChunkResult(
                        sessionId = sessionId,
                        chunkFiles = listOf(singleChunk),
                        thumbnailFile = if (thumbFile.exists()) thumbFile else null,
                        totalDurationMs = totalDurationMs,
                        durationLabel = durationLabel
                    )
                )
            }

            // 4. Fragmentación en tramos con MediaExtractor y MediaMuxer
            val chunkFiles = splitIntoIndependentMp4s(
                sourceFile = tempSourceFile,
                outputDir = tempDir,
                targetChunkMs = targetChunkMs,
                totalDurationMs = totalDurationMs,
                onProgress = onProgress
            )

            onProgress(1.0f, chunkFiles.size.coerceAtLeast(1), chunkFiles.size.coerceAtLeast(1))

            // Si por compatibilidad de códec no se pudieron crear múltiples partes, usar el archivo completo
            val finalChunks = if (chunkFiles.isNotEmpty()) {
                chunkFiles
            } else {
                val fallbackChunk = File(tempDir, "chunk_0000.mp4")
                tempSourceFile.copyTo(fallbackChunk, overwrite = true)
                listOf(fallbackChunk)
            }

            Result.success(
                VideoChunkResult(
                    sessionId = sessionId,
                    chunkFiles = finalChunks,
                    thumbnailFile = if (thumbFile.exists()) thumbFile else null,
                    totalDurationMs = totalDurationMs,
                    durationLabel = durationLabel
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fragmentando vídeo: ${e.message}", e)
            Result.failure(e)
        } finally {
            try {
                tempSourceFile?.delete()
            } catch (_: Exception) {}
        }
    }

    /**
     * Divide el flujo de vídeo y audio en fragmentos independientes MP4.
     */
    private fun splitIntoIndependentMp4s(
        sourceFile: File,
        outputDir: File,
        targetChunkMs: Long,
        totalDurationMs: Long,
        onProgress: (progress: Float, currentChunk: Int, estimatedTotal: Int) -> Unit = { _, _, _ -> }
    ): List<File> {
        val resultFiles = mutableListOf<File>()
        val extractor = MediaExtractor()
        extractor.setDataSource(sourceFile.absolutePath)

        val estimatedChunks = Math.max(1, (totalDurationMs / targetChunkMs.toFloat()).toInt())
        var videoTrackIndex = -1
        var audioTrackIndex = -1
        var videoFormat: MediaFormat? = null
        var audioFormat: MediaFormat? = null

        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("video/") && videoTrackIndex == -1) {
                videoTrackIndex = i
                videoFormat = format
            } else if (mime.startsWith("audio/") && audioTrackIndex == -1) {
                audioTrackIndex = i
                audioFormat = format
            }
        }

        if (videoTrackIndex == -1 || videoFormat == null) {
            extractor.release()
            return emptyList()
        }

        val buffer = ByteBuffer.allocateDirect(1024 * 1024 * 2) // 2 MB buffer
        val bufferInfo = MediaCodec.BufferInfo()

        var currentChunkIndex = 0
        var currentMuxer: MediaMuxer? = null
        var currentFile: File? = null
        var muxerStarted = false
        var muxerVideoTrack = -1
        var muxerAudioTrack = -1

        var chunkStartPtsUs = 0L
        val targetChunkUs = targetChunkMs * 1000L

        fun startNewChunk() {
            if (currentMuxer != null && muxerStarted) {
                try {
                    currentMuxer?.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "Error deteniendo muxer previo: ${e.message}")
                }
                currentMuxer?.release()
                currentFile?.let { resultFiles.add(it) }
            }

            val chunkName = String.format("chunk_%04d.mp4", currentChunkIndex++)
            currentFile = File(outputDir, chunkName)
            currentMuxer = MediaMuxer(currentFile!!.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxerVideoTrack = currentMuxer!!.addTrack(videoFormat)
            if (audioTrackIndex != -1 && audioFormat != null) {
                muxerAudioTrack = currentMuxer!!.addTrack(audioFormat)
            } else {
                muxerAudioTrack = -1
            }

            currentMuxer!!.start()
            muxerStarted = true
            chunkStartPtsUs = -1L
        }

        // Seleccionar pistas para extracción
        extractor.selectTrack(videoTrackIndex)
        if (audioTrackIndex != -1) {
            extractor.selectTrack(audioTrackIndex)
        }

        startNewChunk()

        var lastProgressEmitMs = 0L
        try {
            while (true) {
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    break // Fin del archivo
                }

                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags
                bufferInfo.offset = 0

                val trackIndex = extractor.sampleTrackIndex
                val isVideo = (trackIndex == videoTrackIndex)
                val isKeyFrame = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

                if (isVideo) {
                    if (chunkStartPtsUs < 0) {
                        chunkStartPtsUs = bufferInfo.presentationTimeUs
                    }

                    val elapsedInChunkUs = bufferInfo.presentationTimeUs - chunkStartPtsUs

                    // Si hemos superado el tiempo del fragmento y encontramos un Keyframe (I-frame), cortar a nuevo fragmento
                    if (elapsedInChunkUs >= targetChunkUs && isKeyFrame) {
                        startNewChunk()
                        chunkStartPtsUs = bufferInfo.presentationTimeUs
                    }

                    // Emitir progreso periódicamente
                    val now = System.currentTimeMillis()
                    if (now - lastProgressEmitMs > 100) {
                        lastProgressEmitMs = now
                        val currentPtsMs = bufferInfo.presentationTimeUs / 1000L
                        val progress = if (totalDurationMs > 0) (currentPtsMs.toFloat() / totalDurationMs).coerceIn(0f, 0.99f) else 0.5f
                        onProgress(progress, currentChunkIndex, Math.max(currentChunkIndex, estimatedChunks))
                    }
                }

                // Ajustar timestamps relativos para que cada fragmento inicie en 0
                val samplePtsUs = (bufferInfo.presentationTimeUs - (if (chunkStartPtsUs > 0) chunkStartPtsUs else 0L)).coerceAtLeast(0L)
                bufferInfo.presentationTimeUs = samplePtsUs

                val targetTrack = if (isVideo) muxerVideoTrack else muxerAudioTrack
                if (targetTrack >= 0 && muxerStarted) {
                    try {
                        currentMuxer?.writeSampleData(targetTrack, buffer, bufferInfo)
                    } catch (e: Exception) {
                        // Tolerar saltos de audio en límites
                    }
                }

                extractor.advance()
            }

            // Cerrar el último fragmento
            if (currentMuxer != null && muxerStarted) {
                try {
                    currentMuxer?.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "Error finalizando último muxer: ${e.message}")
                }
                currentMuxer?.release()
                currentFile?.let { resultFiles.add(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error durante multiplexado: ${e.message}", e)
        } finally {
            extractor.release()
        }

        return resultFiles
    }

    /**
     * Limpia los archivos temporales generados tras completar la subida.
     */
    fun cleanupSession(context: Context, sessionId: String) {
        try {
            val tempDir = File(context.cacheDir, "chunks_$sessionId")
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
            }
        } catch (_: Exception) {}
    }
}
