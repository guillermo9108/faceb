package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.FamilyGroup
import com.example.model.FamilyMember
import com.example.model.Post
import com.example.model.ReactionType
import com.example.ui.components.PostCard
import com.example.ui.theme.FacebookBlue

@Composable
fun GroupsScreen(
    currentMember: FamilyMember,
    groups: List<FamilyGroup>,
    selectedGroup: FamilyGroup?,
    groupPosts: List<Post>,
    baseUrl: String,
    onSelectGroup: (FamilyGroup) -> Unit,
    onCloseGroupDetail: () -> Unit,
    onOpenScanDialog: () -> Unit,
    onRefreshGroups: () -> Unit,
    onReactionClick: (Int, ReactionType) -> Unit,
    onCommentClick: (Post) -> Unit,
    onPlayVideo: (String, String, FamilyMember, String?, List<String>) -> Unit,
    onTogglePin: (Int) -> Unit = {},
    onDeletePost: (Int) -> Unit = {}
) {
    val isAdmin = currentMember.role.equals("Administrador", ignoreCase = true) ||
            currentMember.name.equals("Administrador", ignoreCase = true) ||
            currentMember.username.equals("admin", ignoreCase = true)

    if (selectedGroup != null) {
        // Manejo del botón atrás físico o gesto para salir del grupo
        BackHandler {
            onCloseGroupDetail()
        }

        GroupDetailView(
            group = selectedGroup,
            posts = groupPosts,
            currentMember = currentMember,
            baseUrl = baseUrl,
            onBack = onCloseGroupDetail,
            onReactionClick = onReactionClick,
            onCommentClick = onCommentClick,
            onPlayVideo = onPlayVideo,
            onTogglePin = onTogglePin,
            onDeletePost = onDeletePost
        )
    } else {
        GroupsListView(
            groups = groups,
            isAdmin = isAdmin,
            onSelectGroup = onSelectGroup,
            onOpenScanDialog = onOpenScanDialog,
            onRefreshGroups = onRefreshGroups
        )
    }
}

@Composable
private fun GroupsListView(
    groups: List<FamilyGroup>,
    isAdmin: Boolean,
    onSelectGroup: (FamilyGroup) -> Unit,
    onOpenScanDialog: () -> Unit,
    onRefreshGroups: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("groups_list_screen"),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Banner Superior de Grupos
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(FacebookBlue.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Groups,
                                    contentDescription = null,
                                    tint = FacebookBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Grupos Familiares",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Álbumes y carpetas sincronizadas del NAS",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = onRefreshGroups,
                            modifier = Modifier.testTag("refresh_groups_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Actualizar grupos")
                        }
                    }

                    // Botón de sincronización del Administrador
                    if (isAdmin) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onOpenScanDialog() }
                                .testTag("admin_scan_card"),
                            colors = CardDefaults.cardColors(
                                containerColor = FacebookBlue.copy(alpha = 0.08f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(FacebookBlue),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Escanear Carpetas del NAS",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = FacebookBlue
                                    )
                                    Text(
                                        text = "Importar fotos y vídeos de rutas de almacenamiento como grupos",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = FacebookBlue
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Estado Vacío o Listado de Grupos
        if (groups.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderShared,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Aún no hay grupos creados",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isAdmin) {
                            "Como administrador, pulsa el botón de arriba para escanear y convertir las carpetas de tu Synology NAS en grupos familiares."
                        } else {
                            "El administrador puede sincronizar las carpetas del NAS familiar para que todos puedan ver las fotos y comentar."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    if (isAdmin) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onOpenScanDialog,
                            colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.FolderSpecial, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Escanear NAS ahora")
                        }
                    }
                }
            }
        } else {
            items(groups, key = { it.id }) { group ->
                GroupCard(
                    group = group,
                    onClick = { onSelectGroup(group) }
                )
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: FamilyGroup,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("group_card_${group.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Portada del Grupo
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(FacebookBlue.copy(alpha = 0.8f), FacebookBlue)
                        )
                    )
            ) {
                if (!group.coverPhotoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = group.coverPhotoUrl,
                        contentDescription = "Portada de ${group.name}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Sombra inferior para legibilidad
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                                )
                            )
                    )
                }

                // Insignia de publicaciones
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.65f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${group.postsCount} publicaciones",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Nombre en la parte inferior si hay portada
                if (!group.coverPhotoUrl.isNullOrBlank()) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                    )
                }
            }

            // Datos del Grupo
            Column(modifier = Modifier.padding(14.dp)) {
                if (group.coverPhotoUrl.isNullOrBlank()) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (group.description.isNotBlank()) {
                    Text(
                        text = group.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = group.folderPath,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(0.7f)
                        )
                    }

                    Text(
                        text = "Ver grupo →",
                        style = MaterialTheme.typography.labelMedium,
                        color = FacebookBlue,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupDetailView(
    group: FamilyGroup,
    posts: List<Post>,
    currentMember: FamilyMember,
    baseUrl: String,
    onBack: () -> Unit,
    onReactionClick: (Int, ReactionType) -> Unit,
    onCommentClick: (Post) -> Unit,
    onPlayVideo: (String, String, FamilyMember, String?, List<String>) -> Unit,
    onTogglePin: (Int) -> Unit,
    onDeletePost: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("group_detail_screen"),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Encabezado del Grupo con botón Atrás
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Column {
                    // Portada
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(FacebookBlue)
                    ) {
                        if (!group.coverPhotoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = group.coverPhotoUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Gradiente de contraste
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.5f),
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.7f)
                                        )
                                    )
                                )
                        )

                        // Botón de Volver
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .padding(8.dp)
                                .align(Alignment.TopStart)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f))
                                .testTag("group_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver a grupos",
                                tint = Color.White
                            )
                        }

                        // Título y Ruta
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = group.name,
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${group.postsCount} publicaciones • Carpeta: ${group.folderPath}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Información y reglas para familiares
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Todos los familiares pueden ver las fotos y vídeos de este grupo y dejar comentarios.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Publicaciones del Grupo
        if (posts.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Cargando fotos del grupo...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(posts, key = { it.id }) { post ->
                PostCard(
                    post = post,
                    onReactionClick = { rx -> onReactionClick(post.id, rx) },
                    onCommentClick = { onCommentClick(post) },
                    onTogglePin = { onTogglePin(post.id) },
                    onDeletePost = { onDeletePost(post.id) },
                    onPlayVideo = { url, title, _, dur, chunks -> onPlayVideo(url, title, post.author, dur, chunks) }
                )
            }
        }
    }
}
