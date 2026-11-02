package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.UploadStage
import com.example.model.VideoUploadProgress
import com.example.ui.theme.FacebookBlue
import com.example.ui.theme.FacebookPhotoGreen

@Composable
fun VideoUploadProgressDialog(
    progress: VideoUploadProgress,
    onDismiss: () -> Unit = {}
) {
    Dialog(
        onDismissRequest = { /* No cancelar durante subida activa */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("video_upload_progress_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Cabecera con icono
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(FacebookBlue.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (progress.stage == UploadStage.COMPLETED) Icons.Default.CheckCircle
                            else if (progress.stage == UploadStage.SLICING) Icons.Default.ContentCut
                            else Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = if (progress.stage == UploadStage.COMPLETED) FacebookPhotoGreen else FacebookBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (progress.stage == UploadStage.COMPLETED) "¡Subida completada!"
                            else if (progress.stage == UploadStage.SLICING) "Procesando fragmentos"
                            else "Subiendo al Synology NAS",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = progress.statusMessage,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ==========================================
                // BARRA 1: PROGRESO DE FRAGMENTACIÓN
                // ==========================================
                val isSlicingDone = progress.stage != UploadStage.SLICING && progress.sliceProgress >= 1f
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ContentCut,
                            contentDescription = null,
                            tint = if (isSlicingDone) FacebookPhotoGreen else FacebookBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "1. Fragmentación (Zero-Transcoding)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSlicingDone) FacebookPhotoGreen.copy(alpha = 0.15f) else FacebookBlue.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (isSlicingDone) "Listo ✓" else "${(progress.sliceProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isSlicingDone) FacebookPhotoGreen else FacebookBlue
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { progress.sliceProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (isSlicingDone) FacebookPhotoGreen else FacebookBlue,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // ==========================================
                // BARRA 2: PROGRESO DE SUBIDA POR FRAGMENTOS
                // ==========================================
                val isUploadActive = progress.stage == UploadStage.UPLOADING || progress.stage == UploadStage.FINALIZING || progress.stage == UploadStage.COMPLETED
                val isUploadDone = progress.stage == UploadStage.COMPLETED
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = if (isUploadDone) FacebookPhotoGreen else if (isUploadActive) FacebookBlue else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "2. Subida al servidor LAN",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (isUploadActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isUploadDone) FacebookPhotoGreen.copy(alpha = 0.15f)
                        else if (isUploadActive) FacebookBlue.copy(alpha = 0.15f)
                        else Color.Gray.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = if (isUploadDone) "Completado ✓"
                            else if (isUploadActive) "${(progress.uploadProgress * 100).toInt()}%"
                            else "En espera",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isUploadDone) FacebookPhotoGreen
                                else if (isUploadActive) FacebookBlue
                                else Color.Gray
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { if (isUploadActive) progress.uploadProgress else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (isUploadDone) FacebookPhotoGreen else FacebookBlue,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                )

                if (progress.totalChunks > 0 && isUploadActive) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Fragmento ${progress.currentChunk} de ${progress.totalChunks}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Mensaje informativo
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "La fragmentación previa permite que los vídeos de publicaciones y Shorts arranquen de inmediato y sin interrupciones en todos los dispositivos familiares.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        ),
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}
