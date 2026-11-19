package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FamilyMember
import com.example.model.NasConfig
import com.example.ui.theme.FacebookBadgeRed
import com.example.ui.theme.FacebookBlue
import com.example.ui.theme.LanOfflineAmber
import com.example.ui.theme.LanOnlineGreen
import com.example.viewmodel.FeedTab

@Composable
fun HeaderBar(
    currentMember: FamilyMember,
    nasConfig: NasConfig,
    selectedTab: FeedTab,
    onTabSelected: (FeedTab) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenCreatePost: () -> Unit,
    onRefreshFeed: () -> Unit = {},
    unreadNotificationsCount: Int = 0,
    modifier: Modifier = Modifier
) {
    var isSearchExpanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            // Compact Top App Row: "familiar" wordmark + LAN indicator + Circular Quick Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onTabSelected(FeedTab.FEED) }
                        .padding(vertical = 2.dp, horizontal = 2.dp)
                ) {
                    Text(
                        text = "familiar",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = FacebookBlue,
                            fontSize = 28.sp,
                            letterSpacing = (-1.2).sp
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (nasConfig.isConnected) LanOnlineGreen else LanOfflineAmber)
                    )
                }

                // Top Right Circular Action Buttons (Facebook-style)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onOpenCreatePost,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("fb_quick_add_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Crear publicación",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = { isSearchExpanded = !isSearchExpanded },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("search_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Buscar",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onRefreshFeed,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("fb_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Actualizar feed",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Inline Search Bar when expanded
            AnimatedVisibility(visible = isSearchExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Buscar en el muro familiar...", fontSize = 14.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .testTag("feed_search_input"),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Limpiar")
                                }
                            }
                        }
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                thickness = 0.5.dp
            )
        }
    }
}

@Composable
fun FacebookBottomNavBar(
    currentMember: FamilyMember,
    selectedTab: FeedTab,
    unreadNotificationsCount: Int,
    onTabSelected: (FeedTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                thickness = 0.6.dp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItem(
                    selected = selectedTab == FeedTab.FEED,
                    onClick = { onTabSelected(FeedTab.FEED) },
                    iconSelected = Icons.Rounded.Home,
                    iconUnselected = Icons.Outlined.Home,
                    label = "Inicio",
                    modifier = Modifier.weight(1f).testTag("bottom_tab_feed")
                )

                BottomNavItem(
                    selected = selectedTab == FeedTab.WATCH,
                    onClick = { onTabSelected(FeedTab.WATCH) },
                    iconSelected = Icons.Default.OndemandVideo,
                    iconUnselected = Icons.Outlined.OndemandVideo,
                    label = "Watch",
                    modifier = Modifier.weight(1f).testTag("bottom_tab_watch")
                )

                BottomNavItem(
                    selected = selectedTab == FeedTab.SHORTS,
                    onClick = { onTabSelected(FeedTab.SHORTS) },
                    iconSelected = Icons.Default.ElectricBolt,
                    iconUnselected = Icons.Default.ElectricBolt,
                    label = "Shorts",
                    modifier = Modifier.weight(1f).testTag("bottom_tab_shorts")
                )

                BottomNavItem(
                    selected = selectedTab == FeedTab.GROUPS,
                    onClick = { onTabSelected(FeedTab.GROUPS) },
                    iconSelected = Icons.Default.Groups,
                    iconUnselected = Icons.Outlined.Groups,
                    label = "Grupos",
                    modifier = Modifier.weight(1f).testTag("bottom_tab_groups")
                )

                BottomNavItem(
                    selected = selectedTab == FeedTab.NOTIFICATIONS,
                    onClick = { onTabSelected(FeedTab.NOTIFICATIONS) },
                    iconSelected = Icons.Default.Notifications,
                    iconUnselected = Icons.Outlined.Notifications,
                    label = "Alertas",
                    badgeText = if (unreadNotificationsCount > 0) unreadNotificationsCount.toString() else null,
                    modifier = Modifier.weight(1f).testTag("bottom_tab_notifications")
                )

                BottomMenuNavItem(
                    selected = selectedTab == FeedTab.MENU,
                    onClick = { onTabSelected(FeedTab.MENU) },
                    currentMember = currentMember,
                    modifier = Modifier.weight(1f).testTag("bottom_tab_menu")
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    iconSelected: ImageVector,
    iconUnselected: ImageVector,
    label: String,
    badgeText: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Active Top Indicator Bar (Facebook mobile bottom bar style)
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                .background(if (selected) FacebookBlue else Color.Transparent)
        )

        Spacer(modifier = Modifier.height(6.dp))

        if (badgeText != null) {
            BadgedBox(
                badge = {
                    Badge(
                        containerColor = FacebookBadgeRed,
                        contentColor = Color.White
                    ) {
                        Text(badgeText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            ) {
                Icon(
                    imageVector = if (selected) iconSelected else iconUnselected,
                    contentDescription = label,
                    tint = if (selected) FacebookBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        } else {
            Icon(
                imageVector = if (selected) iconSelected else iconUnselected,
                contentDescription = label,
                tint = if (selected) FacebookBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) FacebookBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BottomMenuNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    currentMember: FamilyMember,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                .background(if (selected) FacebookBlue else Color.Transparent)
        )

        Spacer(modifier = Modifier.height(5.dp))

        Box(
            modifier = Modifier.size(26.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(currentMember.avatarColorHex))
                    .border(
                        width = if (selected) 1.5.dp else 0.5.dp,
                        color = if (selected) FacebookBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = currentMember.avatarEmoji, fontSize = 13.sp)
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menú",
                    tint = if (selected) FacebookBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(9.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "Menú",
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) FacebookBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
