package com.example.ui.components

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.util.OfflineMediaManager

/**
 * Reproductor de vídeo de alto rendimiento con AndroidX Media3 ExoPlayer.
 * Soporta precacheo transparente desde .offline_media, concatenación sin interrupciones (gapless)
 * para vídeos fraccionados por fragmentos, control inteligente de búfer y reciclado de memoria.
 */
@OptIn(UnstableApi::class)
@Composable
fun FamilyVideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier,
    chunks: List<String> = emptyList(),
    isChunked: Boolean = false,
    autoPlay: Boolean = true,
    isLooping: Boolean = true,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    showControls: Boolean = false,
    onClick: (() -> Unit)? = null,
    onBufferingChanged: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    var isBuffering by remember { mutableStateOf(true) }
    var isUserPaused by remember { mutableStateOf(!autoPlay) }

    // Crear ExoPlayer con buffer optimizado para reproducción inmediata
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

    // Configurar listener de estado de ExoPlayer
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val buffering = (playbackState == Player.STATE_BUFFERING)
                isBuffering = buffering
                onBufferingChanged?.invoke(buffering)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
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

        if (isChunked && chunks.isNotEmpty()) {
            val mediaItems = chunks.map { chunkUrl ->
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

    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable {
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
                    .background(Color.Black.copy(alpha = 0.55f), shape = androidx.compose.foundation.shape.CircleShape),
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
    }
}
