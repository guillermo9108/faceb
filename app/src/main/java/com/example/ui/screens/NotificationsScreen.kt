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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FamilyMember
import com.example.ui.theme.FacebookBlue
import com.example.ui.theme.ReactionLikeBlue
import com.example.ui.theme.ReactionLoveRed

data class FamilyNotification(
    val id: String,
    val author: FamilyMember,
    val actionText: String,
    val timeAgo: String,
    val isUnread: Boolean,
    val badgeEmoji: String = "👍",
    val badgeColor: Color = ReactionLikeBlue
)

@Composable
fun NotificationsScreen(
    currentMember: FamilyMember,
    familyMembers: List<FamilyMember>,
    onNotificationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sampleNotifications = listOf(
        FamilyNotification(
            id = "n1",
            author = familyMembers.getOrElse(1) { currentMember },
            actionText = "ha publicado una nueva foto en el muro familiar.",
            timeAgo = "Hace 15 min",
            isUnread = true,
            badgeEmoji = "📸",
            badgeColor = FacebookBlue
        ),
        FamilyNotification(
            id = "n2",
            author = familyMembers.getOrElse(0) { currentMember },
            actionText = "reaccionó a una publicación en el Synology NAS.",
            timeAgo = "Hace 1 h",
            isUnread = true,
            badgeEmoji = "👍",
            badgeColor = ReactionLikeBlue
        ),
        FamilyNotification(
            id = "n3",
            author = familyMembers.getOrElse(4) { currentMember },
            actionText = "dejó una receta nueva en los comentarios.",
            timeAgo = "Ayer",
            isUnread = false,
            badgeEmoji = "💬",
            badgeColor = Color(0xFF45BD62)
        )
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("notifications_screen"),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Facebook Notifications Header
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 0.5.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Notificaciones",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                    )

                    IconButton(
                        onClick = onNotificationClick,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar notificaciones",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Nuevas",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        // Notification Items
        items(sampleNotifications, key = { it.id }) { notif ->
            Surface(
                color = if (notif.isUnread) FacebookBlue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNotificationClick() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar with Mini Action Badge
                    Box(modifier = Modifier.size(54.dp)) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(notif.author.avatarColorHex)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = notif.author.avatarEmoji, fontSize = 24.sp)
                        }

                        // Bottom-right mini badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(notif.badgeColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = notif.badgeEmoji, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Text & Time
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) {
                                    append(notif.author.name)
                                }
                                append(" ")
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                                    append(notif.actionText)
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 19.sp),
                            maxLines = 3
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = notif.timeAgo,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (notif.isUnread) FacebookBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (notif.isUnread) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        )
                    }

                    // Unread Blue Dot
                    if (notif.isUnread) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(FacebookBlue)
                        )
                    }

                    IconButton(onClick = onNotificationClick, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "Opciones",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), thickness = 0.5.dp)
        }
    }
}
