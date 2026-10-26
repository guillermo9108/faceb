package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.model.StoryType
import com.example.model.UserStoryGroup

@Composable
fun StoryViewerDialog(
    group: UserStoryGroup,
    onDismiss: () -> Unit
) {
    if (group.stories.isEmpty()) {
        onDismiss()
        return
    }

    var currentIndex by remember { mutableIntStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    val currentStory = group.stories.getOrNull(currentIndex) ?: group.stories.first()

    val progressAnim = remember(currentIndex) { Animatable(0f) }

    // Animación de la barra de progreso de 5 segundos con auto-avance
    LaunchedEffect(currentIndex, isPaused) {
        if (!isPaused) {
            val remaining = (1f - progressAnim.value).coerceIn(0f, 1f)
            val duration = (5000L * remaining).toInt().coerceAtLeast(100)
            val result = progressAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = duration, easing = LinearEasing)
            )
            if (result.endReason == androidx.compose.animation.core.AnimationEndReason.Finished) {
                if (currentIndex < group.stories.size - 1) {
                    currentIndex++
                } else {
                    onDismiss()
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("story_viewer_dialog")
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPaused = true
                            tryAwaitRelease()
                            isPaused = false
                        },
                        onTap = { offset ->
                            val screenWidth = size.width
                            if (offset.x < screenWidth * 0.35f) {
                                // Retroceder historia
                                if (currentIndex > 0) {
                                    currentIndex--
                                }
                            } else {
                                // Avanzar historia
                                if (currentIndex < group.stories.size - 1) {
                                    currentIndex++
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
                    // Historia en texto: degradado de color y tipografía grande
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
                                            Color.Black.copy(alpha = 0.6f),
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.75f)
                                        )
                                    )
                                )
                        )

                        // Pie de foto / texto de la historia
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

            // Top Bar: Barras de progreso segmentadas + Autor + Cerrar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 16.dp)
            ) {
                // Segmented Progress Bars
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    group.stories.forEachIndexed { index, _ ->
                        val progress = when {
                            index < currentIndex -> 1f
                            index == currentIndex -> progressAnim.value
                            else -> 0f
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.35f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // User Info Header
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
                                text = "${group.author.role} · Historia familiar",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar historia",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
