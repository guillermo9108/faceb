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
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.model.FamilyNotification
import com.example.model.NotificationType
import com.example.ui.theme.FacebookBlue
import com.example.ui.theme.ReactionLikeBlue
import com.example.ui.theme.ReactionLoveRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotificationsScreen(
    currentMember: FamilyMember,
    notifications: List<FamilyNotification>,
    onMarkAllAsRead: () -> Unit,
    onNotificationClick: (FamilyNotification) -> Unit,
    modifier: Modifier = Modifier
) {
    val unreadCount = notifications.count { it.isUnread }

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
                    Column {
                        Text(
                            text = "Notificaciones",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            )
                        )
                        if (unreadCount > 0) {
                            Text(
                                text = "$unreadCount sin leer",
                                fontSize = 12.sp,
                                color = FacebookBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (unreadCount > 0) {
                        TextButton(
                            onClick = onMarkAllAsRead,
                            colors = ButtonDefaults.textButtonColors(contentColor = FacebookBlue),
                            modifier = Modifier.testTag("mark_all_read_button")
                        ) {
                            Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Marcar leídas", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (notifications.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(FacebookBlue.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsNone,
                                contentDescription = null,
                                tint = FacebookBlue,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Todo al día",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Cuando tu familia publique fotos, videos o comentarios en el Synology NAS, aparecerán aquí.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            item {
                Text(
                    text = if (unreadCount > 0) "Recientes" else "Anteriores",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            items(notifications, key = { it.id }) { notif ->
                val badgeColor = when (notif.type) {
                    NotificationType.POST -> FacebookBlue
                    NotificationType.SHORT -> Color(0xFFF3425F)
                    NotificationType.COMMENT -> Color(0xFF45BD62)
                    NotificationType.REACTION -> ReactionLoveRed
                    NotificationType.STORY -> Color(0xFF8B5CF6)
                }

                Surface(
                    color = if (notif.isUnread) FacebookBlue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNotificationClick(notif) }
                        .testTag("notification_item_${notif.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Avatar with Action Badge
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
                                    .background(badgeColor),
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

                            if (notif.detailText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = notif.detailText,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    maxLines = 1
                                )
                            }

                            Spacer(modifier = Modifier.height(3.dp))

                            Text(
                                text = formatTimeAgo(notif.timestamp),
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
                                    .padding(start = 8.dp)
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(FacebookBlue)
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), thickness = 0.5.dp)
            }
        }
    }
}

private fun formatTimeAgo(timeMillis: Long): String {
    val diff = (System.currentTimeMillis() - timeMillis).coerceAtLeast(0L)
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "Hace un momento"
        minutes < 60 -> "Hace ${minutes} min"
        hours < 24 -> "Hace ${hours} h"
        days == 1L -> "Ayer"
        days < 7 -> "Hace ${days} d"
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(timeMillis))
    }
}
