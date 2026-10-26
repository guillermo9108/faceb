package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.model.FamilyMember
import com.example.model.StoryType
import com.example.ui.theme.FacebookBlue

@Composable
fun CreateStoryDialog(
    currentMember: FamilyMember,
    onDismiss: () -> Unit,
    onSubmitStory: (caption: String, storyType: StoryType, bgColorHex: Long, mediaUri: Uri?) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Foto/Video, 1: Texto
    var storyCaption by remember { mutableStateOf("") }
    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var selectedBgColor by remember { mutableLongStateOf(0xFF1E40AF) } // Facebook Blue

    val colorPalette = listOf(
        0xFF1E40AF, // Azul clásico
        0xFF9333EA, // Púrpura sunset
        0xFF059669, // Verde esmeralda
        0xFFDC2626, // Rojo rubí
        0xFFD97706, // Ámbar cálido
        0xFF0F172A, // Medianoche oscuro
        0xFFDB2777  // Rosa intenso
    )

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedMediaUri = uri
            selectedTab = 0
        }
    }

    val canPublish = if (selectedTab == 0) selectedMediaUri != null else storyCaption.isNotBlank()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .padding(vertical = 20.dp)
                .testTag("create_story_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Header Bar (Cerrar '✕', Título, Botón "COMPARTIR")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", modifier = Modifier.size(18.dp))
                    }

                    Text(
                        text = "Crear historia familiar",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    )

                    Button(
                        onClick = {
                            if (canPublish) {
                                val type = if (selectedTab == 0) StoryType.IMAGE else StoryType.TEXT
                                onSubmitStory(storyCaption, type, selectedBgColor, selectedMediaUri)
                            }
                        },
                        enabled = canPublish,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FacebookBlue,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("submit_story_button")
                    ) {
                        Text(
                            text = "COMPARTIR",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (canPublish) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Selector de modo: Foto/Video o Texto
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = FacebookBlue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Foto / Video")
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Texto")
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Área de previsualización de la historia
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (selectedTab == 1 || selectedMediaUri == null) {
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(selectedBgColor),
                                        Color(selectedBgColor).copy(alpha = 0.75f),
                                        Color.Black.copy(alpha = 0.85f)
                                    )
                                )
                            } else {
                                Brush.verticalGradient(listOf(Color.Black, Color.Black))
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedTab == 0 && selectedMediaUri != null) {
                        AsyncImage(
                            model = selectedMediaUri,
                            contentDescription = "Previsualización",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Botón para cambiar foto
                        IconButton(
                            onClick = { selectedMediaUri = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Quitar foto", tint = Color.White, modifier = Modifier.size(16.dp))
                        }

                        if (storyCaption.isNotBlank()) {
                            Text(
                                text = storyCaption,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    shadow = androidx.compose.ui.graphics.Shadow(Color.Black, blurRadius = 6f)
                                ),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else if (selectedTab == 0 && selectedMediaUri == null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                mediaPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Toca para elegir foto o video del dispositivo",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        // Modo Texto
                        Text(
                            text = storyCaption.ifBlank { "Escribe algo para tu historia..." },
                            color = if (storyCaption.isNotBlank()) Color.White else Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier.padding(24.dp)
                        )
                    }

                    // Avatar del autor en la esquina superior izquierda
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(currentMember.avatarColorHex)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = currentMember.avatarEmoji, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = currentMember.name,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Paleta de colores si es modo texto o si no hay imagen
                if (selectedTab == 1 || selectedMediaUri == null) {
                    Text(
                        text = "Fondo de color",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(colorPalette) { colorHex ->
                            val isSelected = selectedBgColor == colorHex
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorHex))
                                    .clickable { selectedBgColor = colorHex }
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Campo de texto para la historia o pie de foto
                OutlinedTextField(
                    value = storyCaption,
                    onValueChange = { storyCaption = it },
                    placeholder = {
                        Text(if (selectedTab == 0) "Agrega un pie de foto (opcional)..." else "¿Qué quieres contar a la familia?")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("story_caption_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    maxLines = 3
                )
            }
        }
    }
}
