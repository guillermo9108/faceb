package com.example.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FamilyMember
import com.example.ui.theme.FacebookFeelingYellow
import com.example.ui.theme.FacebookLiveRed
import com.example.ui.theme.FacebookPhotoGreen
import com.example.ui.theme.LanOnlineGreen

@Composable
fun CreatePostCard(
    currentMember: FamilyMember,
    onOpenCreatePost: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.5.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("create_post_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // Top Row: Avatar + Rounded Gray Input Pill + Quick Photo Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // Member Avatar with active status green dot
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(currentMember.avatarColorHex)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = currentMember.avatarEmoji, fontSize = 20.sp)
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(LanOnlineGreen)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // The iconic Facebook "¿Qué estás pensando?" pill
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onOpenCreatePost() }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "¿Qué estás pensando, ${currentMember.role}?",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Quick photo icon on the right
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onOpenCreatePost() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "Foto",
                        tint = FacebookPhotoGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                thickness = 0.5.dp
            )

            // Bottom Row: The 3 Classic Facebook Buttons (En vivo, Foto/video, Sentimiento/act.)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. En vivo (Cámara / Video rápido)
                FacebookComposerActionButton(
                    icon = Icons.Default.Videocam,
                    iconColor = FacebookLiveRed,
                    label = "En vivo",
                    onClick = onOpenCreatePost
                )

                // Thin vertical separator
                Box(
                    modifier = Modifier
                        .height(18.dp)
                        .width(0.5.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                )

                // 2. Foto/video
                FacebookComposerActionButton(
                    icon = Icons.Default.AddPhotoAlternate,
                    iconColor = FacebookPhotoGreen,
                    label = "Foto/video",
                    onClick = onOpenCreatePost
                )

                // Thin vertical separator
                Box(
                    modifier = Modifier
                        .height(18.dp)
                        .width(0.5.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                )

                // 3. Sentimiento/act.
                FacebookComposerActionButton(
                    icon = Icons.Default.EmojiEmotions,
                    iconColor = FacebookFeelingYellow,
                    label = "Sentimiento/act.",
                    onClick = onOpenCreatePost
                )
            }
        }
    }
}

@Composable
private fun FacebookComposerActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    label: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
