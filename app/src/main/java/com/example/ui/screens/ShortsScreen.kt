package com.example.ui.screens

import android.net.Uri
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.model.FamilyMember
import com.example.model.Post
import com.example.model.ReactionType
import com.example.ui.components.UnifiedTimelineHelper
import com.example.ui.components.UnifiedVideoScrubberBar
import com.example.ui.components.findActivity
import com.example.ui.theme.FacebookBlue
import com.example.ui.theme.ReactionLoveRed
import com.example.util.OfflineMediaManager
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun ShortsScreen(
    shorts: List<Post>,
    members: List<FamilyMember>,
    baseUrl: String,
    onLikePost: (Int) -> Unit,
    onOpenComments: (Post) -> Unit,
    onOpenCreateShort: () -> Unit,
    onRefresh: () -> Unit,
    onShortViewed: (Int) -> Unit,
    isScreenActive: Boolean = true,
    initialShortId: Int? = null,
    onVideoStarted: ((postId: Int, videoUrl: String, fallbackChunkUrl: String?) -> Unit)? = null,
    onBackClick: (() -> Unit)? = null
) {
    if (shorts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            if (onBackClick != null) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                        .testTag("shorts_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color.White
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(64.dp)
                )
                Text(
                    text = "Aún no hay Shorts familiares",
                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Sé el primero en compartir un momento rápido o vídeo vertical con tu familia.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.7f)),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                FloatingActionButton(
                    onClick = onOpenCreateShort,
                    containerColor = FacebookBlue,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("create_first_short_fab")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Crear Short")
                    }
                }
            }
        }
        return
    }

    val initialPageIndex = remember(shorts, initialShortId) {
        if (initialShortId == null) 0
        else shorts.indexOfFirst { it.id == initialShortId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialPageIndex, pageCount = { shorts.size })

    LaunchedEffect(initialShortId, shorts) {
        if (initialShortId != null) {
            val targetIdx = shorts.indexOfFirst { it.id == initialShortId }
            if (targetIdx >= 0 && pagerState.currentPage != targetIdx) {
                pagerState.scrollToPage(targetIdx)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val shortPost = shorts[page]
            val isCurrentPage = isScreenActive && (pagerState.currentPage == page)

            LaunchedEffect(isCurrentPage) {
                if (isCurrentPage) {
                    onShortViewed(shortPost.id)
                }
            }

            ShortItemPage(
                post = shortPost,
                baseUrl = baseUrl,
                isActive = isCurrentPage,
                onLike = { onLikePost(shortPost.id) },
                onComments = { onOpenComments(shortPost) },
                onVideoStarted = { vUrl, chunkUrl ->
                    onVideoStarted?.invoke(shortPost.id, vUrl, chunkUrl)
                }
            )
        }

        // Top Bar overlay (Back + Title on Left, Create + FAB on Right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onBackClick != null) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                            .testTag("shorts_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver del modo Shorts",
                            tint = Color.White
                        )
                    }
                }

                Text(
                    text = "Shorts",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            FloatingActionButton(
                onClick = onOpenCreateShort,
                containerColor = FacebookBlue,
                contentColor = Color.White,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("create_short_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nuevo Short")
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ShortItemPage(
    post: Post,
    baseUrl: String,
    isActive: Boolean,
    onLike: () -> Unit,
    onComments: () -> Unit,
    onVideoStarted: (videoUrl: String, fallbackChunkUrl: String?) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var hasNotifiedThumbExtract by remember(post.id) { mutableStateOf(false) }
    var currentChunkIndex by remember { mutableIntStateOf(0) }
    var localPositionMs by remember { mutableLongStateOf(0L) }
    val mediaItem = post.mediaItems.firstOrNull()

    fun resolveShortUrl(raw: String): String {
        if (raw.isBlank()) return ""
        return if (raw.startsWith("http://") || raw.startsWith("https://") ||
            raw.startsWith("content://") || raw.startsWith("file://")
        ) {
            raw
        } else {
            val cleanBase = baseUrl.trimEnd('/')
            val cleanPath = raw.trimStart('/')
            "$cleanBase/$cleanPath"
        }
    }

    val rawVideoUrl = mediaItem?.url ?: ""
    val resolvedUrl = remember(rawVideoUrl, baseUrl) { resolveShortUrl(rawVideoUrl) }
    val resolvedChunks = remember(mediaItem?.chunks, baseUrl) {
        mediaItem?.chunks?.map { resolveShortUrl(it) }?.filter { it.isNotBlank() } ?: emptyList()
    }
    var exoWindowDurationsMs by remember(resolvedUrl, resolvedChunks) {
        mutableStateOf(List(resolvedChunks.size.coerceAtLeast(1)) { 0L })
    }

    val exoPlayer = remember(resolvedUrl, resolvedChunks) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1000, 30000, 500, 1000)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ALL
                if (resolvedChunks.isNotEmpty()) {
                    val items = resolvedChunks.map { chunkUrl ->
                        ExoMediaItem.fromUri(OfflineMediaManager.getPlayableMediaUri(context, chunkUrl))
                    }
                    setMediaItems(items)
                    prepare()
                } else if (resolvedUrl.isNotBlank()) {
                    setMediaItem(ExoMediaItem.fromUri(OfflineMediaManager.getPlayableMediaUri(context, resolvedUrl)))
                    prepare()
                }
            }
    }

    fun syncWindowDurations() {
        val timeline = exoPlayer.currentTimeline
        val count = timeline.windowCount
        if (count > 0) {
            val window = Timeline.Window()
            exoWindowDurationsMs = MutableList(count) { idx ->
                timeline.getWindow(idx, window)
                val dur = window.durationMs
                if (dur != C.TIME_UNSET && dur > 0L) dur else 0L
            }
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = (playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) {
                    syncWindowDurations()
                }
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                syncWindowDurations()
            }

            override fun onMediaItemTransition(mediaItem: ExoMediaItem?, reason: Int) {
                currentChunkIndex = exoPlayer.currentMediaItemIndex.coerceAtLeast(0)
                syncWindowDurations()
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                if (playing) isBuffering = false
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(isActive, isPlaying, isBuffering, resolvedUrl) {
        if (isActive && isPlaying && !isBuffering && !hasNotifiedThumbExtract && resolvedUrl.isNotBlank()) {
            hasNotifiedThumbExtract = true
            onVideoStarted(resolvedUrl, resolvedChunks.firstOrNull())
        }
    }

    val activity = remember(context) { context.findActivity() }
    val keepShortAwake = isActive && (isPlaying || isBuffering)
    DisposableEffect(activity, keepShortAwake) {
        val window = activity?.window
        if (keepShortAwake) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(isActive, isPlaying) {
        if (isActive && isPlaying) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    LaunchedEffect(isActive, isPlaying) {
        while (isActive) {
            currentChunkIndex = exoPlayer.currentMediaItemIndex.coerceAtLeast(0)
            localPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            syncWindowDurations()
            delay(150)
        }
    }

    val chunkCount = resolvedChunks.size.coerceAtLeast(1)
    val fallbackTotalMs = (mediaItem?.totalDurationMs ?: 0L).takeIf { it > 0L }
        ?: UnifiedTimelineHelper.parseDurationLabelMs(mediaItem?.durationLabel)
    val unifiedDurations = remember(chunkCount, mediaItem?.chunkDurationsMs, exoWindowDurationsMs, fallbackTotalMs) {
        UnifiedTimelineHelper.buildChunkDurations(
            chunkCount = chunkCount,
            explicitDurationsMs = mediaItem?.chunkDurationsMs ?: emptyList(),
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
        localPositionMs = localPositionMs,
        offsetsMs = unifiedOffsets,
        durationsMs = unifiedDurations
    )
    val boundaries = remember(unifiedOffsets, totalUnifiedDurationMs) {
        UnifiedTimelineHelper.boundaryFractions(unifiedOffsets, totalUnifiedDurationMs)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        isPlaying = !isPlaying
                    },
                    onDoubleTap = {
                        onLike()
                    }
                )
            }
    ) {
        // Reproductor de vídeo nativo ExoPlayer (soporta fragmentos + caché offline)
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    keepScreenOn = keepShortAwake
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            update = { playerView ->
                playerView.keepScreenOn = keepShortAwake
            },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = isBuffering,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(44.dp)
            )
        }

        AnimatedVisibility(
            visible = !isPlaying && !isBuffering,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Reproducir Short",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // Degradado inferior para legibilidad de subtítulos
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
        )

        // Información del autor y pie de foto (esquina inferior izquierda)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 80.dp, bottom = 78.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(post.author.avatarColorHex)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = post.author.avatarEmoji, fontSize = 20.sp)
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = post.author.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                    )
                    Text(
                        text = post.author.role,
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.7f))
                    )
                }
            }

            if (post.contentText.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = post.contentText,
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Columna de interacción (Me Gusta y Comentarios en la esquina inferior derecha)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 82.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            val isLiked = post.reactions.containsKey(ReactionType.LIKE)
            val likesCount = post.reactions.values.sum()

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onLike,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Me gusta",
                        tint = if (isLiked) ReactionLoveRed else Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    text = if (likesCount > 0) likesCount.toString() else "Me gusta",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onComments,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = "Comentarios",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Text(
                    text = if (post.comments.isNotEmpty()) post.comments.size.toString() else "Comentar",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                )
            }
        }

        // Barra unificada compacta en la parte inferior del Short
        UnifiedVideoScrubberBar(
            globalPositionMs = globalPosMs,
            totalDurationMs = totalUnifiedDurationMs,
            isPlaying = isPlaying,
            currentChunkIndex = currentChunkIndex,
            totalChunks = chunkCount,
            chunkBoundaryFractions = boundaries,
            onTogglePlayPause = { isPlaying = !isPlaying },
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
                localPositionMs = localMs
            },
            compact = true,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }
}
