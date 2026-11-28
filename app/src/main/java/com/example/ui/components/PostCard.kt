package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import android.net.Uri
import android.widget.VideoView
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.model.MediaType
import com.example.model.Post
import com.example.model.ReactionType
import com.example.model.VideoWatchProgress
import com.example.ui.theme.FacebookBlue
import com.example.ui.theme.FacebookCardLight
import com.example.ui.theme.LanOfflineAmber
import com.example.ui.theme.LanOnlineGreen
import com.example.ui.theme.ReactionHahaYellow
import com.example.ui.theme.ReactionLikeBlue
import com.example.ui.theme.ReactionLoveRed
import com.example.util.DownloadItemStatus
import com.example.util.QueuedPostDownload
import com.example.util.SearchHelper

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostCard(
    post: Post,
    onReactionClick: (ReactionType) -> Unit,
    onCommentClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDeletePost: () -> Unit,
    onPlayVideo: (videoUrl: String, title: String, authorName: String, durationLabel: String?, chunks: List<String>, chunkDurationsMs: List<Long>, totalDurationMs: Long, startChunkIndex: Int, startPositionMs: Long) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onSendComment: (text: String) -> Unit = {},
    onToggleSaveOffline: () -> Unit = {},
    fallbackFolderImageUrl: String? = null,
    onVideoStarted: ((postId: Int, videoUrl: String, fallbackChunkUrl: String?) -> Unit)? = null,
    downloadState: QueuedPostDownload? = null,
    headerHighlightBadge: String? = null,
    watchProgress: VideoWatchProgress? = null,
    highlightQuery: String = "",
    onEditPost: (() -> Unit)? = null,
    onToggleWatched: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val thumbTick by com.example.util.OfflineMediaManager.thumbnailTick.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showReactionPicker by remember { mutableStateOf(false) }
    var isPlayingInline by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var lastChunkIndex by remember(post.id) { mutableIntStateOf(0) }
    var lastPositionMs by remember(post.id) { mutableLongStateOf(0L) }
    var activeMediaViewerIndex by remember { mutableStateOf<Int?>(null) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("post_card_${post.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // Pinned Banner o insignia de conversación activa
            if (post.isPinned || !headerHighlightBadge.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (post.isPinned) FacebookBlue.copy(alpha = 0.09f)
                            else LanOfflineAmber.copy(alpha = 0.12f)
                        )
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) {
                    if (post.isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Fijado",
                            tint = FacebookBlue,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = headerHighlightBadge ?: "Publicación destacada de la familia",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (post.isPinned) FacebookBlue else LanOfflineAmber,
                            fontSize = 11.5.sp
                        )
                    )
                }
            }

            // 1. Post Header: Avatar, Name, Group / Subtitle, Relative Time, Options
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Author Avatar (44dp circle with crisp subtle border)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(post.author.avatarColorHex))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = post.author.avatarEmoji, fontSize = 22.sp)
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Name & Subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = post.author.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (!post.groupName.isNullOrBlank()) {
                            Text(
                                text = "▸ ${post.groupName}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                ),
                                color = FacebookBlue,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Feeling or Location
                    if (!post.feeling.isNullOrBlank() || !post.location.isNullOrBlank()) {
                        Text(
                            text = buildString {
                                if (!post.feeling.isNullOrBlank()) append("está ${post.feeling} ")
                                if (!post.location.isNullOrBlank()) append("• en ${post.location}")
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Time & Globe (Public / LAN indicator)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = formatFacebookRelativeTime(post.createdAt),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        )
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        )
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = "Público para la Familia (LAN)",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        if (post.isSavedOffline) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                color = LanOnlineGreen.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDone,
                                        contentDescription = "Guardado offline",
                                        tint = LanOnlineGreen,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Offline",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = LanOnlineGreen
                                    )
                                }
                            }
                        }
                    }
                }

                // Top Right More Options (···)
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "Opciones",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (downloadState?.status) {
                                        DownloadItemStatus.QUEUED -> "En espera (Quitar de la cola)"
                                        DownloadItemStatus.DOWNLOADING -> "Descargando ${downloadState.progressPercent}% (Cancelar)"
                                        else -> if (post.isSavedOffline) "Guardado offline (Quitar)" else "Guardar offline (Descargar todo)"
                                    }
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (downloadState?.status) {
                                        DownloadItemStatus.QUEUED -> Icons.Outlined.Schedule
                                        DownloadItemStatus.DOWNLOADING -> Icons.Outlined.CloudDownload
                                        else -> if (post.isSavedOffline) Icons.Default.CloudDone else Icons.Outlined.CloudDownload
                                    },
                                    contentDescription = null,
                                    tint = when (downloadState?.status) {
                                        DownloadItemStatus.QUEUED -> LanOfflineAmber
                                        DownloadItemStatus.DOWNLOADING -> FacebookBlue
                                        else -> if (post.isSavedOffline) LanOnlineGreen else MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            },
                            onClick = {
                                showMenu = false
                                onToggleSaveOffline()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(if (post.isPinned) "Desfijar del muro" else "Fijar al inicio")
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.PushPin, contentDescription = null)
                            },
                            onClick = {
                                showMenu = false
                                onTogglePin()
                            }
                        )
                        if (onEditPost != null) {
                            DropdownMenuItem(
                                text = { Text("Editar publicación") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Edit, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    onEditPost()
                                }
                            )
                        }
                        if (onToggleWatched != null && post.mediaItems.any { it.type == MediaType.VIDEO }) {
                            val isWatched = watchProgress?.isWatched == true
                            DropdownMenuItem(
                                text = {
                                    Text(if (isWatched) "Marcar como no visto" else "Marcar como visto")
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (isWatched) LanOnlineGreen else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onToggleWatched()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Eliminar publicación") },
                            leadingIcon = {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color.Red)
                            },
                            onClick = {
                                showMenu = false
                                onDeletePost()
                            }
                        )
                    }
                }
            }

            // 2. Post Content Text (con resaltado de búsqueda cuando hay query activa)
            if (post.contentText.isNotBlank()) {
                val displayAnnotatedText = remember(post.contentText, highlightQuery) {
                    if (highlightQuery.isNotBlank()) {
                        SearchHelper.buildHighlightedText(
                            text = post.contentText,
                            query = highlightQuery,
                            highlightColor = FacebookBlue,
                            highlightBackground = FacebookBlue.copy(alpha = 0.16f)
                        )
                    } else null
                }
                if (displayAnnotatedText != null) {
                    Text(
                        text = displayAnnotatedText,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.5.sp,
                            lineHeight = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                } else {
                    Text(
                        text = post.contentText,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.5.sp,
                            lineHeight = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            // 3. Full-bleed Media Container (Images or Videos) - Facebook Style Dynamic Sizing & Mosaic
            if (post.mediaItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                if (post.mediaItems.size == 1) {
                    val media = post.mediaItems.first()
                    val isVideo = media.type == MediaType.VIDEO
                    val imageModel = remember(media.thumbnailUrl, media.url, fallbackFolderImageUrl, thumbTick) {
                        com.example.util.OfflineMediaManager.getDisplayModel(
                            context = context,
                            url = if (isVideo) media.thumbnailUrl else media.url,
                            isVideo = isVideo,
                            fallbackFolderImageUrl = fallbackFolderImageUrl,
                            videoUrl = media.url
                        )
                    }
                    val isChunked = media.isChunked && media.chunks.isNotEmpty()

                    val effectiveStartChunk = if (lastChunkIndex == 0 && lastPositionMs == 0L && watchProgress != null && !watchProgress.isWatched) {
                        watchProgress.chunkIndex
                    } else lastChunkIndex
                    val effectiveStartPos = if (lastChunkIndex == 0 && lastPositionMs == 0L && watchProgress != null && !watchProgress.isWatched) {
                        watchProgress.positionMs
                    } else lastPositionMs

                    if (isVideo && isPlayingInline) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .background(Color.Black.copy(alpha = 0.08f))
                        ) {
                            // Reproducción continua multi-fragmento con barra unificada y soporte offline
                            FamilyVideoPlayer(
                                videoUrl = media.url,
                                chunks = media.chunks,
                                chunkDurationsMs = media.chunkDurationsMs,
                                totalDurationMsHint = media.totalDurationMs,
                                durationLabel = media.durationLabel,
                                isChunked = isChunked,
                                autoPlay = true,
                                isLooping = true,
                                showUnifiedBar = true,
                                initialChunkIndex = effectiveStartChunk,
                                initialPositionMs = effectiveStartPos,
                                onPositionChanged = { idx, pos ->
                                    lastChunkIndex = idx
                                    lastPositionMs = pos
                                },
                                onVideoStarted = {
                                    onVideoStarted?.invoke(post.id, media.url, media.chunks.firstOrNull())
                                },
                                onFullscreenWithPosition = { chunkIdx, posMs ->
                                    lastChunkIndex = chunkIdx
                                    lastPositionMs = posMs
                                    isPlayingInline = false
                                    isPaused = true
                                    onPlayVideo(
                                        media.url,
                                        post.contentText.take(40),
                                        post.author.name,
                                        media.durationLabel,
                                        media.chunks,
                                        media.chunkDurationsMs,
                                        media.totalDurationMs,
                                        chunkIdx,
                                        posMs
                                    )
                                },
                                onCloseClick = {
                                    isPlayingInline = false
                                    isPaused = false
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else if (isVideo) {
                        // Video preview with play overlay (usa miniatura generada o imagen de la carpeta; nunca descarga el vídeo)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color(0xFF141B29), Color(0xFF1E293B))
                                    )
                                )
                        ) {
                            if (imageModel != null) {
                                val thumbRequest = remember(imageModel) {
                                    ImageRequest.Builder(context)
                                        .data(imageModel)
                                        .size(640)
                                        .precision(Precision.INEXACT)
                                        .crossfade(true)
                                        .build()
                                }
                                AsyncImage(
                                    model = thumbRequest,
                                    contentDescription = media.caption.ifBlank { "Miniatura de vídeo" },
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable {
                                            isPlayingInline = true
                                            isBuffering = true
                                            isPaused = false
                                        }
                                )
                            } else {
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Surface(
                                        color = Color.White.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Movie,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = "Reproduce para generar vista previa",
                                                color = Color.White.copy(alpha = 0.9f),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.35f))
                                    .clickable {
                                        isPlayingInline = true
                                        isBuffering = true
                                        isPaused = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.7f))
                                        .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Reproducir vídeo en tarjeta",
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.8f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (isChunked) "⚡ Vídeo continuo (${media.chunks.size} partes) · ${media.durationLabel ?: "Streaming"}" else "Vídeo LAN · ${media.durationLabel ?: "Streaming"}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        isPlayingInline = false
                                        isPaused = true
                                        onPlayVideo(
                                            media.url,
                                            post.contentText.take(40),
                                            post.author.name,
                                            media.durationLabel,
                                            media.chunks,
                                            media.chunkDurationsMs,
                                            media.totalDurationMs,
                                            effectiveStartChunk,
                                            effectiveStartPos
                                        )
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.8f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fullscreen,
                                        contentDescription = "Pantalla completa",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Insignia "✓ Visto" o barra de progreso en vista previa
                            if (watchProgress?.isWatched == true) {
                                Surface(
                                    color = LanOnlineGreen.copy(alpha = 0.92f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(10.dp)
                                        .testTag("video_watched_badge_${post.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Vídeo visto",
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Visto",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else if (watchProgress != null && watchProgress.progressFraction > 0.02f) {
                                LinearProgressIndicator(
                                    progress = { watchProgress.progressFraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .align(Alignment.BottomCenter)
                                        .testTag("video_progress_bar_${post.id}"),
                                    color = FacebookBlue,
                                    trackColor = Color.White.copy(alpha = 0.35f)
                                )
                            }
                        }
                    } else {
                        // Single Photo - Dynamic Facebook sizing, tap opens split viewer
                        val singlePhotoRequest = remember(imageModel) {
                            ImageRequest.Builder(context)
                                .data(imageModel)
                                .size(640)
                                .precision(Precision.INEXACT)
                                .crossfade(true)
                                .build()
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 220.dp, max = 460.dp)
                                .background(Color.Black.copy(alpha = 0.08f))
                                .clickable { activeMediaViewerIndex = 0 }
                        ) {
                            AsyncImage(
                                model = singlePhotoRequest,
                                contentDescription = media.caption.ifBlank { "Foto de publicación" },
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 220.dp, max = 460.dp)
                            )
                        }
                    }
                } else {
                    // Multi-photo Facebook Dynamic Mosaic Grid
                    FacebookMediaGrid(
                        mediaItems = post.mediaItems,
                        fallbackFolderImageUrl = fallbackFolderImageUrl,
                        thumbTick = thumbTick,
                        onMediaClick = { index -> activeMediaViewerIndex = index }
                    )
                }
            }

            // 4. Reactions & Engagement Stats Bar (Facebook style)
            val totalReactions = post.reactions.values.sum()
            if (totalReactions > 0 || post.comments.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Overlapping circular reaction badges (up to top 3) + Count
                    if (totalReactions > 0) {
                        val topReactions = remember(post.reactions) {
                            post.reactions.entries
                                .filter { it.value > 0 }
                                .sortedByDescending { it.value }
                                .take(3)
                                .map { it.key }
                                .ifEmpty { listOf(ReactionType.LIKE) }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showReactionPicker = !showReactionPicker }
                                .padding(vertical = 2.dp, horizontal = 2.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                                topReactions.forEach { rx ->
                                    val badgeColor = when (rx) {
                                        ReactionType.LIKE -> ReactionLikeBlue
                                        ReactionType.LOVE -> ReactionLoveRed
                                        else -> ReactionHahaYellow
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(badgeColor)
                                            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(rx.emoji, fontSize = 10.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(7.dp))

                            Text(
                                text = totalReactions.toString(),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    // Right: Comments count ("X comentarios")
                    if (post.comments.isNotEmpty()) {
                        val label = if (post.comments.size == 1) "1 comentario" else "${post.comments.size} comentarios"
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onCommentClick() }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
                thickness = 0.6.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // Animated Facebook Reactions Capsule Popup
            AnimatedVisibility(
                visible = showReactionPicker,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ReactionType.values().forEach { rx ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onReactionClick(rx)
                                        showReactionPicker = false
                                    }
                                    .padding(4.dp)
                            ) {
                                Text(text = rx.emoji, fontSize = 28.sp)
                                Text(
                                    text = rx.label,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 5. The 3 Action Buttons: Me gusta (tap or long-press reactions), Comentar, Descargar Offline
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hasUserReacted = post.userReaction != null
                val userRx = post.userReaction

                // 1. Me gusta (Tap: Like, Long-press: Selector de reacciones Facebook)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(
                            onClick = {
                                if (hasUserReacted) {
                                    onReactionClick(userRx!!)
                                } else {
                                    onReactionClick(ReactionType.LIKE)
                                }
                            },
                            onLongClick = { showReactionPicker = !showReactionPicker }
                        )
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                        .testTag("reaction_button_${post.id}")
                ) {
                    if (hasUserReacted) {
                        Text(text = userRx!!.emoji, fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = userRx.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = when (userRx) {
                                    ReactionType.LIKE -> FacebookBlue
                                    ReactionType.LOVE -> ReactionLoveRed
                                    else -> ReactionHahaYellow
                                },
                                fontSize = 13.sp
                            )
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.ThumbUp,
                            contentDescription = "Me gusta (mantén pulsado para más reacciones)",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Me gusta",
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        )
                    }
                }

                // 2. Comentar (Comment Button)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onCommentClick() }
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                        .testTag("comment_button_${post.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Comentar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Comentar",
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    )
                }

                // 3. Descarga Offline en segundo plano (En espera / % progreso / Guardado / Descargar)
                val isQueued = downloadState?.status == DownloadItemStatus.QUEUED
                val isDownloading = downloadState?.status == DownloadItemStatus.DOWNLOADING
                val downloadTag = when {
                    isQueued -> "offline_queued_button_${post.id}"
                    isDownloading -> "offline_downloading_button_${post.id}"
                    else -> "offline_download_button_${post.id}"
                }
                val downloadBg = when {
                    isDownloading -> FacebookBlue.copy(alpha = 0.1f)
                    isQueued -> LanOfflineAmber.copy(alpha = 0.12f)
                    post.isSavedOffline -> LanOnlineGreen.copy(alpha = 0.08f)
                    else -> Color.Transparent
                }
                val downloadTint = when {
                    isDownloading -> FacebookBlue
                    isQueued -> LanOfflineAmber
                    post.isSavedOffline -> LanOnlineGreen
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val downloadLabel = when {
                    isDownloading -> "${downloadState?.progressPercent ?: 0}%"
                    isQueued -> "En espera"
                    post.isSavedOffline -> "Guardado"
                    else -> "Descargar"
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(downloadBg)
                        .clickable { onToggleSaveOffline() }
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                        .testTag(downloadTag),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        when {
                            isDownloading -> {
                                CircularProgressIndicator(
                                    progress = { (downloadState?.progressPercent ?: 0) / 100f },
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = FacebookBlue
                                )
                            }
                            isQueued -> {
                                Icon(
                                    imageVector = Icons.Outlined.Schedule,
                                    contentDescription = "En espera en la cola de descarga",
                                    tint = downloadTint,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            else -> {
                                Icon(
                                    imageVector = if (post.isSavedOffline) Icons.Default.CloudDone else Icons.Outlined.CloudDownload,
                                    contentDescription = if (post.isSavedOffline) "Guardado offline" else "Descargar para ver offline",
                                    tint = downloadTint,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = downloadLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = downloadTint,
                                fontWeight = if (post.isSavedOffline || isDownloading || isQueued) FontWeight.Bold else FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        )
                    }
                    if (isDownloading) {
                        Spacer(modifier = Modifier.height(3.dp))
                        LinearProgressIndicator(
                            progress = { (downloadState?.progressPercent ?: 0) / 100f },
                            modifier = Modifier
                                .fillMaxWidth(0.82f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = FacebookBlue,
                            trackColor = FacebookBlue.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }
    }

    // Modal Visor de Fotos Estilo Facebook con Vista Dividida (Foto + Comentarios simultáneos)
    activeMediaViewerIndex?.let { initialIndex ->
        FacebookMediaViewerDialog(
            post = post,
            initialMediaIndex = initialIndex,
            onDismiss = { activeMediaViewerIndex = null },
            onReactionClick = onReactionClick,
            onSendComment = onSendComment
        )
    }
}

@Composable
private fun FacebookMediaGrid(
    mediaItems: List<com.example.model.MediaItem>,
    fallbackFolderImageUrl: String?,
    thumbTick: Int,
    onMediaClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val count = mediaItems.size
    when (count) {
        2 -> {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .height(230.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                FacebookGridItem(
                    media = mediaItems[0],
                    fallbackFolderImageUrl = fallbackFolderImageUrl,
                    thumbTick = thumbTick,
                    onClick = { onMediaClick(0) },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                FacebookGridItem(
                    media = mediaItems[1],
                    fallbackFolderImageUrl = fallbackFolderImageUrl,
                    thumbTick = thumbTick,
                    onClick = { onMediaClick(1) },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
        3 -> {
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                FacebookGridItem(
                    media = mediaItems[0],
                    fallbackFolderImageUrl = fallbackFolderImageUrl,
                    thumbTick = thumbTick,
                    onClick = { onMediaClick(0) },
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    FacebookGridItem(
                        media = mediaItems[1],
                        fallbackFolderImageUrl = fallbackFolderImageUrl,
                        thumbTick = thumbTick,
                        onClick = { onMediaClick(1) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    FacebookGridItem(
                        media = mediaItems[2],
                        fallbackFolderImageUrl = fallbackFolderImageUrl,
                        thumbTick = thumbTick,
                        onClick = { onMediaClick(2) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
        4 -> {
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                FacebookGridItem(
                    media = mediaItems[0],
                    fallbackFolderImageUrl = fallbackFolderImageUrl,
                    thumbTick = thumbTick,
                    onClick = { onMediaClick(0) },
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().height(125.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    FacebookGridItem(
                        media = mediaItems[1],
                        fallbackFolderImageUrl = fallbackFolderImageUrl,
                        thumbTick = thumbTick,
                        onClick = { onMediaClick(1) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    FacebookGridItem(
                        media = mediaItems[2],
                        fallbackFolderImageUrl = fallbackFolderImageUrl,
                        thumbTick = thumbTick,
                        onClick = { onMediaClick(2) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    FacebookGridItem(
                        media = mediaItems[3],
                        fallbackFolderImageUrl = fallbackFolderImageUrl,
                        thumbTick = thumbTick,
                        onClick = { onMediaClick(3) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
        else -> { // count >= 5
            val remainingCount = count - 3
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                FacebookGridItem(
                    media = mediaItems[0],
                    fallbackFolderImageUrl = fallbackFolderImageUrl,
                    thumbTick = thumbTick,
                    onClick = { onMediaClick(0) },
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().height(125.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    FacebookGridItem(
                        media = mediaItems[1],
                        fallbackFolderImageUrl = fallbackFolderImageUrl,
                        thumbTick = thumbTick,
                        onClick = { onMediaClick(1) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    FacebookGridItem(
                        media = mediaItems[2],
                        fallbackFolderImageUrl = fallbackFolderImageUrl,
                        thumbTick = thumbTick,
                        onClick = { onMediaClick(2) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    FacebookGridItem(
                        media = mediaItems[3],
                        fallbackFolderImageUrl = fallbackFolderImageUrl,
                        thumbTick = thumbTick,
                        onClick = { onMediaClick(3) },
                        overlayText = "+$remainingCount",
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun FacebookGridItem(
    media: com.example.model.MediaItem,
    fallbackFolderImageUrl: String?,
    thumbTick: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    overlayText: String? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isVideo = media.type == MediaType.VIDEO
    val imageModel = remember(media.thumbnailUrl, media.url, fallbackFolderImageUrl, thumbTick) {
        com.example.util.OfflineMediaManager.getDisplayModel(
            context = context,
            url = if (isVideo) media.thumbnailUrl else media.url,
            isVideo = isVideo,
            fallbackFolderImageUrl = fallbackFolderImageUrl,
            videoUrl = media.url
        )
    }

    Box(
        modifier = modifier
            .background(if (isVideo && imageModel == null) Color(0xFF141B29) else Color.Black.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
    ) {
        if (imageModel != null) {
            val gridRequest = remember(imageModel) {
                ImageRequest.Builder(context)
                    .data(imageModel)
                    .size(480)
                    .precision(Precision.INEXACT)
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = gridRequest,
                contentDescription = media.caption.ifBlank { "Foto de publicación" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.25f),
                modifier = Modifier
                    .size(34.dp)
                    .align(Alignment.Center)
            )
        }

        if (isVideo) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Vídeo",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        if (overlayText != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = overlayText,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun formatFacebookRelativeTime(timestamp: Long): String {
    val diffMs = System.currentTimeMillis() - timestamp
    val diffSec = diffMs / 1000
    val diffMin = diffSec / 60
    val diffHours = diffMin / 60
    val diffDays = diffHours / 24

    return when {
        diffMin < 1 -> "Hace un momento"
        diffMin < 60 -> "${diffMin} min"
        diffHours < 24 -> "${diffHours} h"
        diffDays == 1L -> "Ayer"
        else -> "${diffDays} d"
    }
}
