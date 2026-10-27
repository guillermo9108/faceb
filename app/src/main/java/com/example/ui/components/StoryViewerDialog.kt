package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.model.StoryType
import com.example.model.UserStoryGroup
import kotlinx.coroutines.delay

@Composable
fun StoryViewerDialog(
    group: UserStoryGroup,
    onDismiss: () -> Unit
) {
    if (group.stories.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    var currentIndex by remember { mutableIntStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    var canAcceptTap by remember { mutableStateOf(false) }
    var currentProgress by remember(currentIndex) { mutableFloatStateOf(0f) }

    val currentStory = group.stories.getOrNull(currentIndex) ?: group.stories.first()

    // Evitar que el toque que abrió la historia cierre el visor por accidente
    LaunchedEffect(Unit) {
        delay(350)
        canAcceptTap = true
    }

    // Temporizador suave y estable de 5 segundos
    LaunchedEffect(currentIndex, isPaused) {
        val totalMs = 5000L
        val tickMs = 40L
        while (currentProgress < 1f) {
            if (!isPaused) {
                delay(tickMs)
                currentProgress = (currentProgress + (tickMs.toFloat() / totalMs)).coerceAtMost(1f)
            } else {
                delay(80L)
            }
        }

        if (currentIndex < group.stories.size - 1) {
            currentIndex++
            currentProgress = 0f
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("story_viewer_dialog")
                .pointerInput(currentIndex) {
                    detectTapGestures(
                        onPress = {
                            isPaused = true
                            tryAwaitRelease()
                            isPaused = false
                        },
                        onTap = { offset ->
                            if (!canAcceptTap) return@detectTapGestures
                            val screenWidth = size.width
                            if (offset.x < screenWidth * 0.35f) {
                                // Retroceder
                                if (currentIndex > 0) {
                                    currentIndex--
                                    currentProgress = 0f
                                } else {
                                    currentProgress = 0f
                                }
                            } else {
                                // Avanzar
                                if (currentIndex < group.stories.size - 1) {
                                    currentIndex++
                                    currentProgress = 0f
                                } else {
                                    onDismiss()
                                }
                            }
                        }
                    )
                }
        ) {
            // Contenido visual de la historia
            when (currentStory.storyType) {
                StoryType.TEXT -> {
                    // Historia en texto con degradado
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(currentStory.backgroundColorHex),
                                        Color(currentStory.backgroundColorHex).copy(alpha = 0.85f),
                                        Color.Black.copy(alpha = 0.9f)
                                    )
                                )
                            )
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentStory.caption.ifBlank { currentStory.title },
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                lineHeight = 36.sp
                            )
                        )
                    }
                }
                else -> {
                    // Historia con imagen o vídeo
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = currentStory.mediaUrl.ifBlank { currentStory.imageUrl },
                            contentDescription = currentStory.caption,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Sombra superior e inferior para legibilidad
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.65f),
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.8f)
                                        )
                                    )
                                )
                        )

                        // Pie de foto
                        if (currentStory.caption.isNotBlank()) {
                            Text(
                                text = currentStory.caption,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    shadow = androidx.compose.ui.graphics.Shadow(
                                        color = Color.Black,
                                        blurRadius = 6f
                                    )
                                ),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 24.dp, vertical = 40.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Barra superior de progreso e información del autor
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 20.dp)
            ) {
                // Barras de progreso segmentadas
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    group.stories.forEachIndexed { index, _ ->
                        val barProgress = when {
                            index < currentIndex -> 1f
                            index == currentIndex -> currentProgress
                            else -> 0f
                        }
                        LinearProgressIndicator(
                            progress = { barProgress },
                            modifier = Modifier
                                .weight(1f)
                                .height(3.5.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.35f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Cabecera: Avatar, nombre y botón cerrar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(group.author.avatarColorHex)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = group.author.avatarEmoji, fontSize = 20.sp)
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = group.author.name,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "${group.author.role} · Historia familiar LAN",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar visor de historias",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
