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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun FeedScreen(
    currentMember: FamilyMember,
    familyMembers: List<FamilyMember>,
    stories: List<FamilyStory>,
    posts: List<Post>,
    nasConfig: NasConfig,
    isLoading: Boolean,
    selectedMemberFilter: FamilyMember?,
    searchQuery: String,
    onFilterMember: (FamilyMember?) -> Unit,
    onRefreshFeed: () -> Unit,
    onOpenCreatePost: () -> Unit,
    onReactionClick: (Int, ReactionType) -> Unit,
    onCommentClick: (Post) -> Unit,
    onTogglePin: (Int) -> Unit,
    onDeletePost: (Int) -> Unit,
    onOpenNasConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("feed_list"),
        contentPadding = PaddingValues(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. LAN Real Connection Banner
        item {
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
                                text = if (nasConfig.isConnected) {
                                    "Synology NAS: Conectado (${nasConfig.ip}:${nasConfig.port})"
                                } else {
                                    "Synology NAS: Esperando conexión LAN"
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
        item {
            CreatePostCard(
                currentMember = currentMember,
                onOpenCreatePost = onOpenCreatePost
            )
        }

        // 3. Stories Row (Directly below composer widget)
        if (stories.isNotEmpty()) {
            item {
                StoriesSection(
                    currentMember = currentMember,
                    stories = stories,
                    onCreateStoryClick = onOpenCreatePost
                )
            }
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

        // Active filter badge if any
        if (selectedMemberFilter != null || searchQuery.isNotBlank()) {
            item {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = FacebookBlue.copy(alpha = 0.1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = buildString {
                                append("Filtro activo: ")
                                if (selectedMemberFilter != null) append("Solo ${selectedMemberFilter.name} ")
                                if (searchQuery.isNotBlank()) append("'$searchQuery'")
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = FacebookBlue
                        )

                        IconButton(
                            onClick = { onFilterMember(null) },
                            modifier = Modifier.size(24.dp)
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

        // Estado vacío real cuando no hay publicaciones en el NAS
        if (posts.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp, horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "🏡", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (!nasConfig.isConnected) "Esperando conexión con el Synology NAS" else "No hay publicaciones en el NAS todavía",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (!nasConfig.isConnected) {
                                "Verifica que el Synology NAS esté encendido en la red Wi-Fi y que el servicio Web Station en el puerto ${nasConfig.port} esté iniciado."
                            } else {
                                "¡Sé el primero en compartir un momento, foto o vídeo familiar en el muro local!"
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
                                Text("Reintentar")
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
            // 5. Publicaciones reales del muro (Full-width edge-to-edge con separador)
            items(posts, key = { it.id }) { post ->
                PostCard(
                    post = post,
                    onReactionClick = { reactionType -> onReactionClick(post.id, reactionType) },
                    onCommentClick = { onCommentClick(post) },
                    onTogglePin = { onTogglePin(post.id) },
                    onDeletePost = { onDeletePost(post.id) }
                )
            }
        }
    }
}
