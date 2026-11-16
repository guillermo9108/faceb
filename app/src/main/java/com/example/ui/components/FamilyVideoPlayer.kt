package com.example.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.ui.theme.FacebookBlue
import com.example.util.OfflineMediaManager
import com.example.util.VideoHelper
import kotlinx.coroutines.delay

/**
 * Utilidad pura para unificar la línea de tiempo de vídeos compuestos por múltiples fragmentos (chunks)
 * en una única barra de progreso continua desde 00:00 hasta la duración total del vídeo.
 */
object UnifiedTimelineHelper {

    fun parseDurationLabelMs(label: String?): Long {
        if (label.isNullOrBlank()) return 0L
        val parts = label.trim().split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            2 -> ((parts[0] * 60L) + parts[1]) * 1000L
            3 -> ((parts[0] * 3600L) + (parts[1] * 60L) + parts[2]) * 1000L
            else -> 0L
        }
    }

    fun buildChunkDurations(
        chunkCount: Int,
        explicitDurationsMs: List<Long> = emptyList(),
        exoDurationsMs: List<Long> = emptyList(),
        fallbackTotalMs: Long = 0L
    ): List<Long> {
        val count = chunkCount.coerceAtLeast(1)
        val raw = (0 until count).map { idx ->
            val exo = exoDurationsMs.getOrNull(idx) ?: 0L
            val exp = explicitDurationsMs.getOrNull(idx) ?: 0L
            when {
                exo > 0L -> exo
                exp > 0L -> exp
                else -> 0L
            }
        }

        val knownDurations = raw.filter { it > 0L }
        val missingCount = raw.count { it <= 0L }
        if (missingCount == 0) return raw

        if (knownDurations.isNotEmpty()) {
            val knownSum = knownDurations.sum()
            val perMissing = if (fallbackTotalMs > knownSum) {
                ((fallbackTotalMs - knownSum) / missingCount).coerceAtLeast(1000L)
            } else {
                (knownSum / knownDurations.size).coerceAtLeast(1000L)
            }
            return raw.map { if (it > 0L) it else perMissing }
        }

        if (fallbackTotalMs > 0L) {
            val base = (fallbackTotalMs / count).coerceAtLeast(1L)
            val remainder = fallbackTotalMs - (base * count)
            return List(count) { idx ->
                if (idx == count - 1) base + remainder else base
            }
        }

        return List(count) { 8000L }
    }

    fun buildChunkOffsets(durationsMs: List<Long>): List<Long> {
        var acc = 0L
        return durationsMs.map { dur ->
            val start = acc
            acc += dur.coerceAtLeast(0L)
            start
        }
    }

    fun totalDurationMs(durationsMs: List<Long>): Long = durationsMs.sumOf { it.coerceAtLeast(0L) }

    fun globalPositionMs(
        chunkIndex: Int,
        localPositionMs: Long,
        offsetsMs: List<Long>,
        durationsMs: List<Long>
    ): Long {
        if (offsetsMs.isEmpty() || durationsMs.isEmpty()) return localPositionMs.coerceAtLeast(0L)
        val idx = chunkIndex.coerceIn(0, offsetsMs.lastIndex)
        val chunkMax = durationsMs.getOrElse(idx) { Long.MAX_VALUE }.coerceAtLeast(0L)
        val clampedLocal = localPositionMs.coerceIn(0L, chunkMax)
        val total = totalDurationMs(durationsMs)
        return (offsetsMs[idx] + clampedLocal).coerceIn(0L, total)
    }

    fun seekTargetForGlobal(
        globalPosMs: Long,
        offsetsMs: List<Long>,
        durationsMs: List<Long>
    ): Pair<Int, Long> {
        if (offsetsMs.isEmpty() || durationsMs.isEmpty()) {
            return Pair(0, globalPosMs.coerceAtLeast(0L))
        }
        val total = totalDurationMs(durationsMs)
        val target = globalPosMs.coerceIn(0L, total)
        for (i in offsetsMs.indices) {
            val start = offsetsMs[i]
            val dur = durationsMs.getOrElse(i) { 0L }
            val end = start + dur
            if (target < end || i == offsetsMs.lastIndex) {
                val local = (target - start).coerceIn(0L, (dur - 1L).coerceAtLeast(0L))
                return Pair(i, local)
            }
        }
        return Pair(0, 0L)
    }

    fun boundaryFractions(offsetsMs: List<Long>, totalDurationMs: Long): List<Float> {
        if (offsetsMs.size <= 1 || totalDurationMs <= 0L) return emptyList()
        return (1 until offsetsMs.size).map { idx ->
            (offsetsMs[idx].toFloat() / totalDurationMs.toFloat()).coerceIn(0.01f, 0.99f)
        }
    }
}

/**
 * Barra unificada reutilizable para reproducción continua de vídeos multi-fragmento.
 */
@Composable
fun UnifiedVideoScrubberBar(
    globalPositionMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    currentChunkIndex: Int,
    totalChunks: Int,
    chunkBoundaryFractions: List<Float>,
    onTogglePlayPause: () -> Unit,
    onSeekToGlobalMs: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onFullscreenClick: (() -> Unit)? = null,
    onCloseClick: (() -> Unit)? = null,
    compact: Boolean = false
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val playbackFraction = if (totalDurationMs > 0L) {
        (globalPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val displayFraction = if (isDragging) dragFraction else playbackFraction
    val displayedTimeMs = if (isDragging && totalDurationMs > 0L) {
        (dragFraction * totalDurationMs).toLong()
    } else globalPositionMs

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                )
            )
            .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 4.dp else 8.dp)
            .testTag("unified_video_scrubber_bar")
    ) {
        // Barra deslizante con marcadores sutiles de unión entre fragmentos
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
            contentAlignment = Alignment.Center
        ) {
            Slider(
                value = displayFraction,
                onValueChange = { frac ->
                    isDragging = true
                    dragFraction = frac
                },
                onValueChangeFinished = {
                    if (totalDurationMs > 0L) {
                        onSeekToGlobalMs((dragFraction * totalDurationMs).toLong())
                    }
                    isDragging = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = FacebookBlue,
                    activeTrackColor = FacebookBlue,
                    inactiveTrackColor = Color.White.copy(alpha = 0.32f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("unified_video_slider")
            )

            if (chunkBoundaryFractions.isNotEmpty()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .padding(horizontal = 10.dp)
                ) {
                    chunkBoundaryFractions.forEach { frac ->
                        val x = size.width * frac
                        drawLine(
                            color = Color.White.copy(alpha = 0.65f),
                            start = Offset(x, 1f),
                            end = Offset(x, size.height - 1f),
                            strokeWidth = 2f
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("unified_video_play_pause")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar vídeo" else "Reproducir vídeo",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Text(
                    text = "${VideoHelper.formatDuration(displayedTimeMs)} / ${
                        if (totalDurationMs > 0L) VideoHelper.formatDuration(totalDurationMs) else "--:--"
                    }",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum"
                    ),
                    fontSize = 11.sp
                )

                if (totalChunks > 1) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = FacebookBlue.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Parte ${currentChunkIndex + 1}/$totalChunks",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onFullscreenClick != null) {
                    IconButton(
                        onClick = onFullscreenClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("unified_video_fullscreen")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Pantalla completa",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                if (onCloseClick != null) {
                    IconButton(
                        onClick = onCloseClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("unified_video_close")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar reproductor",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reproductor de vídeo de alto rendimiento con AndroidX Media3 ExoPlayer.
 * Soporta precacheo transparente desde .offline_media, concatenación sin interrupciones (gapless)
 * para vídeos fraccionados por fragmentos y barra unificada continua entre todas las partes.
 */
@OptIn(UnstableApi::class)
@Composable
fun FamilyVideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier,
    chunks: List<String> = emptyList(),
    chunkDurationsMs: List<Long> = emptyList(),
    totalDurationMsHint: Long = 0L,
    durationLabel: String? = null,
    isChunked: Boolean = false,
    autoPlay: Boolean = true,
    isLooping: Boolean = true,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    showControls: Boolean = false,
    showUnifiedBar: Boolean = false,
    onFullscreenClick: (() -> Unit)? = null,
    onCloseClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onBufferingChanged: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    var isBuffering by remember { mutableStateOf(true) }
    var isUserPaused by remember { mutableStateOf(!autoPlay) }
    var isActuallyPlaying by remember { mutableStateOf(autoPlay) }
    var currentChunkIndex by remember { mutableIntStateOf(0) }
    var currentLocalPosMs by remember { mutableLongStateOf(0L) }
    var exoWindowDurationsMs by remember(chunks, videoUrl) {
        mutableStateOf(List(chunks.size.coerceAtLeast(1)) { 0L })
    }

    val exoPlayer = remember(context) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1000,  // minBufferMs (arranque ultra-rápido en 1s)
                30000, // maxBufferMs
                500,   // bufferForPlaybackMs (inicio inmediato en LAN / offline)
                1000   // bufferForPlaybackAfterRebufferMs
            )
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
                repeatMode = if (isLooping) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            }
    }

    fun syncWindowDurations() {
        val timeline = exoPlayer.currentTimeline
        val count = timeline.windowCount
        if (count > 0) {
            val window = Timeline.Window()
            val list = MutableList(count) { idx ->
                timeline.getWindow(idx, window)
                val dur = window.durationMs
                if (dur != C.TIME_UNSET && dur > 0L) dur else 0L
            }
            exoWindowDurationsMs = list
        }
    }

    // Configurar listener de estado de ExoPlayer
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val buffering = (playbackState == Player.STATE_BUFFERING)
                isBuffering = buffering
                onBufferingChanged?.invoke(buffering)
                if (playbackState == Player.STATE_READY) {
                    syncWindowDurations()
                }
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                syncWindowDurations()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentChunkIndex = exoPlayer.currentMediaItemIndex
                syncWindowDurations()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isActuallyPlaying = isPlaying
                if (isPlaying) {
                    isBuffering = false
                    onBufferingChanged?.invoke(false)
                }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    // Preparar lista de reproducción concatenada (gapless)
    LaunchedEffect(videoUrl, chunks, isChunked) {
        exoPlayer.clearMediaItems()

        val activeChunkUrls = if (chunks.isNotEmpty() && (isChunked || chunks.size > 1)) {
            chunks
        } else if (isChunked && chunks.isNotEmpty()) {
            chunks
        } else {
            emptyList()
        }

        if (activeChunkUrls.isNotEmpty()) {
            val mediaItems = activeChunkUrls.map { chunkUrl ->
                val playableUri = OfflineMediaManager.getPlayableMediaUri(context, chunkUrl)
                MediaItem.fromUri(playableUri)
            }
            exoPlayer.setMediaItems(mediaItems)
        } else if (videoUrl.isNotBlank()) {
            val playableUri = OfflineMediaManager.getPlayableMediaUri(context, videoUrl)
            exoPlayer.setMediaItem(MediaItem.fromUri(playableUri))
        }

        exoPlayer.prepare()
        if (!isUserPaused) {
            exoPlayer.play()
        }
    }

    LaunchedEffect(isUserPaused) {
        if (isUserPaused) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    LaunchedEffect(isActuallyPlaying, showUnifiedBar) {
        while (isActuallyPlaying || showUnifiedBar) {
            currentChunkIndex = exoPlayer.currentMediaItemIndex.coerceAtLeast(0)
            currentLocalPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            syncWindowDurations()
            delay(150)
        }
    }

    val chunkCount = chunks.size.coerceAtLeast(1)
    val fallbackTotalMs = totalDurationMsHint.takeIf { it > 0L }
        ?: UnifiedTimelineHelper.parseDurationLabelMs(durationLabel)
    val unifiedDurations = remember(chunkCount, chunkDurationsMs, exoWindowDurationsMs, fallbackTotalMs) {
        UnifiedTimelineHelper.buildChunkDurations(
            chunkCount = chunkCount,
            explicitDurationsMs = chunkDurationsMs,
            exoDurationsMs = exoWindowDurationsMs,
            fallbackTotalMs = fallbackTotalMs
        )
    }
    val unifiedOffsets = remember(unifiedDurations) {
        UnifiedTimelineHelper.buildChunkOffsets(unifiedDurations)
    }
    val totalUnifiedDurationMs = remember(unifiedDurations) {
        UnifiedTimelineHelper.totalDurationMs(unifiedDurations)
    }
    val globalPosMs = UnifiedTimelineHelper.globalPositionMs(
        chunkIndex = currentChunkIndex,
        localPositionMs = currentLocalPosMs,
        offsetsMs = unifiedOffsets,
        durationsMs = unifiedDurations
    )
    val boundaries = remember(unifiedOffsets, totalUnifiedDurationMs) {
        UnifiedTimelineHelper.boundaryFractions(unifiedOffsets, totalUnifiedDurationMs)
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (onClick != null) {
                    onClick()
                } else {
                    isUserPaused = !isUserPaused
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = showControls
                    this.resizeMode = resizeMode
                    player = exoPlayer
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
                playerView.resizeMode = resizeMode
                playerView.useController = showControls
            },
            modifier = Modifier.fillMaxSize()
        )

        // Indicador de buffering animado
        AnimatedVisibility(
            visible = isBuffering,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        // Icono de pausa cuando el usuario detiene la reproducción
        AnimatedVisibility(
            visible = isUserPaused && !isBuffering,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.Black.copy(alpha = 0.55f), shape = CircleShape)
                    .clickable { isUserPaused = false },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Reproducir",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        if (showUnifiedBar) {
            UnifiedVideoScrubberBar(
                globalPositionMs = globalPosMs,
                totalDurationMs = totalUnifiedDurationMs,
                isPlaying = !isUserPaused && isActuallyPlaying,
                currentChunkIndex = currentChunkIndex,
                totalChunks = chunkCount,
                chunkBoundaryFractions = boundaries,
                onTogglePlayPause = { isUserPaused = !isUserPaused },
                onSeekToGlobalMs = { targetGlobalMs ->
                    val (targetChunk, localMs) = UnifiedTimelineHelper.seekTargetForGlobal(
                        globalPosMs = targetGlobalMs,
                        offsetsMs = unifiedOffsets,
                        durationsMs = unifiedDurations
                    )
                    if (chunkCount > 1) {
                        exoPlayer.seekTo(targetChunk, localMs)
                    } else {
                        exoPlayer.seekTo(localMs)
                    }
                    currentChunkIndex = targetChunk
                    currentLocalPosMs = localMs
                },
                onFullscreenClick = onFullscreenClick,
                onCloseClick = onCloseClick,
                compact = true,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

