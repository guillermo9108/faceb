package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.FamilyGroup
import com.example.model.FamilyMember
import com.example.model.FamilyStory
import com.example.model.NasConfig
import com.example.model.Post
import com.example.model.ReactionType
import com.example.ui.components.CreatePostCard
import com.example.ui.components.PostCard
import com.example.ui.components.StoriesSection
import com.example.ui.theme.FacebookBlue
import com.example.ui.theme.FamilyBluePrimary
import com.example.ui.theme.LanOfflineAmber
import com.example.ui.theme.LanOnlineGreen
import com.example.util.FeedStreamEntry
import com.example.util.FeedStreamHelper
import com.example.util.OfflineMediaManager

@Composable
fun FeedScreen(
    currentMember: FamilyMember,
    familyMembers: List<FamilyMember>,
    userStoryGroups: List<com.example.model.UserStoryGroup>,
    posts: List<Post>,
    nasConfig: NasConfig,
    isLoading: Boolean,
    selectedMemberFilter: FamilyMember?,
    searchQuery: String,
    onFilterMember: (FamilyMember?) -> Unit,
    onRefreshFeed: () -> Unit,
    onOpenCreatePost: () -> Unit,
    onOpenCreateStory: () -> Unit,
    onStoryClick: (com.example.model.UserStoryGroup) -> Unit,
    onReactionClick: (Int, ReactionType) -> Unit,
    onCommentClick: (Post) -> Unit,
    onTogglePin: (Int) -> Unit,
    onDeletePost: (Int) -> Unit,
    onPlayVideo: (videoUrl: String, title: String, authorName: String, durationLabel: String?, chunks: List<String>, chunkDurationsMs: List<Long>, totalDurationMs: Long) -> Unit,
    onOpenNasConfig: () -> Unit,
    onOpenErrorLogs: (() -> Unit)? = null,
    onSendComment: (Int, String) -> Unit = { _, _ -> },
    hasMorePosts: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onToggleSaveOffline: (Post) -> Unit = {},
    savedOfflineOnly: Boolean = false,
    onClearOfflineFilter: () -> Unit = {},
    feedEntries: List<FeedStreamEntry> = FeedStreamHelper.buildFeedEntries(posts, emptyList()),
    isSearchingGlobal: Boolean = false,
    groups: List<FamilyGroup> = emptyList(),
    followedUserIds: Set<Int> = emptySet(),
    followedGroupIds: Set<Int> = emptySet(),
    onToggleFollowUser: (Int) -> Unit = {},
    onToggleFollowGroup: (Int) -> Unit = {},
    onOpenShort: (Post) -> Unit = {},
    onOpenAllShorts: () -> Unit = {},
    onVideoStarted: ((postId: Int, videoUrl: String, fallbackChunkUrl: String?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val thumbTick by OfflineMediaManager.thumbnailTick.collectAsState()
    val groupCoverMap = remember(groups) {
        groups.associate { it.id to it.coverPhotoUrl }
    }
    val suggestedMembers = remember(familyMembers, currentMember.id, followedUserIds) {
        familyMembers.filter { it.id != currentMember.id && !followedUserIds.contains(it.id) }
    }
    val suggestedGroups = remember(groups, followedGroupIds) {
        groups.filter { !followedGroupIds.contains(it.id) }
    }
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
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("feed_list"),
        contentPadding = PaddingValues(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. LAN Real Connection Banner
        item(key = "lan_banner", contentType = "banner") {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (nasConfig.isConnected) LanOnlineGreen.copy(alpha = 0.12f) else LanOfflineAmber.copy(alpha = 0.12f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (nasConfig.isConnected) LanOnlineGreen else LanOfflineAmber)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = when {
                                    nasConfig.isConnected -> "Synology NAS: Conectado (${nasConfig.ip}:${nasConfig.port})"
                                    posts.isNotEmpty() -> "Modo sin conexión · Caché local (${posts.size} publicaciones)"
                                    else -> "Synology NAS: Esperando conexión LAN"
                                },
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (nasConfig.isConnected) LanOnlineGreen else LanOfflineAmber
                                )
                            )
                            if (!nasConfig.isConnected && nasConfig.lastError != null) {
                                Text(
                                    text = nasConfig.lastError,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                if (onOpenErrorLogs != null) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "🔍 Ver registro de errores",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            color = FacebookBlue,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.clickable { onOpenErrorLogs() }
                                    )
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = FamilyBluePrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        IconButton(
                            onClick = onRefreshFeed,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sincronizar",
                                tint = FamilyBluePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. "What's on your mind?" Composer Card (Directly below header tabs, like Facebook)
        item(key = "composer_card", contentType = "composer") {
            CreatePostCard(
                currentMember = currentMember,
                onOpenCreatePost = onOpenCreatePost
            )
        }

        // 3. Stories Row (Directly below composer widget)
        item(key = "stories_section", contentType = "stories") {
            StoriesSection(
                currentMember = currentMember,
                userStoryGroups = userStoryGroups,
                onCreateStoryClick = onOpenCreateStory,
                onStoryClick = onStoryClick
            )
        }

        // 4. Family Member Filter Chips Row
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedMemberFilter == null,
                            onClick = { onFilterMember(null) },
                            label = { Text("Toda la familia") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = FacebookBlue,
                                selectedLabelColor = Color.White
                            )
                        )
                    }

                    items(familyMembers, key = { it.id }) { member ->
                        val isSelected = selectedMemberFilter?.id == member.id
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) onFilterMember(null) else onFilterMember(member)
                            },
                            label = { Text("${member.avatarEmoji} ${member.role}") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = FacebookBlue,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        // Active search / filter badge (búsqueda ligera en toda la BD o filtros activos)
        if (selectedMemberFilter != null || searchQuery.isNotBlank() || savedOfflineOnly) {
            item(key = "active_filter_banner") {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = FacebookBlue.copy(alpha = 0.1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isSearchingGlobal) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    strokeWidth = 2.dp,
                                    color = FacebookBlue
                                )
                            } else if (searchQuery.isNotBlank()) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = FacebookBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Text(
                                text = buildString {
                                    if (searchQuery.isNotBlank()) {
                                        if (isSearchingGlobal) {
                                            append("Buscando '$searchQuery' en toda la BD...")
                                        } else {
                                            append("${posts.size} resultado(s) en toda la BD para '$searchQuery'")
                                        }
                                    } else {
                                        append("Filtro activo: ")
                                        if (savedOfflineOnly) append("Guardados offline ")
                                        if (selectedMemberFilter != null) append("Solo ${selectedMemberFilter.name}")
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = FacebookBlue
                            )
                        }

                        if (selectedMemberFilter != null || savedOfflineOnly) {
                            IconButton(
                                onClick = {
                                    onFilterMember(null)
                                    onClearOfflineFilter()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Limpiar filtro",
                                    tint = FacebookBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sugerencias de personas y grupos para seguir directamente desde el Muro
        if (searchQuery.isBlank() && !savedOfflineOnly && (suggestedMembers.isNotEmpty() || suggestedGroups.isNotEmpty())) {
            item(key = "follow_suggestions", contentType = "follow_suggestions") {
                FollowSuggestionsRow(
                    suggestedMembers = suggestedMembers,
                    suggestedGroups = suggestedGroups,
                    onFollowMember = onToggleFollowUser,
                    onFollowGroup = onToggleFollowGroup
                )
            }
        }

        // Estado vacío cuando no hay publicaciones visibles (curadas o búsqueda)
        if (feedEntries.isEmpty() && !isSearchingGlobal) {
            item(key = "feed_empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp, horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = when {
                                searchQuery.isNotBlank() -> "🔍"
                                savedOfflineOnly -> "☁️"
                                else -> "🏡"
                            },
                            fontSize = 46.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = when {
                                searchQuery.isNotBlank() -> "Sin resultados para \"$searchQuery\""
                                savedOfflineOnly -> "No tienes publicaciones guardadas offline"
                                !nasConfig.isConnected -> "Esperando conexión con el Synology NAS"
                                suggestedMembers.isNotEmpty() || suggestedGroups.isNotEmpty() ->
                                    "Tu Muro sigue a quienes elijas"
                                else -> "No hay publicaciones en tu Muro todavía"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when {
                                searchQuery.isNotBlank() -> "Se buscó en toda la base de datos y no hubo coincidencias."
                                savedOfflineOnly -> "Pulsa \"Descargar\" en cualquier publicación para guardarla completa en tu dispositivo."
                                !nasConfig.isConnected -> "Verifica que el Synology NAS esté encendido en la red Wi-Fi y que el servicio Web Station en el puerto ${nasConfig.port} esté iniciado."
                                suggestedMembers.isNotEmpty() || suggestedGroups.isNotEmpty() ->
                                    "Aquí verás tus publicaciones y las de los familiares o grupos que sigas. ¡Toca \"Seguir\" arriba o crea tu primera publicación!"
                                else -> "¡Sé el primero en compartir un momento, foto o vídeo familiar en el muro local!"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onRefreshFeed) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Actualizar")
                            }

                            Button(
                                onClick = onOpenCreatePost,
                                colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue)
                            ) {
                                Text("Crear publicación")
                            }
                        }
                    }
                }
            }
        } else {
            // 5. Publicaciones curadas (10 en 10) + Carruseles intercalados de Shorts familiares
            items(
                items = feedEntries,
                key = { it.stableKey },
                contentType = { entry ->
                    when (entry) {
                        is FeedStreamEntry.PostEntry -> "post_card"
                        is FeedStreamEntry.ShortsGroupEntry -> "shorts_carousel"
                    }
                }
            ) { entry ->
                when (entry) {
                    is FeedStreamEntry.PostEntry -> {
                        val post = entry.post
                        PostCard(
                            post = post,
                            onReactionClick = { reactionType -> onReactionClick(post.id, reactionType) },
                            onCommentClick = { onCommentClick(post) },
                            onTogglePin = { onTogglePin(post.id) },
                            onDeletePost = { onDeletePost(post.id) },
                            onPlayVideo = onPlayVideo,
                            onSendComment = { text -> onSendComment(post.id, text) },
                            onToggleSaveOffline = { onToggleSaveOffline(post) },
                            fallbackFolderImageUrl = groupCoverMap[post.groupId],
                            onVideoStarted = onVideoStarted
                        )
                    }
                    is FeedStreamEntry.ShortsGroupEntry -> {
                        ShortsCarouselSection(
                            shorts = entry.shorts,
                            groupCoverMap = groupCoverMap,
                            thumbTick = thumbTick,
                            context = context,
                            onShortClick = onOpenShort,
                            onSeeAllShorts = onOpenAllShorts
                        )
                    }
                }
            }

            // 6. Indicador de paginación inferior (solo fuera de búsqueda global)
            if (searchQuery.isBlank()) {
                item(key = "feed_pagination_footer", contentType = "footer") {
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
                                text = "Has llegado al final de tu muro (${posts.size} publicaciones)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            OutlinedButton(onClick = onLoadMore) {
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
private fun FollowSuggestionsRow(
    suggestedMembers: List<FamilyMember>,
    suggestedGroups: List<FamilyGroup>,
    onFollowMember: (Int) -> Unit,
    onFollowGroup: (Int) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("follow_suggestions_section")
    ) {
        Column(modifier = Modifier.padding(vertical = 10.dp)) {
            Text(
                text = "Descubre a tu familia y grupos para tu Muro",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(suggestedMembers, key = { "sug_user_${it.id}" }) { member ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.width(142.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(member.avatarColorHex)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = member.avatarEmoji, fontSize = 22.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = member.name,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = member.role,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { onFollowMember(member.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("suggest_follow_user_${member.id}")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Seguir", fontSize = 12.sp)
                            }
                        }
                    }
                }

                items(suggestedGroups, key = { "sug_group_${it.id}" }) { group ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.width(156.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(FacebookBlue.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!group.coverPhotoUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = group.coverPhotoUrl,
                                        contentDescription = group.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Groups,
                                        contentDescription = null,
                                        tint = FacebookBlue,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = group.name,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${group.postsCount} pub. · Grupo",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { onFollowGroup(group.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("suggest_follow_group_${group.id}")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Seguir", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortsCarouselSection(
    shorts: List<Post>,
    groupCoverMap: Map<Int, String?>,
    thumbTick: Int,
    context: android.content.Context,
    onShortClick: (Post) -> Unit,
    onSeeAllShorts: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("interleaved_shorts_carousel")
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE1306C)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "Shorts familiares",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Text(
                    text = "Ver todos",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = FacebookBlue,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSeeAllShorts() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(shorts, key = { "reel_card_${it.id}" }) { shortPost ->
                    val media = shortPost.mediaItems.firstOrNull()
                    val displayModel = remember(media?.thumbnailUrl, media?.url, shortPost.groupId, thumbTick, groupCoverMap) {
                        if (media == null) null
                        else OfflineMediaManager.getDisplayModel(
                            context = context,
                            url = media.thumbnailUrl,
                            isVideo = true,
                            fallbackFolderImageUrl = shortPost.groupId?.let { groupCoverMap[it] },
                            videoUrl = media.url
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(138.dp)
                            .height(236.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFF1F2937), Color(0xFF111827))
                                )
                            )
                            .clickable { onShortClick(shortPost) }
                            .testTag("feed_short_card_${shortPost.id}")
                    ) {
                        if (displayModel != null) {
                            AsyncImage(
                                model = displayModel,
                                contentDescription = shortPost.contentText.ifBlank { "Short de ${shortPost.author.name}" },
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.28f),
                                modifier = Modifier
                                    .size(38.dp)
                                    .align(Alignment.Center)
                            )
                        }

                        // Gradiente inferior para lectura clara
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.78f)
                                        )
                                    )
                                )
                        )

                        // Etiqueta superior Play + duración
                        Surface(
                            color = Color.Black.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = media?.durationLabel?.takeIf { it.isNotBlank() } ?: "Short",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Autor y descripción
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color(shortPost.author.avatarColorHex)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = shortPost.author.avatarEmoji, fontSize = 11.sp)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = shortPost.author.name,
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (shortPost.contentText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = shortPost.contentText,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
