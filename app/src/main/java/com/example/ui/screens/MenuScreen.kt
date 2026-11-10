package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FamilyMember
import com.example.model.NasConfig
import com.example.ui.theme.FacebookBlue
import com.example.ui.theme.LanOfflineAmber
import com.example.ui.theme.LanOnlineGreen

@Composable
fun MenuScreen(
    currentMember: FamilyMember,
    nasConfig: NasConfig,
    onNavigateToPhotos: () -> Unit,
    onNavigateToNasConfig: () -> Unit,
    onNavigateToMembers: () -> Unit,
    onNavigateToWatch: () -> Unit,
    onNavigateToShorts: () -> Unit,
    onNavigateToGroups: () -> Unit,
    onNavigateToErrorLogs: () -> Unit,
    errorCount: Int = 0,
    onOpenProfileSettings: () -> Unit,
    onLogout: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Tarjeta de Perfil Actual
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenProfileSettings() }
                .testTag("menu_profile_card")
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(currentMember.avatarColorHex)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = currentMember.avatarEmoji, fontSize = 28.sp)
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentMember.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${currentMember.role} • Pulsa para editar perfil",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            }
        }

        Text(
            text = "Accesos Directos",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Cuadrícula de Opciones
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                MenuItemRow(
                    icon = Icons.Default.Folder,
                    iconTint = FacebookBlue,
                    title = "Grupos y Álbumes Familiares",
                    subtitle = "Carpetas sincronizadas desde el NAS",
                    onClick = onNavigateToGroups
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                MenuItemRow(
                    icon = Icons.Default.PlayArrow,
                    iconTint = Color(0xFFFA3E3E),
                    title = "Shorts",
                    subtitle = "Vídeos verticales familiares",
                    onClick = onNavigateToShorts
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                MenuItemRow(
                    icon = Icons.Default.OndemandVideo,
                    iconTint = Color(0xFF1877F2),
                    title = "Vídeos y Watch",
                    subtitle = "Reproductor nativo en LAN",
                    onClick = onNavigateToWatch
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                MenuItemRow(
                    icon = Icons.Default.PhotoLibrary,
                    iconTint = Color(0xFF45BD62),
                    title = "Fotos",
                    subtitle = "Galería completa de recuerdos",
                    onClick = onNavigateToPhotos
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                MenuItemRow(
                    icon = Icons.Default.Group,
                    iconTint = Color(0xFF8A2BE2),
                    title = "Miembros Familiares",
                    subtitle = "Cuentas y perfiles activos",
                    onClick = onNavigateToMembers
                )
            }
        }

        Text(
            text = "Configuración y Sistema",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                MenuItemRow(
                    icon = Icons.Default.Dns,
                    iconTint = FacebookBlue,
                    title = "Ajustes de Synology NAS",
                    subtitle = if (nasConfig.isConnected) "Conectado a ${nasConfig.ip}" else "Sin conexión al NAS",
                    onClick = onNavigateToNasConfig
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                MenuItemRow(
                    icon = Icons.Default.Warning,
                    iconTint = if (errorCount > 0) LanOfflineAmber else MaterialTheme.colorScheme.outline,
                    title = if (errorCount > 0) "Registro de Errores ($errorCount)" else "Registro de Errores",
                    subtitle = "Diagnóstico en vivo PHP 7.3 y MariaDB 10",
                    onClick = onNavigateToErrorLogs
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                MenuItemRow(
                    icon = Icons.Default.ManageAccounts,
                    iconTint = FacebookBlue,
                    title = "Editar Mi Perfil",
                    subtitle = "Cambiar avatar, nombre o contraseña",
                    onClick = onOpenProfileSettings
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                MenuItemRow(
                    icon = Icons.Default.ExitToApp,
                    iconTint = Color(0xFFE53935),
                    title = "Cerrar Sesión",
                    subtitle = "Cambiar de usuario familiar",
                    onClick = onLogout
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun MenuItemRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}
