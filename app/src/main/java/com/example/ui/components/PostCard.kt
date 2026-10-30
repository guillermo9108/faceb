package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import android.net.Uri
import android.widget.VideoView
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.model.MediaType
import com.example.model.Post
import com.example.model.ReactionType
import com.example.ui.theme.FacebookBlue
import com.example.ui.theme.FacebookCardLight
import com.example.ui.theme.ReactionHahaYellow
import com.example.ui.theme.ReactionLikeBlue
import com.example.ui.theme.ReactionLoveRed

@Composable
fun PostCard(
    post: Post,
    onReactionClick: (ReactionType) -> Unit,
    onCommentClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDeletePost: () -> Unit,
    onPlayVideo: (videoUrl: String, title: String, authorName: String, durationLabel: String?) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showReactionPicker by remember { mutableStateOf(false) }
    var isPlayingInline by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var inlineVideoView by remember { mutableStateOf<VideoView?>(null) }

    DisposableEffect(isPlayingInline) {
        onDispose {
            inlineVideoView?.stopPlayback()
            inlineVideoView = null
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.5.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("post_card_${post.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // Pinned Banner (Destacado familiar)
            if (post.isPinned) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FacebookBlue.copy(alpha = 0.08f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Fijado",
                        tint = FacebookBlue,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Publicación destacada de la familia",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = FacebookBlue,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            // 1. Post Header: Avatar, Name, Relative Time, Privacy Globe, Options
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Author Avatar (40dp circle)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(post.author.avatarColorHex)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = post.author.avatarEmoji, fontSize = 20.sp)
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Name & Subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = post.author.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
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
                    }
                }

                // Top Right More Options (···)
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(36.dp)
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

            // 2. Post Content Text
            if (post.contentText.isNotBlank()) {
                Text(
                    text = post.contentText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        lineHeight = 21.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }

            // 3. Full-bleed Media Container (Images or Videos)
            if (post.mediaItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                val media = post.mediaItems.first()
                val isVideo = media.type == MediaType.VIDEO
                val imageModel = remember(media.thumbnailUrl, media.url) {
                    val raw = if (isVideo) media.thumbnailUrl.ifBlank { media.url } else media.url
                    com.example.util.OfflineMediaManager.getDisplayModel(context, raw)
                }
                val playableVideoUri = remember(media.url) {
                    com.example.util.OfflineMediaManager.getPlayableMediaUri(context, media.url)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black.copy(alpha = 0.08f))
                ) {
                    if (isVideo && isPlayingInline) {
                        // Reproducción directa en la misma tarjeta
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    setVideoURI(playableVideoUri)
                                    setOnPreparedListener { mp ->
                                        isBuffering = false
                                        mp.isLooping = true
                                        start()
                                    }
                                    setOnErrorListener { _, _, _ ->
                                        isBuffering = false
                                        isPlayingInline = false
                                        false
                                    }
                                    inlineVideoView = this
                                }
                            },
                            update = { vv ->
                                inlineVideoView = vv
                                if (isPaused) {
                                    if (vv.isPlaying) vv.pause()
                                } else {
                                    if (!vv.isPlaying && !isBuffering) vv.start()
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable {
                                    isPaused = !isPaused
                                }
                        )

                        // Indicador de carga / buffering
                        if (isBuffering) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                            }
                        }

                        // Overlay con botón para cerrar vídeo inline
                        IconButton(
                            onClick = {
                                isPlayingInline = false
                                inlineVideoView?.stopPlayback()
                                inlineVideoView = null
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar reproducción inline",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Barra inferior de controles inline
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(Color.Black.copy(alpha = 0.65f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = { isPaused = !isPaused },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                        contentDescription = if (isPaused) "Reanudar" else "Pausar",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                    text = if (isPaused) "Pausado" else "Reproduciendo en tarjeta",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                )
                            }

                            IconButton(
                                onClick = {
                                    isPlayingInline = false
                                    isPaused = true
                                    inlineVideoView?.stopPlayback()
                                    inlineVideoView = null
                                    onPlayVideo(media.url, post.contentText.take(40), post.author.name, media.durationLabel)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Pantalla completa",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else {
                        // Vista previa de imagen o miniatura de vídeo
                        AsyncImage(
                            model = imageModel,
                            contentDescription = media.caption.ifBlank { "Foto de publicación" },
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(enabled = isVideo) {
                                    isPlayingInline = true
                                    isBuffering = true
                                    isPaused = false
                                }
                        )

                        // Superposición de vídeo con botón reproducir en tarjeta
                        if (isVideo) {
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
                                        text = "Vídeo LAN · ${media.durationLabel ?: "Streaming"}",
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
                                        inlineVideoView?.stopPlayback()
                                        inlineVideoView = null
                                        onPlayVideo(media.url, post.contentText.take(40), post.author.name, media.durationLabel)
                                    },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.8f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fullscreen,
                                        contentDescription = "Pantalla completa",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Reactions & Engagement Stats Bar (Facebook style)
            val totalReactions = post.reactions.values.sum()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Overlapping circular reaction badges (👍 ❤️ 😆) + Count
                if (totalReactions > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Badge 1: Like Thumb
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(ReactionLikeBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("👍", fontSize = 11.sp)
                        }

                        // Badge 2: Love Heart (if active)
                        if (post.reactions.containsKey(ReactionType.LOVE)) {
                            Box(
                                modifier = Modifier
                                    .padding(start = 2.dp)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(ReactionLoveRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("❤️", fontSize = 10.sp)
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = totalReactions.toString(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                // Right: Comments count ("X comentarios")
                if (post.comments.isNotEmpty()) {
                    Text(
                        text = "${post.comments.size} comentarios",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        ),
                        modifier = Modifier.clickable { onCommentClick() }
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = 14.dp)
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

            // 5. The 3 Iconic Facebook Action Buttons (Me gusta, Comentar, Compartir)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hasUserReacted = post.userReaction != null
                val userRx = post.userReaction

                // 1. Me gusta (Like Button)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            if (hasUserReacted) {
                                onReactionClick(userRx!!)
                            } else {
                                onReactionClick(ReactionType.LIKE)
                            }
                        }
                        .padding(vertical = 8.dp)
                        .testTag("reaction_button_${post.id}")
                ) {
                    if (hasUserReacted) {
                        Text(text = userRx!!.emoji, fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = userRx.label,
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
                            contentDescription = "Me gusta",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Me gusta",
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
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onCommentClick() }
                        .padding(vertical = 8.dp)
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
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    )
                }

                // 3. Compartir (Share Button)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { showReactionPicker = !showReactionPicker }
                        .padding(vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Reply,
                        contentDescription = "Compartir / Reaccionar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Compartir",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    )
                }
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
