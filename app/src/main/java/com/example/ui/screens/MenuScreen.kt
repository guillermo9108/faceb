package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.AppThemeMode
import com.example.model.FamilyGroup
import com.example.model.FamilyMember
import com.example.model.NasConfig
import com.example.model.OfflineStorageStats
import com.example.ui.theme.FacebookBadgeRed
import com.example.ui.theme.FacebookBlue
import com.example.ui.theme.FacebookFeelingYellow
import com.example.ui.theme.FacebookPhotoGreen
import com.example.ui.theme.LanOfflineAmber
import com.example.ui.theme.LanOnlineGreen
import com.example.ui.theme.ReactionLoveRed

@Composable
fun MenuScreen(
    currentMember: FamilyMember,
    nasConfig: NasConfig,
    familyMembers: List<FamilyMember> = emptyList(),
    groups: List<FamilyGroup> = emptyList(),
    savedOfflineCount: Int = 0,
    onOpenGroup: (FamilyGroup) -> Unit = {},
    onFilterMember: (FamilyMember) -> Unit = {},
    onNavigateToSavedOffline: () -> Unit = {},
    onOpenCreatePost: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToPhotos: () -> Unit,
    onNavigateToNasConfig: () -> Unit,
    onNavigateToMembers: () -> Unit,
    onNavigateToWatch: () -> Unit,
    onNavigateToShorts: () -> Unit,
    onNavigateToGroups: () -> Unit,
    onNavigateToErrorLogs: () -> Unit,
    onOpenScanFolders: () -> Unit = {},
    onRunDiagnostics: () -> Unit = {},
    onRepairDatabase: () -> Unit = {},
    errorCount: Int = 0,
    onOpenProfileSettings: () -> Unit,
    onLogout: () -> Unit,
    offlineStorageStats: OfflineStorageStats = OfflineStorageStats(),
    onOpenDownloadManager: () -> Unit = {},
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    onThemeModeChange: (AppThemeMode) -> Unit = {},
    isAdminPinLockEnabled: Boolean = false,
    onToggleAdminPinLock: (Boolean) -> Unit = {},
    onConfigureAdminPin: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    var showMoreShortcuts by remember { mutableStateOf(false) }
    var isHelpExpanded by remember { mutableStateOf(false) }
    var isSettingsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(bottom = 96.dp)
            .testTag("menu_screen")
    ) {
        // 0. Encabezado "Menú" estilo Facebook con acceso directo a Ajustes NAS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Menú",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            IconButton(
                onClick = onNavigateToNasConfig,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .testTag("menu_header_nas_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Ajustes Synology NAS",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // 1. Tarjeta superior redondeada del perfil (Estilo Facebook Menu Screenshot)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable { onOpenProfileSettings() }
                .testTag("menu_profile_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(currentMember.avatarColorHex))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = currentMember.avatarEmoji, fontSize = 24.sp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentMember.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentMember.role,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Mini badge de estado LAN + Botón circular desplegable
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (nasConfig.isConnected) LanOnlineGreen.copy(alpha = 0.16f)
                                else LanOfflineAmber.copy(alpha = 0.16f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (nasConfig.isConnected) LanOnlineGreen else LanOfflineAmber)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Editar perfil",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. "Tus accesos directos" (Tira horizontal con grupos y miembros reales)
        Text(
            text = "Tus accesos directos",
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        val otherMembers = remember(familyMembers, currentMember.id) {
            familyMembers.filter { it.id != currentMember.id }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Grupos reales del NAS
            items(groups, key = { "group_${it.id}" }) { group ->
                ShortcutStripTile(
                    label = group.name,
                    coverUrl = group.coverPhotoUrl?.let { raw ->
                        if (raw.startsWith("http")) raw else "${nasConfig.baseUrl.trimEnd('/')}/${raw.trimStart('/')}"
                    },
                    emoji = null,
                    bgColor = FacebookBlue.copy(alpha = 0.14f),
                    badgeIcon = Icons.Default.Groups,
                    badgeColor = FacebookBlue,
                    onClick = { onOpenGroup(group) }
                )
            }

            // Miembros reales de la familia
            items(otherMembers, key = { "member_${it.id}" }) { member ->
                ShortcutStripTile(
                    label = member.name,
                    coverUrl = null,
                    emoji = member.avatarEmoji,
                    bgColor = Color(member.avatarColorHex),
                    badgeIcon = Icons.Default.People,
                    badgeColor = LanOnlineGreen,
                    onClick = { onFilterMember(member) }
                )
            }

            // Si no hay ni grupos ni otros miembros aún, mostrar accesos rápidos funcionales reales
            if (groups.isEmpty() && otherMembers.isEmpty()) {
                item(key = "quick_photos") {
                    ShortcutStripTile(
                        label = "Fotos",
                        coverUrl = null,
                        emoji = "🖼️",
                        bgColor = FacebookPhotoGreen.copy(alpha = 0.16f),
                        badgeIcon = Icons.Default.PhotoLibrary,
                        badgeColor = FacebookPhotoGreen,
                        onClick = onNavigateToPhotos
                    )
                }
                item(key = "quick_offline") {
                    ShortcutStripTile(
                        label = "Guardado",
                        coverUrl = null,
                        emoji = "☁️",
                        bgColor = Color(0xFF9C27B0).copy(alpha = 0.16f),
                        badgeIcon = Icons.Default.CloudDone,
                        badgeColor = Color(0xFF9C27B0),
                        onClick = onNavigateToSavedOffline
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Cuadrícula de 2 columnas estilo Facebook (Tarjetas redondeadas)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Fila 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MenuGridTile(
                    icon = Icons.Default.People,
                    iconTint = FacebookBlue,
                    title = if (familyMembers.isNotEmpty()) "Familia (${familyMembers.size} miembros)" else "Familia",
                    onClick = onNavigateToMembers,
                    modifier = Modifier.weight(1f).testTag("menu_tile_members")
                )
                MenuGridTile(
                    icon = Icons.Default.Groups,
                    iconTint = FacebookBlue,
                    title = if (groups.isNotEmpty()) "Grupos (${groups.size})" else "Grupos",
                    onClick = onNavigateToGroups,
                    modifier = Modifier.weight(1f).testTag("menu_tile_groups")
                )
            }

            // Fila 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MenuGridTile(
                    icon = Icons.Default.CloudDone,
                    iconTint = Color(0xFF9C27B0),
                    title = if (savedOfflineCount > 0) "Guardado ($savedOfflineCount)" else "Guardado",
                    onClick = onNavigateToSavedOffline,
                    modifier = Modifier.weight(1f).testTag("menu_tile_saved_offline")
                )
                MenuGridTile(
                    icon = Icons.Default.ElectricBolt,
                    iconTint = ReactionLoveRed,
                    title = "Shorts",
                    onClick = onNavigateToShorts,
                    modifier = Modifier.weight(1f).testTag("menu_tile_shorts")
                )
            }

            // Fila 3
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MenuGridTile(
                    icon = Icons.Default.OndemandVideo,
                    iconTint = FacebookBlue,
                    title = "Watch",
                    onClick = onNavigateToWatch,
                    modifier = Modifier.weight(1f).testTag("menu_tile_watch")
                )
                MenuGridTile(
                    icon = Icons.Default.PhotoLibrary,
                    iconTint = FacebookPhotoGreen,
                    title = "Fotos",
                    onClick = onNavigateToPhotos,
                    modifier = Modifier.weight(1f).testTag("menu_tile_photos")
                )
            }

            // Fila 4
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MenuGridTile(
                    icon = Icons.Default.PostAdd,
                    iconTint = FacebookFeelingYellow,
                    title = "Publicar",
                    onClick = onOpenCreatePost,
                    modifier = Modifier.weight(1f).testTag("menu_tile_create_post")
                )
                MenuGridTile(
                    icon = Icons.Default.Notifications,
                    iconTint = FacebookBadgeRed,
                    title = "Notificaciones",
                    onClick = onNavigateToNotifications,
                    modifier = Modifier.weight(1f).testTag("menu_tile_notifications")
                )
            }

            // Filas adicionales desplegadas por "Ver más"
            AnimatedVisibility(
                visible = showMoreShortcuts,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MenuGridTile(
                            icon = Icons.Default.FolderOpen,
                            iconTint = FacebookBlue,
                            title = "Escanear NAS",
                            onClick = onOpenScanFolders,
                            modifier = Modifier.weight(1f).testTag("menu_tile_scan_nas")
                        )
                        MenuGridTile(
                            icon = Icons.Default.Storage,
                            iconTint = LanOnlineGreen,
                            title = "Diagnóstico BD",
                            onClick = onRunDiagnostics,
                            modifier = Modifier.weight(1f).testTag("menu_tile_db_diag")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MenuGridTile(
                            icon = Icons.Default.CloudDownload,
                            iconTint = FacebookBlue,
                            title = "Descargas y caché (${offlineStorageStats.formattedTotalSize})",
                            onClick = onOpenDownloadManager,
                            modifier = Modifier.weight(1f).testTag("menu_tile_download_manager")
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // 4. Botón ancho "Ver más" / "Ver menos"
            FilledTonalButton(
                onClick = { showMoreShortcuts = !showMoreShortcuts },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("menu_see_more_button")
            ) {
                Text(
                    text = if (showMoreShortcuts) "Ver menos" else "Ver más",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5. Secciones desplegables inferiores estilo Facebook:
        //    "Ayuda y soporte técnico" & "Configuración y privacidad"
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))

        MenuAccordionHeader(
            icon = Icons.Default.HelpOutline,
            title = "Ayuda y soporte técnico",
            expanded = isHelpExpanded,
            badgeCount = errorCount,
            onClick = { isHelpExpanded = !isHelpExpanded },
            modifier = Modifier.testTag("menu_help_accordion")
        )

        AnimatedVisibility(
            visible = isHelpExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MenuSubItemCard(
                    icon = Icons.Default.Warning,
                    iconTint = if (errorCount > 0) LanOfflineAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                    title = if (errorCount > 0) "Registro de Errores ($errorCount)" else "Registro de Errores",
                    subtitle = "Bitácora de red LAN, PHP y MariaDB",
                    onClick = onNavigateToErrorLogs
                )
                MenuSubItemCard(
                    icon = Icons.Default.Storage,
                    iconTint = LanOnlineGreen,
                    title = "Diagnóstico en vivo MariaDB 10",
                    subtitle = "Verificar puerto 3307, tablas y PHP PDO",
                    onClick = onRunDiagnostics
                )
                MenuSubItemCard(
                    icon = Icons.Default.Build,
                    iconTint = FacebookBlue,
                    title = "Reparar / Verificar tablas NAS",
                    subtitle = "Crear estructura completa en MariaDB 10",
                    onClick = onRepairDatabase
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))

        MenuAccordionHeader(
            icon = Icons.Default.Settings,
            title = "Configuración y privacidad",
            expanded = isSettingsExpanded,
            onClick = { isSettingsExpanded = !isSettingsExpanded },
            modifier = Modifier.testTag("menu_settings_accordion")
        )

        AnimatedVisibility(
            visible = isSettingsExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Selector de Tema (Claro / Oscuro / Automático)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DarkMode,
                                contentDescription = null,
                                tint = FacebookBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tema visual de la aplicación",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AppThemeMode.values().forEach { mode ->
                                val label = when (mode) {
                                    AppThemeMode.SYSTEM -> "Auto"
                                    AppThemeMode.LIGHT -> "Claro"
                                    AppThemeMode.DARK -> "Oscuro"
                                }
                                FilterChip(
                                    selected = themeMode == mode,
                                    onClick = { onThemeModeChange(mode) },
                                    label = { Text(label, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = FacebookBlue,
                                        selectedLabelColor = Color.White
                                    ),
                                    modifier = Modifier.testTag("theme_chip_${mode.value}")
                                )
                            }
                        }
                    }
                }

                // Bloqueo PIN de administración
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = FacebookBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Bloqueo PIN de administración",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "Protege ajustes NAS, diagnóstico y escaneo",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = isAdminPinLockEnabled,
                                onCheckedChange = onToggleAdminPinLock,
                                modifier = Modifier.testTag("admin_pin_lock_switch")
                            )
                        }
                        if (isAdminPinLockEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = onConfigureAdminPin,
                                    modifier = Modifier.testTag("configure_admin_pin_button")
                                ) {
                                    Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp), tint = FacebookBlue)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Configurar / Cambiar PIN", color = FacebookBlue, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                MenuSubItemCard(
                    icon = Icons.Default.CloudDownload,
                    iconTint = FacebookBlue,
                    title = "Gestor de descargas y caché",
                    subtitle = "Offline: ${offlineStorageStats.formattedProtectedSize} · Caché: ${offlineStorageStats.formattedCacheSize}",
                    onClick = onOpenDownloadManager
                )
                MenuSubItemCard(
                    icon = Icons.Default.Dns,
                    iconTint = FacebookBlue,
                    title = "Ajustes de Synology NAS",
                    subtitle = if (nasConfig.isConnected) "Conectado a ${nasConfig.ip}:${nasConfig.port}" else "Sin conexión al NAS (${nasConfig.ip})",
                    onClick = onNavigateToNasConfig
                )
                MenuSubItemCard(
                    icon = Icons.Default.ManageAccounts,
                    iconTint = FacebookBlue,
                    title = "Editar Mi Perfil",
                    subtitle = "Cambiar avatar, nombre o contraseña",
                    onClick = onOpenProfileSettings
                )
                MenuSubItemCard(
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    iconTint = Color(0xFFE53935),
                    title = "Cerrar Sesión",
                    subtitle = "Cambiar de cuenta familiar",
                    onClick = onLogout
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
    }
}

@Composable
private fun ShortcutStripTile(
    label: String,
    coverUrl: String?,
    emoji: String?,
    bgColor: Color,
    badgeIcon: ImageVector,
    badgeColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(74.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Box(modifier = Modifier.size(64.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(bgColor)
                    .border(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (!emoji.isNullOrBlank()) {
                    Text(text = emoji, fontSize = 26.sp)
                } else {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = label,
                        tint = FacebookBlue,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            // Mini badge en esquina inferior derecha (idéntico al de Facebook)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = badgeIcon,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = label,
            fontSize = 11.5.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MenuGridTile(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)),
        modifier = modifier
            .heightIn(min = 82.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    lineHeight = 17.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MenuAccordionHeader(
    icon: ImageVector,
    title: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )

        if (badgeCount > 0) {
            Surface(
                color = LanOfflineAmber,
                shape = CircleShape
            ) {
                Text(
                    text = badgeCount.toString(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) "Contraer" else "Expandir",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MenuSubItemCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}
