package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.FamilyMember
import com.example.model.FamilyStory
import com.example.ui.theme.FacebookBlue

@Composable
fun StoriesSection(
    currentMember: FamilyMember,
    stories: List<FamilyStory>,
    onCreateStoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.5.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // First item: Facebook "Crear historia" Card
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .width(105.dp)
                        .height(180.dp)
                        .clickable { onCreateStoryClick() }
                        .testTag("create_story_card")
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Top 68%: Member Avatar preview area
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.68f)
                                    .background(Color(currentMember.avatarColorHex).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = currentMember.avatarEmoji, fontSize = 42.sp)
                            }

                            // Bottom 32%: White card area with "Crear historia"
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.32f)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Text(
                                    text = "Crear\nhistoria",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        lineHeight = 14.sp
                                    ),
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }

                        // Centered Facebook Blue '+' Circle right on the partition line
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(top = 40.dp)
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(FacebookBlue)
                                .border(2.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Crear historia",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // Real Family Stories Cards (with Facebook blue ring and dark gradient scrim)
            items(stories, key = { it.id }) { story ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .width(105.dp)
                        .height(180.dp)
                        .testTag("story_card_${story.id}")
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Full-bleed Story Image
                        AsyncImage(
                            model = story.imageUrl,
                            contentDescription = story.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Facebook-style vertical dark gradient scrim
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.4f),
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.75f)
                                        )
                                    )
                                )
                        )

                        // Top-left: Avatar with Iconic Facebook Blue Ring
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(FacebookBlue)
                                .padding(2.5.dp)
                                .clip(CircleShape)
                                .background(Color(story.author.avatarColorHex)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = story.author.avatarEmoji, fontSize = 16.sp)
                        }

                        // Bottom-left: Author Name in Bold White
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = story.author.name,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    shadow = androidx.compose.ui.graphics.Shadow(
                                        color = Color.Black,
                                        blurRadius = 4f
                                    )
                                ),
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
