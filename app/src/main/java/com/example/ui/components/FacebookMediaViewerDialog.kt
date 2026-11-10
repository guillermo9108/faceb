package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.model.Comment
import com.example.model.MediaItem
import com.example.model.MediaType
import com.example.model.Post
import com.example.model.ReactionType
import com.example.ui.theme.FacebookBlue
import com.example.ui.theme.LanOnlineGreen
import com.example.ui.theme.ReactionLoveRed
import com.example.util.OfflineMediaManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FacebookMediaViewerDialog(
    post: Post,
    initialMediaIndex: Int = 0,
    onDismiss: () -> Unit,
    onReactionClick: (ReactionType) -> Unit,
    onSendComment: (String) -> Unit
) {
    val context = LocalContext.current
    val mediaCount = post.mediaItems.size
    val safeInitial = initialMediaIndex.coerceIn(0, (mediaCount - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = safeInitial, pageCount = { mediaCount.coerceAtLeast(1) })

    var commentInput by remember { mutableStateOf("") }
    var showReactionMenu by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("facebook_media_viewer_dialog")
        ) {
            val isWideScreen = maxWidth > 680.dp

            if (isWideScreen) {
                // Diseño Pantalla Dividida Horizontal (Tablet / Horizontal)
                Row(modifier = Modifier.fillMaxSize()) {
                    // Lado Izquierdo: Visor de Imagen con fondo negro (65%)
                    Box(
                        modifier = Modifier
                            .weight(0.65f)
                            .fillMaxHeight()
                            .background(Color.Black)
                    ) {
                        ImageViewerPager(
                            mediaItems = post.mediaItems,
                            pagerState = pagerState,
                            onClose = onDismiss,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Lado Derecho: Panel Social Facebook (35%)
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .weight(0.35f)
                            .fillMaxHeight()
                    ) {
                        SocialDetailsPanel(
                            post = post,
                            commentInput = commentInput,
                            onCommentInputChange = { commentInput = it },
                            onSendComment = {
                                if (commentInput.isNotBlank()) {
                                    onSendComment(commentInput.trim())
                                    commentInput = ""
                                }
                            },
                            onReactionClick = onReactionClick,
                            showReactionMenu = showReactionMenu,
                            onToggleReactionMenu = { showReactionMenu = !showReactionMenu },
                            onClose = onDismiss,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
                // Diseño Pantalla Dividida Vertical (Móvil / Vertical)
                Column(modifier = Modifier.fillMaxSize()) {
                    // Mitad Superior: Visor de Imagen con fondo negro (55%)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.55f)
                            .background(Color.Black)
                    ) {
                        ImageViewerPager(
                            mediaItems = post.mediaItems,
                            pagerState = pagerState,
                            onClose = onDismiss,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Mitad Inferior: Panel Social Facebook con comentarios (45%)
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.45f)
                    ) {
                        SocialDetailsPanel(
                            post = post,
                            commentInput = commentInput,
                            onCommentInputChange = { commentInput = it },
                            onSendComment = {
                                if (commentInput.isNotBlank()) {
                                    onSendComment(commentInput.trim())
                                    commentInput = ""
                                }
                            },
                            onReactionClick = onReactionClick,
                            showReactionMenu = showReactionMenu,
                            onToggleReactionMenu = { showReactionMenu = !showReactionMenu },
                            onClose = onDismiss,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageViewerPager(
    mediaItems: List<MediaItem>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val total = mediaItems.size

    Box(modifier = modifier) {
        if (mediaItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay fotos en esta publicación", color = Color.White)
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val item = mediaItems.getOrNull(page)
                if (item != null) {
                    ZoomableMediaImage(
                        mediaItem = item,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Barra Superior sobre la foto (Botón cerrar ✕, Contador "2 de 5")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (total > 1) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} de $total",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(38.dp))
            }
        }
    }
}

@Composable
private fun ZoomableMediaImage(
    mediaItem: MediaItem,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val displayModel = remember(mediaItem.url, mediaItem.thumbnailUrl) {
        val isVid = mediaItem.type == MediaType.VIDEO
        val raw = if (isVid) mediaItem.thumbnailUrl.ifBlank { mediaItem.url } else mediaItem.url
        OfflineMediaManager.getDisplayModel(context, raw, isVid)
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 4f)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = displayModel,
            contentDescription = "Foto ampliada",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
    }
}

@Composable
private fun SocialDetailsPanel(
    post: Post,
    commentInput: String,
    onCommentInputChange: (String) -> Unit,
    onSendComment: () -> Unit,
    onReactionClick: (ReactionType) -> Unit,
    showReactionMenu: Boolean,
    onToggleReactionMenu: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalReactions = post.reactions.values.sum()
    val isLiked = post.userReaction != null

    Column(modifier = modifier) {
        // Encabezado del autor estilo Facebook
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(post.author.avatarColorHex)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = post.author.avatarEmoji.ifBlank { "👤" },
                    fontSize = 22.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = post.author.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatRelativeTime(post.createdAt),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = " · ",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = "LAN Familiar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        // Texto del post si existe
        if (post.contentText.isNotBlank()) {
            Text(
                text = post.contentText,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Barra de Resumen de Reacciones y Comentarios
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (totalReactions > 0) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(FacebookBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$totalReactions",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "${post.comments.size} comentarios",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
        )

        // Botones de Acción ("Me gusta" y "Comentar")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        if (isLiked) {
                            onReactionClick(post.userReaction ?: ReactionType.LIKE)
                        } else {
                            onReactionClick(ReactionType.LIKE)
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                    contentDescription = "Me gusta",
                    tint = if (isLiked) FacebookBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = post.userReaction?.label ?: "Me gusta",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isLiked) FacebookBlue else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = "Comentar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Comentar",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
        )

        // Lista de Comentarios en Vivo
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (post.comments.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Sé el primero en comentar esta foto familiar.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(post.comments, key = { it.id }) { c ->
                    CommentBubble(comment = c)
                }
            }
        }

        // Campo de entrada para escribir comentario al instante
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = commentInput,
                    onValueChange = onCommentInputChange,
                    placeholder = { Text("Escribe un comentario...", fontSize = 13.sp) },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FacebookBlue,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("viewer_comment_input")
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onSendComment,
                    enabled = commentInput.isNotBlank(),
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (commentInput.isNotBlank()) FacebookBlue else MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Enviar",
                        tint = if (commentInput.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentBubble(comment: Comment) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(comment.author.avatarColorHex)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = comment.author.avatarEmoji.ifBlank { "👤" },
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(
                        text = comment.author.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = comment.text,
                        fontSize = 13.sp,
                        lineHeight = 17.sp
                    )
                }
            }
            Text(
                text = formatRelativeTime(comment.createdAt),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "Hace un momento"
        minutes < 60 -> "Hace ${minutes}m"
        hours < 24 -> "Hace ${hours}h"
        days < 7 -> "Hace ${days}d"
        else -> {
            val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
