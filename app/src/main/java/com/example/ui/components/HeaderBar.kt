package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.People
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FamilyMember
import com.example.model.NasConfig
import com.example.ui.theme.FacebookBadgeRed
import com.example.ui.theme.FacebookBlue
import com.example.ui.theme.FacebookButtonBgDark
import com.example.ui.theme.FacebookButtonBgLight
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

            // ROW 1: Facebook-style Logotype ("familiar") + Circular Actions (+, 🔍, 🔄)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Facebook Logo Typography
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onTabSelected(FeedTab.FEED) }
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

                    // Small LAN indicator dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (nasConfig.isConnected) LanOnlineGreen else Color(0xFFF59E0B))
                    )
                }

                // Top Right Circular Action Buttons (Facebook-style)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Quick Add (+) Button
                    IconButton(
                        onClick = onOpenCreatePost,
                        modifier = Modifier
                            .size(38.dp)
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

                    // 2. Search (🔍) Button
                    IconButton(
                        onClick = { isSearchExpanded = !isSearchExpanded },
                        modifier = Modifier
                            .size(38.dp)
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

                    // 3. Refresh Synology Feed Button (🔄)
                    IconButton(
                        onClick = onRefreshFeed,
                        modifier = Modifier
                            .size(38.dp)
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

            // ROW 2: The Iconic 5 Facebook Top Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tab 1: Inicio / Muro (Home)
                FacebookTabItem(
                    selected = selectedTab == FeedTab.FEED,
                    onClick = { onTabSelected(FeedTab.FEED) },
                    iconSelected = Icons.Rounded.Home,
                    iconUnselected = Icons.Outlined.Home,
                    contentDescription = "Inicio",
                    modifier = Modifier.weight(1f).testTag("tab_feed")
                )

                // Tab 2: Watch / Videos del NAS
                FacebookTabItem(
                    selected = selectedTab == FeedTab.WATCH,
                    onClick = { onTabSelected(FeedTab.WATCH) },
                    iconSelected = Icons.Default.OndemandVideo,
                    iconUnselected = Icons.Outlined.OndemandVideo,
                    contentDescription = "Videos",
                    modifier = Modifier.weight(1f).testTag("tab_watch")
                )

                // Tab: Shorts
                FacebookTabItem(
                    selected = selectedTab == FeedTab.SHORTS,
                    onClick = { onTabSelected(FeedTab.SHORTS) },
                    iconSelected = Icons.Default.ElectricBolt,
                    iconUnselected = Icons.Default.ElectricBolt,
                    contentDescription = "Shorts",
                    modifier = Modifier.weight(1f).testTag("tab_shorts")
                )

                // Tab 3: Grupos / Familia
                FacebookTabItem(
                    selected = selectedTab == FeedTab.MEMBERS,
                    onClick = { onTabSelected(FeedTab.MEMBERS) },
                    iconSelected = Icons.Default.People,
                    iconUnselected = Icons.Outlined.People,
                    contentDescription = "Familia",
                    modifier = Modifier.weight(1f).testTag("tab_members")
                )

                // Tab 4: Notificaciones / Novedades
                FacebookTabItem(
                    selected = selectedTab == FeedTab.NOTIFICATIONS,
                    onClick = { onTabSelected(FeedTab.NOTIFICATIONS) },
                    iconSelected = Icons.Default.Notifications,
                    iconUnselected = Icons.Outlined.Notifications,
                    contentDescription = "Notificaciones",
                    badgeText = if (unreadNotificationsCount > 0) unreadNotificationsCount.toString() else null,
                    modifier = Modifier.weight(1f).testTag("tab_notifications")
                )

                // Tab 5: Menú (Avatar actual + ☰)
                FacebookMenuTabItem(
                    selected = selectedTab == FeedTab.MENU,
                    onClick = { onTabSelected(FeedTab.MENU) },
                    currentMember = currentMember,
                    modifier = Modifier.weight(1f).testTag("tab_menu")
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                thickness = 0.5.dp
            )
        }
    }
}

@Composable
private fun FacebookTabItem(
    selected: Boolean,
    onClick: () -> Unit,
    iconSelected: androidx.compose.ui.graphics.vector.ImageVector,
    iconUnselected: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    badgeText: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable { onClick() }
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (badgeText != null) {
                BadgedBox(
                    badge = {
                        Badge(
                            containerColor = FacebookBadgeRed,
                            contentColor = Color.White
                        ) {
                            Text(badgeText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (selected) iconSelected else iconUnselected,
                        contentDescription = contentDescription,
                        tint = if (selected) FacebookBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = if (selected) iconSelected else iconUnselected,
                    contentDescription = contentDescription,
                    tint = if (selected) FacebookBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Blue Underline Indicator when active
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(if (selected) FacebookBlue else Color.Transparent)
        )
    }
}

@Composable
private fun FacebookMenuTabItem(
    selected: Boolean,
    onClick: () -> Unit,
    currentMember: FamilyMember,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable { onClick() }
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            // Member avatar with hamburger mini overlay
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(currentMember.avatarColorHex)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = currentMember.avatarEmoji, fontSize = 14.sp)
            }

            // Mini hamburger badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menú",
                    tint = if (selected) FacebookBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(if (selected) FacebookBlue else Color.Transparent)
        )
    }
}
