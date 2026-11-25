package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.model.FamilyGroup
import com.example.model.FamilyMember
import com.example.model.GroupOrganizationMode
import com.example.model.MediaType
import com.example.model.Post
import com.example.model.ReactionType
import com.example.ui.components.PostCard
import com.example.ui.theme.FacebookBlue
import com.example.ui.theme.LanOnlineGreen
import com.example.util.QueuedPostDownload

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
    onPlayVideo: (String, String, FamilyMember, String?, List<String>, List<Long>, Long, Int, Long) -> Unit,
    onTogglePin: (Int) -> Unit = {},
    onDeletePost: (Int) -> Unit = {},
    onToggleSaveOffline: (Post) -> Unit = {},
    followedGroupIds: Set<Int> = emptySet(),
    onToggleFollowGroup: (Int) -> Unit = {},
    groupSearchQuery: String = "",
    isSearchingGroup: Boolean = false,
    onGroupSearchQueryChange: (String) -> Unit = {},
    hasMoreGroupPosts: Boolean = false,
    isLoadingGroupPosts: Boolean = false,
    isLoadingMoreGroupPosts: Boolean = false,
    onLoadMoreGroupPosts: () -> Unit = {},
    onCreateGroup: (name: String, description: String, allowUploads: Boolean, orgMode: GroupOrganizationMode, coverUri: Uri?) -> Unit = { _, _, _, _, _ -> },
    onUpdateGroup: (groupId: Int, name: String, description: String, allowUploads: Boolean, orgMode: GroupOrganizationMode, coverUri: Uri?, coverUrl: String?) -> Unit = { _, _, _, _, _, _, _ -> },
    onDeleteGroup: (groupId: Int) -> Unit = {},
    onCreatePostInGroup: (FamilyGroup) -> Unit = {},
    onVideoStarted: ((postId: Int, videoUrl: String, fallbackChunkUrl: String?) -> Unit)? = null,
    downloadStates: Map<Int, QueuedPostDownload> = emptyMap()
) {
    val isAdmin = currentMember.role.equals("Administrador", ignoreCase = true) ||
            currentMember.name.equals("Administrador", ignoreCase = true) ||
            currentMember.username.equals("admin", ignoreCase = true)

    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var groupToManage by remember { mutableStateOf<FamilyGroup?>(null) }

    if (selectedGroup != null) {
        // Manejo del botón atrás físico o gesto para salir del grupo
        BackHandler {
            onCloseGroupDetail()
        }

        GroupDetailView(
            group = selectedGroup,
            currentMember = currentMember,
            isAdmin = isAdmin,
            posts = groupPosts,
            isFollowing = followedGroupIds.contains(selectedGroup.id),
            onToggleFollow = { onToggleFollowGroup(selectedGroup.id) },
            onOpenManageGroup = { groupToManage = selectedGroup },
            onCreatePostInGroup = { onCreatePostInGroup(selectedGroup) },
            searchQuery = groupSearchQuery,
            isSearching = isSearchingGroup,
            onSearchQueryChange = onGroupSearchQueryChange,
            hasMorePosts = hasMoreGroupPosts,
            isLoadingPosts = isLoadingGroupPosts,
            isLoadingMore = isLoadingMoreGroupPosts,
            onLoadMore = onLoadMoreGroupPosts,
            onBack = onCloseGroupDetail,
            onReactionClick = onReactionClick,
            onCommentClick = onCommentClick,
            onPlayVideo = onPlayVideo,
            onTogglePin = onTogglePin,
            onDeletePost = onDeletePost,
            onToggleSaveOffline = onToggleSaveOffline,
            onVideoStarted = onVideoStarted,
            downloadStates = downloadStates
        )
    } else {
        GroupsListView(
            groups = groups,
            isAdmin = isAdmin,
            followedGroupIds = followedGroupIds,
            onToggleFollowGroup = onToggleFollowGroup,
            onSelectGroup = onSelectGroup,
            onOpenCreateGroup = { showCreateGroupDialog = true },
            onOpenScanDialog = onOpenScanDialog,
            onRefreshGroups = onRefreshGroups
        )
    }

    if (showCreateGroupDialog) {
        CreateGroupDialog(
            onDismiss = { showCreateGroupDialog = false },
            onConfirmCreate = { name, desc, allowUploads, orgMode, coverUri ->
                showCreateGroupDialog = false
                onCreateGroup(name, desc, allowUploads, orgMode, coverUri)
            }
        )
    }

    groupToManage?.let { targetGroup ->
        ManageGroupDialog(
            group = targetGroup,
            groupPosts = groupPosts,
            onDismiss = { groupToManage = null },
            onSaveChanges = { name, desc, allowUploads, orgMode, coverUri, coverUrl ->
                groupToManage = null
                onUpdateGroup(targetGroup.id, name, desc, allowUploads, orgMode, coverUri, coverUrl)
            },
            onDeleteGroup = {
                groupToManage = null
                onDeleteGroup(targetGroup.id)
            }
        )
    }
}

@Composable
private fun GroupsListView(
    groups: List<FamilyGroup>,
    isAdmin: Boolean,
    followedGroupIds: Set<Int>,
    onToggleFollowGroup: (Int) -> Unit,
    onSelectGroup: (FamilyGroup) -> Unit,
    onOpenCreateGroup: () -> Unit,
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

                    Spacer(modifier = Modifier.height(12.dp))

                    // Botón Crear Grupo (disponible para todos los usuarios)
                    Button(
                        onClick = onOpenCreateGroup,
                        colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("create_group_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Crear nuevo grupo", fontWeight = FontWeight.Bold)
                    }

                    // Botón de sincronización del Administrador
                    if (isAdmin) {
                        Spacer(modifier = Modifier.height(10.dp))
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
                    isFollowing = followedGroupIds.contains(group.id),
                    onToggleFollow = { onToggleFollowGroup(group.id) },
                    onClick = { onSelectGroup(group) }
                )
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: FamilyGroup,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
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

                // Insignias de estado (Serie / Solo admin) arriba a la izquierda
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (group.organizationMode == GroupOrganizationMode.SERIES) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = FacebookBlue.copy(alpha = 0.9f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlaylistPlay,
                                    contentDescription = "Organizado como serie",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Serie", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (!group.allowUploads) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black.copy(alpha = 0.65f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Solo administrador publica",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Solo admin", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
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
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
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
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (isFollowing) {
                        OutlinedButton(
                            onClick = onToggleFollow,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("follow_group_card_button_${group.id}")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = LanOnlineGreen, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Siguiendo", color = LanOnlineGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onToggleFollow,
                            colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("follow_group_card_button_${group.id}")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Seguir", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupDetailView(
    group: FamilyGroup,
    currentMember: FamilyMember,
    isAdmin: Boolean,
    posts: List<Post>,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
    onOpenManageGroup: () -> Unit,
    onCreatePostInGroup: () -> Unit,
    searchQuery: String,
    isSearching: Boolean,
    onSearchQueryChange: (String) -> Unit,
    hasMorePosts: Boolean,
    isLoadingPosts: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
    onReactionClick: (Int, ReactionType) -> Unit,
    onCommentClick: (Post) -> Unit,
    onPlayVideo: (String, String, FamilyMember, String?, List<String>, List<Long>, Long, Int, Long) -> Unit,
    onTogglePin: (Int) -> Unit,
    onDeletePost: (Int) -> Unit,
    onToggleSaveOffline: (Post) -> Unit = {},
    onVideoStarted: ((postId: Int, videoUrl: String, fallbackChunkUrl: String?) -> Unit)? = null,
    downloadStates: Map<Int, QueuedPostDownload> = emptyMap()
) {
    val canManageGroup = isAdmin || group.createdByUserId == currentMember.id
    val canUserPost = group.allowUploads || canManageGroup
    val listState = rememberLazyListState()
    val shouldLoadMore by remember(hasMorePosts, isLoadingMore, searchQuery) {
        derivedStateOf {
            if (searchQuery.isNotBlank() || !hasMorePosts || isLoadingMore) return@derivedStateOf false
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisible >= (totalItems - 3)
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag("group_detail_screen"),
        contentPadding = PaddingValues(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Encabezado del Grupo con botón Atrás, Seguir y Buscador ligero en BD
        item(key = "group_header") {
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
                                .size(48.dp)
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

                        // Botón Administrar Grupo (dueño o administrador NAS)
                        if (canManageGroup) {
                            IconButton(
                                onClick = onOpenManageGroup,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(48.dp)
                                    .align(Alignment.TopEnd)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.52f))
                                    .testTag("manage_group_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Administrar grupo",
                                    tint = Color.White
                                )
                            }
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

                    // Fila de seguir grupo + descripción
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isFollowing) "Siguiendo · Aparece en tu Muro" else "Sigue este grupo para verlo en tu Muro",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        if (isFollowing) {
                            OutlinedButton(
                                onClick = onToggleFollow,
                                modifier = Modifier.testTag("follow_group_detail_button_${group.id}")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = LanOnlineGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Siguiendo", color = LanOnlineGreen, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = onToggleFollow,
                                colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue),
                                modifier = Modifier.testTag("follow_group_detail_button_${group.id}")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Seguir grupo", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Botones de acción dentro del grupo: Publicar en grupo / Administrar + banner de Serie
                    if (canUserPost || canManageGroup) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (canUserPost) {
                                Button(
                                    onClick = onCreatePostInGroup,
                                    colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("group_create_post_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Publicar en grupo", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (canManageGroup) {
                                OutlinedButton(
                                    onClick = onOpenManageGroup,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("manage_group_action_button")
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Administrar", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Solo administradores pueden publicar en este grupo.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (group.organizationMode == GroupOrganizationMode.SERIES) {
                        Surface(
                            color = FacebookBlue.copy(alpha = 0.09f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlaylistPlay,
                                    contentDescription = null,
                                    tint = FacebookBlue,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Organizado como Serie · Orden secuencial de episodios",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = FacebookBlue
                                )
                            }
                        }
                    }

                    // Buscador ligero en toda la base de datos del grupo
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Buscar en todo ${group.name}...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Buscar en grupo")
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(
                                    onClick = { onSearchQueryChange("") },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Limpiar búsqueda")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                            .testTag("group_search_input")
                    )

                    if (searchQuery.isNotBlank()) {
                        Surface(
                            color = FacebookBlue.copy(alpha = 0.08f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isSearching) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = FacebookBlue)
                                }
                                Text(
                                    text = if (isSearching) "Buscando en toda la BD del grupo..."
                                    else "${posts.size} resultado(s) encontrado(s) en ${group.name}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = FacebookBlue
                                )
                            }
                        }
                    }
                }
            }
        }

        // Publicaciones del Grupo (cargadas de 10 en 10 o resultados de búsqueda)
        if (posts.isEmpty() && (isLoadingPosts || isSearching)) {
            item(key = "group_loading") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = FacebookBlue)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isSearching) "Buscando en todo el grupo..." else "Cargando primeras 10 publicaciones...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (posts.isEmpty()) {
            item(key = "group_empty") {
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
                        text = if (searchQuery.isNotBlank()) "No se encontraron publicaciones para \"$searchQuery\" en este grupo."
                        else "Aún no hay publicaciones en este grupo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            itemsIndexed(posts, key = { _, p -> p.id }) { index, post ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (group.organizationMode == GroupOrganizationMode.SERIES) {
                        Surface(
                            color = FacebookBlue.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                            modifier = Modifier.padding(horizontal = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlaylistPlay,
                                    contentDescription = null,
                                    tint = FacebookBlue,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Episodio ${index + 1}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = FacebookBlue
                                )
                            }
                        }
                    }
                    PostCard(
                        post = post,
                        onReactionClick = { rx -> onReactionClick(post.id, rx) },
                        onCommentClick = { onCommentClick(post) },
                        onTogglePin = { onTogglePin(post.id) },
                        onDeletePost = { onDeletePost(post.id) },
                        onPlayVideo = { url, title, _, dur, chunks, chunkDurations, totalDur, chunkIdx, posMs ->
                            onPlayVideo(url, title, post.author, dur, chunks, chunkDurations, totalDur, chunkIdx, posMs)
                        },
                        onToggleSaveOffline = { onToggleSaveOffline(post) },
                        fallbackFolderImageUrl = group.coverPhotoUrl,
                        onVideoStarted = onVideoStarted,
                        downloadState = downloadStates[post.id]
                    )
                }
            }

            if (searchQuery.isBlank()) {
                item(key = "group_pagination_footer") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoadingMore) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = FacebookBlue
                                )
                                Text(
                                    text = "Cargando 10 publicaciones más...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (!hasMorePosts) {
                            Text(
                                text = "Has llegado al final del grupo (${posts.size} publicaciones)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            OutlinedButton(
                                onClick = onLoadMore,
                                modifier = Modifier.testTag("load_more_group_posts_button")
                            ) {
                                Text("Cargar siguientes 10 publicaciones")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onConfirmCreate: (name: String, description: String, allowUploads: Boolean, orgMode: GroupOrganizationMode, coverUri: Uri?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var allowUploads by remember { mutableStateOf(true) }
    var orgMode by remember { mutableStateOf(GroupOrganizationMode.NORMAL) }
    var selectedCoverUri by remember { mutableStateOf<Uri?>(null) }

    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) selectedCoverUri = uri
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp)
                .testTag("create_group_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Crear grupo familiar",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del grupo") },
                    placeholder = { Text("Ej: Serie Familiar, Viaje al Sur...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_group_name_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción (opcional)") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_group_desc_input")
                )

                // Portada del grupo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Portada del grupo",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    OutlinedButton(
                        onClick = {
                            coverPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (selectedCoverUri == null) "Elegir foto" else "Cambiar foto")
                    }
                }

                selectedCoverUri?.let { uri ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "Vista previa de portada",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        IconButton(
                            onClick = { selectedCoverUri = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Quitar portada", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Modo de organización: Normal vs Series
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Organización de publicaciones",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = orgMode == GroupOrganizationMode.NORMAL,
                            onClick = { orgMode = GroupOrganizationMode.NORMAL },
                            label = { Text("Normal (Más recientes)") }
                        )
                        FilterChip(
                            selected = orgMode == GroupOrganizationMode.SERIES,
                            onClick = { orgMode = GroupOrganizationMode.SERIES },
                            label = { Text("Series (Episodios)") }
                        )
                    }
                }

                // Autorizar subida a miembros
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Autorizar subir contenido",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Permite que otros miembros publiquen fotos o vídeos en este grupo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = allowUploads,
                        onCheckedChange = { allowUploads = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onConfirmCreate(name.trim(), description.trim(), allowUploads, orgMode, selectedCoverUri)
                            }
                        },
                        enabled = name.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("confirm_create_group_button")
                    ) {
                        Text("Crear grupo", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ManageGroupDialog(
    group: FamilyGroup,
    groupPosts: List<Post>,
    onDismiss: () -> Unit,
    onSaveChanges: (name: String, description: String, allowUploads: Boolean, orgMode: GroupOrganizationMode, coverUri: Uri?, coverUrl: String?) -> Unit,
    onDeleteGroup: () -> Unit
) {
    var name by remember(group.id) { mutableStateOf(group.name) }
    var description by remember(group.id) { mutableStateOf(group.description) }
    var allowUploads by remember(group.id) { mutableStateOf(group.allowUploads) }
    var orgMode by remember(group.id) { mutableStateOf(group.organizationMode) }
    var selectedCoverUri by remember(group.id) { mutableStateOf<Uri?>(null) }
    var selectedCoverUrl by remember(group.id) { mutableStateOf(group.coverPhotoUrl) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val existingGroupImageUrls = remember(groupPosts) {
        groupPosts.flatMap { post ->
            post.mediaItems.mapNotNull { m ->
                when {
                    m.type == MediaType.IMAGE && m.url.isNotBlank() -> m.url
                    !m.thumbnailUrl.isNullOrBlank() && m.thumbnailUrl != m.url -> m.thumbnailUrl
                    else -> null
                }
            }
        }.distinct().take(12)
    }

    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedCoverUri = uri
            selectedCoverUrl = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp)
                .testTag("manage_group_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Administrar grupo",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del grupo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // Portada del grupo (galería o fotos del grupo)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Portada del grupo",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    OutlinedButton(
                        onClick = {
                            coverPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("De galería")
                    }
                }

                val previewModel: Any? = selectedCoverUri ?: selectedCoverUrl?.takeIf { it.isNotBlank() }
                if (previewModel != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        AsyncImage(
                            model = previewModel,
                            contentDescription = "Portada seleccionada",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                if (existingGroupImageUrls.isNotEmpty()) {
                    Text(
                        text = "O elegir imagen del grupo:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(existingGroupImageUrls) { candidateUrl ->
                            val isSelected = selectedCoverUri == null && selectedCoverUrl == candidateUrl
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) FacebookBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        selectedCoverUri = null
                                        selectedCoverUrl = candidateUrl
                                    }
                            ) {
                                AsyncImage(
                                    model = candidateUrl,
                                    contentDescription = "Miniatura candidata para portada",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                // Organización de publicaciones
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Organización de publicaciones",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = orgMode == GroupOrganizationMode.NORMAL,
                            onClick = { orgMode = GroupOrganizationMode.NORMAL },
                            label = { Text("Normal") }
                        )
                        FilterChip(
                            selected = orgMode == GroupOrganizationMode.SERIES,
                            onClick = { orgMode = GroupOrganizationMode.SERIES },
                            label = { Text("Series (Episodios)") }
                        )
                    }
                }

                // Autorizar subir contenido
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Autorizar subir contenido",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = if (allowUploads) "Todos los miembros pueden publicar" else "Solo administradores pueden publicar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = allowUploads,
                        onCheckedChange = { allowUploads = it }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("delete_group_button")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color.Red, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Eliminar grupo", color = Color.Red, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onSaveChanges(
                                    name.trim(),
                                    description.trim(),
                                    allowUploads,
                                    orgMode,
                                    selectedCoverUri,
                                    selectedCoverUrl
                                )
                            }
                        },
                        enabled = name.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("save_group_settings_button")
                    ) {
                        Text("Guardar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminar grupo") },
            text = { Text("¿Seguro que quieres eliminar el grupo \"${group.name}\"? Se eliminará de la lista de grupos del NAS.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteGroup()
                    },
                    modifier = Modifier.testTag("confirm_delete_group_button")
                ) {
                    Text("Eliminar", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
