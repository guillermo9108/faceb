package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.NasImportProgress
import com.example.model.NasStorageVolume
import com.example.model.ScannedFolder
import com.example.ui.theme.FacebookBlue
import com.example.ui.theme.LanOnlineGreen

@Composable
fun AdminFolderScanDialog(
    scannedFolders: List<ScannedFolder>,
    isScanning: Boolean,
    isImporting: Boolean,
    importProgress: NasImportProgress?,
    detectedVolumes: List<NasStorageVolume> = emptyList(),
    onDetectVolumes: () -> Unit = {},
    onScanPath: (String) -> Unit,
    onToggleFolder: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onImportSelected: () -> Unit,
    onDismiss: () -> Unit
) {
    var searchPath by remember { mutableStateOf("/volume3/photo") }
    val allSelected = scannedFolders.isNotEmpty() && scannedFolders.all { it.isSelected }
    val selectedCount = scannedFolders.count { it.isSelected }

    // Si se detectan volúmenes y hay un volumen con fotos (ej. volume3/photo), actualizar sugerencia
    LaunchedEffect(detectedVolumes) {
        if (detectedVolumes.isNotEmpty()) {
            val vol3 = detectedVolumes.find { it.volumeName == "volume3" }
            val preferred = vol3 ?: detectedVolumes.firstOrNull { it.mediaFolders.isNotEmpty() } ?: detectedVolumes.firstOrNull()
            val best = preferred?.mediaFolders?.firstOrNull() ?: preferred?.path ?: "/volume3/photo"
            if (searchPath == "/volume1/photo" || searchPath.isBlank()) {
                searchPath = best
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isImporting && !isScanning) {
                onDismiss()
            }
        },
        modifier = Modifier.testTag("admin_folder_scan_dialog"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(FacebookBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = "Escanear Carpetas",
                        tint = FacebookBlue
                    )
                }
                Column {
                    Text(
                        text = "Sincronizar Carpetas del NAS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "Importación recursiva de fotos y videos",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Sección de Detección de Discos y Volúmenes del Servidor
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = "Discos",
                            tint = FacebookBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Discos en Servidor:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    TextButton(
                        onClick = onDetectVolumes,
                        enabled = !isScanning && !isImporting,
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Detectar Discos",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Detectar Discos", fontSize = 11.sp)
                    }
                }

                // Chips de acceso rápido a los volúmenes detectados (ej: volume3, volume1...)
                val volumeChips = remember(detectedVolumes) {
                    val list = mutableListOf<Pair<String, String>>()
                    if (detectedVolumes.isNotEmpty()) {
                        for (vol in detectedVolumes) {
                            val vName = vol.volumeName
                            // Agregar acceso a /photo del volumen
                            if (vol.mediaFolders.isNotEmpty()) {
                                for (mf in vol.mediaFolders) {
                                    val shortName = "$vName (${mf.substringAfterLast('/')})"
                                    list.add(Pair(shortName, mf))
                                }
                            } else {
                                list.add(Pair("$vName/photo", "${vol.path}/photo"))
                            }
                            // Agregar acceso raíz del volumen
                            list.add(Pair(vName, vol.path))
                        }
                    } else {
                        // Chips por defecto comunes en Synology
                        list.add(Pair("volume3 (photo)", "/volume3/photo"))
                        list.add(Pair("volume3", "/volume3"))
                        list.add(Pair("volume1 (photo)", "/volume1/photo"))
                    }
                    list.distinctBy { it.second }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    items(volumeChips) { (label, path) ->
                        val isSelected = searchPath.trimEnd('/') == path.trimEnd('/')
                        AssistChip(
                            onClick = {
                                searchPath = path
                                onScanPath(path)
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = if (isSelected) FacebookBlue else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (isSelected) FacebookBlue.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = AssistChipDefaults.assistChipBorder(
                                enabled = true,
                                borderColor = if (isSelected) FacebookBlue else Color.Transparent
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Entrada de ruta a escanear
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchPath,
                        onValueChange = { searchPath = it },
                        label = { Text("Ruta en NAS Synology") },
                        placeholder = { Text("/volume3/photo o /volume3/...") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("scan_path_input"),
                        enabled = !isScanning && !isImporting
                    )

                    Button(
                        onClick = { onScanPath(searchPath.trim()) },
                        enabled = !isScanning && !isImporting && searchPath.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("scan_folders_button")
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Escanear"
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Nota de compatibilidad de subcarpetas y discos
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = FacebookBlue.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "• Compatible con volume3 y todos los discos. Incluye subcarpetas en segundo plano.",
                            fontSize = 11.sp,
                            color = FacebookBlue,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Progreso de importación activo
                if (isImporting && importProgress != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Importando contenido...",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${importProgress.processedFiles} / ${importProgress.totalFiles}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FacebookBlue
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            val progressFrac = if (importProgress.totalFiles > 0) {
                                (importProgress.processedFiles.toFloat() / importProgress.totalFiles).coerceIn(0f, 1f)
                            } else 0f
                            LinearProgressIndicator(
                                progress = { progressFrac },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = FacebookBlue,
                                trackColor = MaterialTheme.colorScheme.surface
                            )
                            if (importProgress.statusMessage.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = importProgress.statusMessage,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Encabezado de la lista con "Seleccionar todo"
                if (scannedFolders.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(enabled = !isImporting) {
                                onSelectAll(!allSelected)
                            }
                        ) {
                            Checkbox(
                                checked = allSelected,
                                onCheckedChange = { onSelectAll(it) },
                                enabled = !isImporting,
                                colors = CheckboxDefaults.colors(checkedColor = FacebookBlue),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Seleccionar todas (${scannedFolders.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = "$selectedCount seleccionadas",
                            fontSize = 12.sp,
                            color = FacebookBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Lista de Carpetas
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(scannedFolders, key = { it.folderPath }) { folder ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (folder.isSelected) {
                                        FacebookBlue.copy(alpha = 0.08f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isImporting) {
                                        onToggleFolder(folder.folderPath)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = folder.isSelected,
                                        onCheckedChange = { onToggleFolder(folder.folderPath) },
                                        enabled = !isImporting,
                                        colors = CheckboxDefaults.colors(checkedColor = FacebookBlue),
                                        modifier = Modifier.size(24.dp)
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(FacebookBlue.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = "Carpeta",
                                            tint = FacebookBlue,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = folder.folderName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = folder.folderPath,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Image,
                                                    contentDescription = "Fotos",
                                                    modifier = Modifier.size(11.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(
                                                    text = "${folder.photosCount}",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayCircle,
                                                    contentDescription = "Videos",
                                                    modifier = Modifier.size(11.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(
                                                    text = "${folder.videosCount}",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (folder.isAlreadyImported) {
                                                Text(
                                                    text = "✓ Importada",
                                                    fontSize = 10.sp,
                                                    color = LanOnlineGreen,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (!isScanning) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Presiona 'Escanear' o selecciona un disco/volumen arriba para buscar álbumes.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onImportSelected,
                enabled = selectedCount > 0 && !isImporting && !isScanning,
                colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue),
                modifier = Modifier.testTag("import_folders_button")
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Importando...")
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Importar",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Importar ($selectedCount)")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isImporting,
                modifier = Modifier.testTag("close_scan_dialog_button")
            ) {
                Text("Cerrar")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
