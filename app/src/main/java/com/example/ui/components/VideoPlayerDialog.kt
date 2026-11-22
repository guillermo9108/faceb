package com.example.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerDialog(
    videoUrl: String,
    title: String = "Vídeo familiar LAN",
    authorName: String = "",
    durationLabel: String? = null,
    chunks: List<String> = emptyList(),
    chunkDurationsMs: List<Long> = emptyList(),
    totalDurationMsHint: Long = 0L,
    onVideoStarted: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var hasNotifiedThumbExtract by remember(videoUrl, chunks) { mutableStateOf(false) }
    var localPositionMs by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var isEnded by remember { mutableStateOf(false) }
    var currentMediaItemIndex by remember { mutableIntStateOf(0) }
    var exoWindowDurationsMs by remember(chunks, videoUrl) {
        mutableStateOf(List(chunks.size.coerceAtLeast(1)) { 0L })
    }

    val exoPlayer = remember(context) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1000, 30000, 500, 1000)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
                playWhenReady = true
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

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = (playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) {
                    syncWindowDurations()
                }
                if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                    isEnded = true
                    showControls = true
                }
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                syncWindowDurations()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentMediaItemIndex = exoPlayer.currentMediaItemIndex.coerceAtLeast(0)
                syncWindowDurations()
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) {
                    isEnded = false
                    isBuffering = false
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

    LaunchedEffect(videoUrl, chunks) {
        exoPlayer.clearMediaItems()
        if (chunks.isNotEmpty()) {
            val items = chunks.map { cUrl ->
                val pUri = OfflineMediaManager.getPlayableMediaUri(context, cUrl)
                MediaItem.fromUri(pUri)
            }
            exoPlayer.setMediaItems(items)
        } else if (videoUrl.isNotBlank()) {
            val pUri = OfflineMediaManager.getPlayableMediaUri(context, videoUrl)
            exoPlayer.setMediaItem(MediaItem.fromUri(pUri))
        }
        exoPlayer.prepare()
        exoPlayer.play()
    }

    LaunchedEffect(isPlaying, isBuffering, videoUrl) {
        if (isPlaying && !isBuffering && !hasNotifiedThumbExtract) {
            hasNotifiedThumbExtract = true
            onVideoStarted?.invoke()
        }
    }

    // Actualizador periódico de posición unificada
    LaunchedEffect(isPlaying, showControls) {
        while (isPlaying || showControls) {
            currentMediaItemIndex = exoPlayer.currentMediaItemIndex.coerceAtLeast(0)
            localPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            syncWindowDurations()
            delay(150)
        }
    }

    // Auto-ocultar controles tras 3.5 segundos
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3500)
            showControls = false
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
        chunkIndex = currentMediaItemIndex,
        localPositionMs = localPositionMs,
        offsetsMs = unifiedOffsets,
        durationsMs = unifiedDurations
    )
    val boundaries = remember(unifiedOffsets, totalUnifiedDurationMs) {
        UnifiedTimelineHelper.boundaryFractions(unifiedOffsets, totalUnifiedDurationMs)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    showControls = !showControls
                }
                .testTag("video_player_dialog")
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        player = exoPlayer
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
            )

            // Indicador de buffering inicial
            if (isBuffering) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = FacebookBlue,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Transmitiendo con ExoPlayer (LAN / Offline)...",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // Controles superpuestos con animación
            AnimatedVisibility(
                visible = showControls || isBuffering,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                ) {
                    // Top Bar: Título, autor y botón cerrar '✕'
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title.ifBlank { "Vídeo familiar" },
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                ),
                                maxLines = 1
                            )
                            if (chunkCount > 1) {
                                Text(
                                    text = "⚡ Reproducción unificada · Parte ${currentMediaItemIndex + 1} de $chunkCount",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = FacebookBlue,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                )
                            } else if (authorName.isNotBlank()) {
                                Text(
                                    text = "Publicado por $authorName · Synology LAN",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar reproductor",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Botón central Play / Pause / Replay
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.7f))
                            .clickable {
                                if (isEnded) {
                                    exoPlayer.seekTo(0, 0)
                                    exoPlayer.play()
                                    isEnded = false
                                } else if (isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.play()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isEnded -> Icons.Default.Replay
                                isPlaying -> Icons.Default.Pause
                                else -> Icons.Default.PlayArrow
                            },
                            contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    // Barra Unificada Inferior para todas las partes del vídeo
                    UnifiedVideoScrubberBar(
                        globalPositionMs = globalPosMs,
                        totalDurationMs = totalUnifiedDurationMs,
                        isPlaying = isPlaying,
                        currentChunkIndex = currentMediaItemIndex,
                        totalChunks = chunkCount,
                        chunkBoundaryFractions = boundaries,
                        onTogglePlayPause = {
                            if (isEnded) {
                                exoPlayer.seekTo(0, 0)
                                exoPlayer.play()
                                isEnded = false
                            } else if (isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                        },
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
                            currentMediaItemIndex = targetChunk
                            localPositionMs = localMs
                            isEnded = false
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                    )
                }
            }
        }
    }
}
