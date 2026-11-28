package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.GroupOrganizationMode
import com.example.model.NasImportProgress
import com.example.model.NasStorageVolume
import com.example.model.ScannedFolder
import com.example.ui.theme.FacebookBlue
import com.example.ui.theme.LanOnlineGreen

@Composable
fun AdminFolderScanDialog(
    scannedFolders: List<ScannedFolder>,
    currentScanPath: String = "/volume3/photo",
    parentScanPath: String? = null,
    isScanning: Boolean,
    isImporting: Boolean,
    importProgress: NasImportProgress?,
    detectedVolumes: List<NasStorageVolume> = emptyList(),
    onDetectVolumes: () -> Unit = {},
    onScanPath: (String) -> Unit,
    onToggleFolder: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onImportSelected: (allowUploads: Boolean, organizationMode: GroupOrganizationMode) -> Unit,
    onDismiss: () -> Unit
) {
    var searchPath by remember { mutableStateOf(currentScanPath) }
    var bulkAllowUploads by remember { mutableStateOf(true) }
    var bulkOrganizationMode by remember { mutableStateOf(GroupOrganizationMode.NORMAL) }

    val allSelected = scannedFolders.isNotEmpty() && scannedFolders.all { it.isSelected }
    val selectedCount = scannedFolders.count { it.isSelected }

    // Sincronizar campo de texto cuando se entra en una subcarpeta o se sube un nivel
    LaunchedEffect(currentScanPath) {
        if (currentScanPath.isNotBlank()) {
            searchPath = currentScanPath
        }
    }

    // Si se detectan volúmenes y el usuario aún no cambió la ruta manualmente, sugerir volumen con fotos
    LaunchedEffect(detectedVolumes) {
        if (detectedVolumes.isNotEmpty() && searchPath == "/volume1/photo") {
            val vol3 = detectedVolumes.find { it.volumeName == "volume3" }
            val preferred = vol3 ?: detectedVolumes.firstOrNull { it.mediaFolders.isNotEmpty() } ?: detectedVolumes.firstOrNull()
            val best = preferred?.mediaFolders?.firstOrNull() ?: preferred?.path ?: "/volume3/photo"
            searchPath = best
        }
    }

    val breadcrumbs = remember(currentScanPath) {
        val parts = currentScanPath.trim('/').split('/').filter { it.isNotBlank() }
        val crumbs = mutableListOf<Pair<String, String>>()
        var acc = ""
        for (p in parts) {
            acc += "/$p"
            crumbs.add(p to acc)
        }
        crumbs
    }

    Dialog(
        onDismissRequest = {
            if (!isImporting && !isScanning) {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 760.dp)
                .testTag("admin_folder_scan_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Encabezado del diálogo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
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
                                text = "Explorar y Crear Grupos NAS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Navega dentro de subcarpetas y configura el lote",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !isImporting,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("close_scan_dialog_icon_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Cuerpo desplazable para pantallas pequeñas sin comprimir botones ni lista
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
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
                            modifier = Modifier.heightIn(min = 36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Detectar Discos",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Detectar Discos", fontSize = 12.sp)
                        }
                    }

                    val volumeChips = remember(detectedVolumes) {
                        val list = mutableListOf<Pair<String, String>>()
                        if (detectedVolumes.isNotEmpty()) {
                            for (vol in detectedVolumes) {
                                val vName = vol.volumeName
                                if (vol.mediaFolders.isNotEmpty()) {
                                    for (mf in vol.mediaFolders) {
                                        val shortName = "$vName (${mf.substringAfterLast('/')})"
                                        list.add(Pair(shortName, mf))
                                    }
                                } else {
                                    list.add(Pair("$vName/photo", "${vol.path}/photo"))
                                }
                                list.add(Pair(vName, vol.path))
                            }
                        } else {
                            list.add(Pair("volume3 (photo)", "/volume3/photo"))
                            list.add(Pair("volume3", "/volume3"))
                            list.add(Pair("volume1 (photo)", "/volume1/photo"))
                        }
                        list.distinctBy { it.second }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
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

                    Spacer(modifier = Modifier.height(6.dp))

                    // Entrada manual de ruta + botón Escanear
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchPath,
                            onValueChange = { searchPath = it },
                            label = { Text("Ruta actual en NAS") },
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
                                    contentDescription = "Escanear ruta"
                                )
                            }
                        }
                    }

                    // Navegación por ruta: botón Subir nivel + breadcrumbs clicables
                    if (parentScanPath != null || breadcrumbs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (parentScanPath != null) {
                                OutlinedButton(
                                    onClick = {
                                        searchPath = parentScanPath
                                        onScanPath(parentScanPath)
                                    },
                                    enabled = !isScanning && !isImporting,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .heightIn(min = 36.dp)
                                        .testTag("scan_parent_folder_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = "Subir a carpeta anterior",
                                        modifier = Modifier.size(14.dp),
                                        tint = FacebookBlue
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Subir", fontSize = 11.sp, color = FacebookBlue)
                                }
                            }

                            LazyRow(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                itemsIndexed(breadcrumbs) { idx, (segment, fullCrumbPath) ->
                                    val isCurrent = idx == breadcrumbs.lastIndex
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isCurrent) {
                                            FacebookBlue.copy(alpha = 0.14f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                        },
                                        modifier = Modifier.clickable(enabled = !isScanning && !isImporting && !isCurrent) {
                                            searchPath = fullCrumbPath
                                            onScanPath(fullCrumbPath)
                                        }
                                    ) {
                                        Text(
                                            text = segment,
                                            fontSize = 11.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isCurrent) FacebookBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                    if (!isCurrent) {
                                        Text(
                                            text = "/",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 2.dp)
                                        )
                                    }
                                }
                            }
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

                    // Lista de carpetas encontradas en la ruta actual
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
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clickable(enabled = !isImporting) {
                                        onSelectAll(!allSelected)
                                    }
                            ) {
                                Checkbox(
                                    checked = allSelected,
                                    onCheckedChange = { onSelectAll(it) },
                                    enabled = !isImporting,
                                    colors = CheckboxDefaults.colors(checkedColor = FacebookBlue),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Seleccionar todas (${scannedFolders.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Text(
                                text = "$selectedCount selec.",
                                fontSize = 12.sp,
                                color = FacebookBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 230.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(scannedFolders, key = { it.folderPath }) { folder ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (folder.isSelected) {
                                            FacebookBlue.copy(alpha = 0.08f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        }
                                    ),
                                    border = if (folder.isSelected) {
                                        BorderStroke(1.dp, FacebookBlue.copy(alpha = 0.4f))
                                    } else null,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isImporting) {
                                            onToggleFolder(folder.folderPath)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
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
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(FacebookBlue.copy(alpha = 0.14f)),
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
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
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
                                                if (folder.subfoldersCount > 0) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = Icons.Default.FolderOpen,
                                                            contentDescription = "Subcarpetas",
                                                            modifier = Modifier.size(11.dp),
                                                            tint = FacebookBlue
                                                        )
                                                        Spacer(modifier = Modifier.width(2.dp))
                                                        Text(
                                                            text = "${folder.subfoldersCount}",
                                                            fontSize = 10.sp,
                                                            color = FacebookBlue,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }
                                                if (folder.isAlreadyImported) {
                                                    Text(
                                                        text = "✓ Grupo",
                                                        fontSize = 10.sp,
                                                        color = LanOnlineGreen,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        // Botón directo para entrar dentro de la carpeta y escanear su interior
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (folder.hasSubfolders || folder.subfoldersCount > 0) {
                                                FacebookBlue.copy(alpha = 0.12f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                            },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable(enabled = !isScanning && !isImporting) {
                                                    searchPath = folder.folderPath
                                                    onScanPath(folder.folderPath)
                                                }
                                                .testTag("explore_folder_button_${folder.folderName}")
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = "Entrar",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (folder.hasSubfolders || folder.subfoldersCount > 0) {
                                                        FacebookBlue
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                )
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                    contentDescription = "Entrar en ${folder.folderName}",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = if (folder.hasSubfolders || folder.subfoldersCount > 0) {
                                                        FacebookBlue
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                )
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
                                text = "No hay subcarpetas dentro de esta ruta.\nPuedes volver arriba o elegir otra carpeta.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Configuración en masa antes de crear los grupos seleccionados
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = FacebookBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Configuración en masa antes de crear",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // 1) Permiso de subida en masa
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Permitir subir contenido",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (bulkAllowUploads) {
                                            "Todos los familiares podrán publicar en los grupos creados"
                                        } else {
                                            "Solo administradores podrán publicar en estos grupos"
                                        },
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(
                                    checked = bulkAllowUploads,
                                    onCheckedChange = { bulkAllowUploads = it },
                                    enabled = !isImporting,
                                    colors = SwitchDefaults.colors(checkedTrackColor = FacebookBlue),
                                    modifier = Modifier.testTag("bulk_allow_uploads_switch")
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))

                            // 2) Orden de contenido del grupo en masa (Normal vs Series/Novelas)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Orden del grupo",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = bulkOrganizationMode == GroupOrganizationMode.NORMAL,
                                        onClick = { bulkOrganizationMode = GroupOrganizationMode.NORMAL },
                                        enabled = !isImporting,
                                        label = {
                                            Text(
                                                text = "Normal (Recientes)",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = FacebookBlue.copy(alpha = 0.15f),
                                            selectedLabelColor = FacebookBlue
                                        ),
                                        modifier = Modifier.testTag("bulk_org_normal_chip")
                                    )
                                    FilterChip(
                                        selected = bulkOrganizationMode == GroupOrganizationMode.SERIES,
                                        onClick = { bulkOrganizationMode = GroupOrganizationMode.SERIES },
                                        enabled = !isImporting,
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.PlaylistPlay,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = "Series / Novelas",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = FacebookBlue.copy(alpha = 0.15f),
                                            selectedLabelColor = FacebookBlue
                                        ),
                                        modifier = Modifier.testTag("bulk_org_series_chip")
                                    )
                                }
                                if (bulkOrganizationMode == GroupOrganizationMode.SERIES) {
                                    Text(
                                        text = "Ordena capítulos automáticamente (cap 01, 02, 3, 004, t1x05, 0006, 7...) y temporadas.",
                                        fontSize = 10.sp,
                                        color = FacebookBlue,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Botones de acción inferiores
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isImporting,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("close_scan_dialog_button")
                    ) {
                        Text("Cerrar")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { onImportSelected(bulkAllowUploads, bulkOrganizationMode) },
                        enabled = selectedCount > 0 && !isImporting && !isScanning,
                        colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("import_folders_button")
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
                            Text("Crear grupos ($selectedCount)")
                        }
                    }
                }
            }
        }
    }
}
