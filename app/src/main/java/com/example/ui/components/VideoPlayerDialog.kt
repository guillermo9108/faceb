package com.example.ui.components

import android.content.pm.ActivityInfo
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.VideoSize
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
    initialChunkIndex: Int = 0,
    initialPositionMs: Long = 0L,
    isInPictureInPictureMode: Boolean = false,
    nextEpisodeTitle: String? = null,
    onPlayNextEpisode: (() -> Unit)? = null,
    onProgressUpdate: ((chunkIndex: Int, localPosMs: Long, globalPosMs: Long, totalDurMs: Long, isEnded: Boolean) -> Unit)? = null,
    onEnterPiP: (() -> Unit)? = null,
    onVideoStarted: (() -> Unit)? = null,
    onDismissWithPosition: ((chunkIndex: Int, positionMs: Long) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val hostView = LocalView.current
    val activityWindow = activity?.window

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var hasNotifiedThumbExtract by remember(videoUrl, chunks) { mutableStateOf(false) }
    var localPositionMs by remember(videoUrl, chunks) {
        mutableLongStateOf(initialPositionMs.coerceAtLeast(0L))
    }
    var showControls by remember(videoUrl, chunks) { mutableStateOf(true) }
    var isEnded by remember(videoUrl, chunks) { mutableStateOf(false) }
    var currentMediaItemIndex by remember(videoUrl, chunks) {
        mutableIntStateOf(initialChunkIndex.coerceAtLeast(0))
    }
    var isLandscapeVideo by remember(videoUrl, chunks) { mutableStateOf<Boolean?>(null) }
    var exoWindowDurationsMs by remember(chunks, videoUrl) {
        mutableStateOf(List(chunks.size.coerceAtLeast(1)) { 0L })
    }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var seekOverlayHint by remember { mutableStateOf<String?>(null) }
    var containerWidthPx by remember { mutableIntStateOf(1080) }

    // Auto-avance al siguiente episodio al terminar
    var autoAdvanceCancelled by remember(videoUrl, chunks) { mutableStateOf(false) }
    var autoAdvanceSeconds by remember(videoUrl, chunks) { mutableIntStateOf(5) }

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

    val reportProgressNow: (Boolean) -> Unit = { ended ->
        val idx = exoPlayer.currentMediaItemIndex.coerceAtLeast(0)
        val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
        val gPos = UnifiedTimelineHelper.globalPositionMs(
            chunkIndex = idx,
            localPositionMs = pos,
            offsetsMs = unifiedOffsets,
            durationsMs = unifiedDurations
        )
        val total = totalUnifiedDurationMs
        if (total > 0L || gPos > 0L || ended) {
            onProgressUpdate?.invoke(idx, pos, gPos, total, ended)
        }
    }

    val seekRelativeMs: (Long) -> Unit = { deltaMs ->
        val curIdx = exoPlayer.currentMediaItemIndex.coerceAtLeast(0)
        val curPos = exoPlayer.currentPosition.coerceAtLeast(0L)
        val curGlobal = UnifiedTimelineHelper.globalPositionMs(
            chunkIndex = curIdx,
            localPositionMs = curPos,
            offsetsMs = unifiedOffsets,
            durationsMs = unifiedDurations
        )
        val maxGlobal = totalUnifiedDurationMs.takeIf { it > 0L } ?: (curGlobal + 60_000L)
        val targetGlobal = (curGlobal + deltaMs).coerceIn(0L, maxGlobal)
        val (targetChunk, localMs) = UnifiedTimelineHelper.seekTargetForGlobal(
            globalPosMs = targetGlobal,
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
        seekOverlayHint = if (deltaMs < 0) "↺ 10s" else "10s ↻"
    }

    LaunchedEffect(seekOverlayHint) {
        if (seekOverlayHint != null) {
            delay(750)
            seekOverlayHint = null
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
                    reportProgressNow(true)
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

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    isLandscapeVideo = videoSize.width >= videoSize.height
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

    // Aplicar velocidad de reproducción
    LaunchedEffect(playbackSpeed) {
        exoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    // Restaurar orientación previa al cerrar el reproductor a pantalla completa
    DisposableEffect(activity) {
        val previousOrientation = activity?.requestedOrientation
            ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose {
            activity?.requestedOrientation = previousOrientation
        }
    }

    // Autorrotación automática según relación de aspecto del vídeo (no forzar en PiP)
    LaunchedEffect(isLandscapeVideo, activity, isInPictureInPictureMode) {
        if (isInPictureInPictureMode) return@LaunchedEffect
        val landscape = isLandscapeVideo ?: return@LaunchedEffect
        activity?.requestedOrientation = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
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

        val safeChunkIdx = if (chunks.isNotEmpty()) {
            initialChunkIndex.coerceIn(0, chunks.lastIndex)
        } else 0
        val safePosMs = initialPositionMs.coerceAtLeast(0L)
        if (safeChunkIdx > 0 || safePosMs > 0L) {
            exoPlayer.seekTo(safeChunkIdx, safePosMs)
            currentMediaItemIndex = safeChunkIdx
            localPositionMs = safePosMs
        }

        exoPlayer.setPlaybackSpeed(playbackSpeed)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    LaunchedEffect(isPlaying, isBuffering, videoUrl) {
        if (isPlaying && !isBuffering && !hasNotifiedThumbExtract) {
            hasNotifiedThumbExtract = true
            onVideoStarted?.invoke()
        }
    }

    // Actualizador periódico de posición unificada + persistencia de progreso cada ~2s
    LaunchedEffect(isPlaying, showControls) {
        var tick = 0
        while (isPlaying || showControls) {
            currentMediaItemIndex = exoPlayer.currentMediaItemIndex.coerceAtLeast(0)
            localPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            syncWindowDurations()
            tick++
            if (tick % 14 == 0) {
                reportProgressNow(false)
            }
            delay(150)
        }
    }

    // Auto-ocultar controles tras 3.5 segundos
    LaunchedEffect(showControls, isPlaying, isInPictureInPictureMode) {
        if (isInPictureInPictureMode) {
            showControls = false
        } else if (showControls && isPlaying && !isEnded) {
            delay(3500)
            showControls = false
        }
    }

    // Cuenta atrás de 5s para siguiente episodio al terminar
    LaunchedEffect(isEnded, nextEpisodeTitle, autoAdvanceCancelled, isInPictureInPictureMode) {
        if (isEnded && nextEpisodeTitle != null && onPlayNextEpisode != null && !autoAdvanceCancelled && !isInPictureInPictureMode) {
            autoAdvanceSeconds = 5
            while (autoAdvanceSeconds > 0 && isEnded && !autoAdvanceCancelled) {
                delay(1000)
                autoAdvanceSeconds--
            }
            if (isEnded && !autoAdvanceCancelled && autoAdvanceSeconds <= 0) {
                onPlayNextEpisode.invoke()
            }
        }
    }

    val handleDismiss = {
        reportProgressNow(isEnded)
        onDismissWithPosition?.invoke(
            exoPlayer.currentMediaItemIndex.coerceAtLeast(0),
            exoPlayer.currentPosition.coerceAtLeast(0L)
        )
        onDismiss()
    }

    BackHandler(enabled = !isInPictureInPictureMode) {
        handleDismiss()
    }

    // Pantalla completa inmersiva (ocultar barras de sistema + usar área de cutout)
    DisposableEffect(activityWindow, hostView, isInPictureInPictureMode) {
        if (!isInPictureInPictureMode && activityWindow != null) {
            WindowCompat.setDecorFitsSystemWindows(activityWindow, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val lp = activityWindow.attributes
                lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                activityWindow.attributes = lp
            }
            WindowInsetsControllerCompat(activityWindow, hostView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            activityWindow?.let { win ->
                WindowInsetsControllerCompat(win, hostView)
                    .show(WindowInsetsCompat.Type.systemBars())
                win.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    // Mantener pantalla encendida durante reproducción o carga en pantalla completa
    val keepAwake = isPlaying || isBuffering
    DisposableEffect(activityWindow, keepAwake) {
        if (keepAwake) {
            activityWindow?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activityWindow?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activityWindow?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { containerWidthPx = it.width.coerceAtLeast(1) }
            .pointerInput(totalUnifiedDurationMs, isInPictureInPictureMode) {
                detectTapGestures(
                    onTap = {
                        if (!isInPictureInPictureMode) {
                            showControls = !showControls
                        }
                    },
                    onDoubleTap = { offset ->
                        if (!isInPictureInPictureMode) {
                            val isLeftHalf = offset.x < (containerWidthPx / 2f)
                            seekRelativeMs(if (isLeftHalf) -10_000L else 10_000L)
                        }
                    }
                )
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
                    keepScreenOn = keepAwake
                    player = exoPlayer
                }
            },
            update = { playerView ->
                playerView.keepScreenOn = keepAwake
            },
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
        )

        // Feedback visual de doble toque (-10s / +10s)
        AnimatedVisibility(
            visible = seekOverlayHint != null && !isInPictureInPictureMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.78f)
            ) {
                Text(
                    text = seekOverlayHint.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }

        // Indicador de buffering inicial
        if (isBuffering && !isInPictureInPictureMode) {
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

        // Controles superpuestos con animación (ocultos automáticamente en PiP)
        AnimatedVisibility(
            visible = !isInPictureInPictureMode && (showControls || isBuffering || isEnded),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
            ) {
                // Top Bar: Título, autor, velocidad, siguiente episodio, PiP, rotación y cerrar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                                    color = Color.White.copy(alpha = 0.72f),
                                    fontSize = 12.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Pill de velocidad (1x -> 1.25x -> 1.5x -> 2x)
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    playbackSpeed = when (playbackSpeed) {
                                        1f -> 1.25f
                                        1.25f -> 1.5f
                                        1.5f -> 2f
                                        else -> 1f
                                    }
                                }
                                .testTag("video_speed_button")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = if (playbackSpeed % 1f == 0f) "${playbackSpeed.toInt()}x" else "${playbackSpeed}x",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (onEnterPiP != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            IconButton(
                                onClick = {
                                    reportProgressNow(false)
                                    onEnterPiP()
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .testTag("video_pip_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureInPictureAlt,
                                    contentDescription = "Imagen en imagen (PiP)",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                isLandscapeVideo = !(isLandscapeVideo ?: false)
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .testTag("video_dialog_rotate_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ScreenRotation,
                                contentDescription = "Rotar pantalla",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = handleDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .testTag("video_dialog_close_button")
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

                // Controles centrales: -10s / Play-Pause-Replay / +10s
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    IconButton(
                        onClick = { seekRelativeMs(-10_000L) },
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .testTag("video_rewind_10_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Retroceder 10 segundos",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.72f))
                            .clickable {
                                if (isEnded) {
                                    exoPlayer.seekTo(0, 0)
                                    exoPlayer.play()
                                    isEnded = false
                                    autoAdvanceCancelled = true
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

                    IconButton(
                        onClick = { seekRelativeMs(10_000L) },
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .testTag("video_forward_10_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Adelantar 10 segundos",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // Banner "Siguiente episodio" + cuenta atrás al finalizar
                if (nextEpisodeTitle != null && onPlayNextEpisode != null) {
                    if (isEnded && !autoAdvanceCancelled) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.Black.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, FacebookBlue.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 112.dp)
                                .fillMaxWidth()
                                .testTag("auto_next_episode_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Siguiente episodio en ${autoAdvanceSeconds}s",
                                        color = FacebookBlue,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = nextEpisodeTitle,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { autoAdvanceCancelled = true }) {
                                        Text("Cancelar", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = { onPlayNextEpisode() },
                                        colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Siguiente", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = FacebookBlue.copy(alpha = 0.9f),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .navigationBarsPadding()
                                .padding(end = 16.dp, bottom = 106.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    reportProgressNow(true)
                                    onPlayNextEpisode()
                                }
                                .testTag("next_episode_pill_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Siguiente episodio",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Siguiente: $nextEpisodeTitle",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
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
                            autoAdvanceCancelled = true
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
                        autoAdvanceCancelled = true
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp)
                )
            }
        }
    }
}
