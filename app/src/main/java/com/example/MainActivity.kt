package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.ReactionType
import com.example.model.NotificationType
import com.example.ui.components.AdminFolderScanDialog
import com.example.ui.components.CommentsSheet
import com.example.ui.components.CreatePostDialog
import com.example.ui.components.CreateStoryDialog
import com.example.ui.components.ErrorLogsDialog
import com.example.ui.components.FacebookBottomNavBar
import com.example.ui.components.HeaderBar
import com.example.ui.components.NasIpConfirmationDialog
import com.example.ui.components.ProfileSettingsDialog
import com.example.ui.components.StoryViewerDialog
import com.example.ui.components.VideoPlayerDialog
import com.example.ui.screens.FamilyMembersScreen
import com.example.ui.screens.FeedScreen
import com.example.ui.screens.GroupsScreen
import com.example.ui.screens.LoginRegisterScreen
import com.example.ui.screens.MenuScreen
import com.example.ui.screens.NasConfigScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.PhotosScreen
import com.example.ui.screens.ShortsScreen
import com.example.ui.screens.WatchScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.FamilyFeedViewModel
import com.example.viewmodel.FeedTab
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                FamilyWallApp()
            }
        }
    }
}

@Composable
fun FamilyWallApp(viewModel: FamilyFeedViewModel = viewModel()) {
    val currentMember by viewModel.currentMember.collectAsState()
    val nasConfig by viewModel.nasConfig.collectAsState()
    val groupedStories by viewModel.groupedStories.collectAsState()
    val posts by viewModel.filteredPosts.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedMemberFilter by viewModel.selectedMemberFilter.collectAsState()
    val selectedPostForComments by viewModel.activePostForComments.collectAsState()
    val isCreatePostDialogOpen by viewModel.isCreatePostDialogOpen.collectAsState()
    val isCreateShortMode by viewModel.isCreateShortMode.collectAsState()
    val isCreateStoryDialogOpen by viewModel.isCreateStoryDialogOpen.collectAsState()
    val selectedStoryGroupForViewing by viewModel.selectedStoryGroupForViewing.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val unreadNotificationsCount by viewModel.unreadNotificationsCount.collectAsState()
    val showNasIpConfirmation by viewModel.showNasIpConfirmation.collectAsState()
    val isAutoDetectingNas by viewModel.isAutoDetectingNas.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    data class ActiveVideoPlayInfo(
        val url: String,
        val title: String,
        val author: String = "",
        val duration: String? = null,
        val chunks: List<String> = emptyList(),
        val chunkDurationsMs: List<Long> = emptyList(),
        val totalDurationMs: Long = 0L
    )

    var activeVideoToPlay by remember { mutableStateOf<ActiveVideoPlayInfo?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    val familyMembers by viewModel.familyMembers.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasMoreFeedPosts by viewModel.hasMoreFeedPosts.collectAsState()
    val isLoadingMoreFeed by viewModel.isLoadingMoreFeed.collectAsState()
    val isUserLoggedIn by viewModel.isUserLoggedIn.collectAsState()
    val shorts by viewModel.shorts.collectAsState()
    val isProfileDialogOpen by viewModel.isProfileDialogOpen.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val selectedGroupForViewing by viewModel.selectedGroupForViewing.collectAsState()
    val groupPosts by viewModel.groupPosts.collectAsState()
    val isScanFoldersDialogOpen by viewModel.isScanFoldersDialogOpen.collectAsState()
    val scannedFolders by viewModel.scannedFolders.collectAsState()
    val isScanningFolders by viewModel.isScanningFolders.collectAsState()
    val isImportingFolders by viewModel.isImportingFolders.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()
    val videoUploadProgress by viewModel.videoUploadProgress.collectAsState()
    val isRepairingDb by viewModel.isRepairingDb.collectAsState()
    val errorLogs by viewModel.errorLogs.collectAsState()
    val isErrorLogsDialogOpen by viewModel.isErrorLogsDialogOpen.collectAsState()
    val diagnosticReport by viewModel.diagnosticReport.collectAsState()
    val isRunningDiagnostics by viewModel.isRunningDiagnostics.collectAsState()
    val detectedVolumes by viewModel.detectedVolumes.collectAsState()
    val savedOfflineOnly by viewModel.savedOfflineOnlyFilter.collectAsState()
    val savedOfflineCount by viewModel.savedOfflineCount.collectAsState()

    // Mostrar snackbar cuando haya un mensaje de estado
    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatus()
        }
    }

    // Si el usuario no ha iniciado sesión, mostrar la pantalla de Login / Registro
    if (!isUserLoggedIn) {
        Box(modifier = Modifier.fillMaxSize()) {
            LoginRegisterScreen(
                members = familyMembers,
                nasIp = nasConfig.ip,
                onLogin = { u, p, remember -> viewModel.login(u, p, remember) },
                onRegister = { u, nick, p, emoji, colorHex, remember ->
                    viewModel.register(
                        username = u,
                        displayName = nick,
                        pass = p,
                        avatarEmoji = emoji,
                        avatarColorHex = colorHex,
                        rememberSession = remember
                    )
                },
                onOpenNasConfig = { viewModel.retryAutoDetectNas() }
            )

            if (showNasIpConfirmation) {
                NasIpConfirmationDialog(
                    initialIp = nasConfig.ip,
                    initialPort = nasConfig.port,
                    initialPath = nasConfig.path,
                    isScanning = isAutoDetectingNas,
                    onConfirm = { ip, port, path -> viewModel.confirmNasIp(ip, port, path) },
                    onRetryAutoDetect = { viewModel.retryAutoDetectNas() },
                    onDismiss = { viewModel.dismissNasIpConfirmation() }
                )
            }
        }
        return
    }

    // Pestañas principales navegables con swipe lateral y barra inferior
    val primaryTabs = remember {
        listOf(
            FeedTab.FEED,
            FeedTab.WATCH,
            FeedTab.SHORTS,
            FeedTab.GROUPS,
            FeedTab.NOTIFICATIONS,
            FeedTab.MENU
        )
    }
    val initialPrimaryIndex = remember { primaryTabs.indexOf(selectedTab).coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = initialPrimaryIndex, pageCount = { primaryTabs.size })

    // Visibilidad automática de la barra inferior al hacer scroll vertical (estilo Facebook)
    var isBottomBarVisible by remember { mutableStateOf(true) }

    LaunchedEffect(selectedTab) {
        isBottomBarVisible = (selectedTab != FeedTab.SHORTS)
        val targetIndex = primaryTabs.indexOf(selectedTab)
        if (targetIndex >= 0 && pagerState.currentPage != targetIndex) {
            pagerState.animateScrollToPage(targetIndex)
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        if (selectedTab in primaryTabs) {
            val settledTab = primaryTabs[pagerState.settledPage]
            if (selectedTab != settledTab) {
                viewModel.setSelectedTab(settledTab)
            }
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (abs(available.y) > abs(available.x)) {
                    if (available.y < -12f) {
                        isBottomBarVisible = false
                    } else if (available.y > 12f) {
                        isBottomBarVisible = true
                    }
                }
                return Offset.Zero
            }
        }
    }

    // Manejo de retroceso: subrutas vuelven al Menú, otras pestañas vuelven al Inicio
    val isSecondaryScreen = selectedTab !in primaryTabs
    BackHandler(enabled = selectedTab != FeedTab.FEED) {
        if (isSecondaryScreen) {
            viewModel.setSelectedTab(FeedTab.MENU)
        } else {
            viewModel.setSelectedTab(FeedTab.FEED)
        }
    }

    val showBottomBar = isBottomBarVisible && selectedTab != FeedTab.SHORTS && selectedTab in primaryTabs

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        Scaffold(
            topBar = {
                AnimatedVisibility(
                    visible = selectedTab != FeedTab.SHORTS,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                ) {
                    HeaderBar(
                        currentMember = currentMember,
                        nasConfig = nasConfig,
                        selectedTab = selectedTab,
                        onTabSelected = { viewModel.setSelectedTab(it) },
                        searchQuery = searchQuery,
                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                        onOpenCreatePost = { viewModel.openCreatePostDialog() },
                        onRefreshFeed = { viewModel.refreshFeed() },
                        unreadNotificationsCount = unreadNotificationsCount
                    )
                }
            },
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = if (showBottomBar) 68.dp else 12.dp)
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            if (!isSecondaryScreen) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = innerPadding.calculateTopPadding())
                ) { pageIndex ->
                    when (primaryTabs[pageIndex]) {
                        FeedTab.FEED -> {
                            FeedScreen(
                                currentMember = currentMember,
                                familyMembers = familyMembers,
                                userStoryGroups = groupedStories,
                                posts = posts,
                                nasConfig = nasConfig,
                                isLoading = isLoading,
                                selectedMemberFilter = selectedMemberFilter,
                                searchQuery = searchQuery,
                                onFilterMember = { viewModel.setMemberFilter(it) },
                                onRefreshFeed = { viewModel.refreshFeed() },
                                onOpenCreatePost = { viewModel.openCreatePostDialog() },
                                onOpenCreateStory = { viewModel.openCreateStoryDialog() },
                                onStoryClick = { group -> viewModel.openStoryViewer(group) },
                                onReactionClick = { postId, rx -> viewModel.reactToPost(postId, rx) },
                                onCommentClick = { post -> viewModel.openCommentsSheet(post) },
                                onTogglePin = { postId -> viewModel.togglePinPost(postId) },
                                onDeletePost = { postId -> viewModel.deletePost(postId) },
                                onPlayVideo = { url, title, author, dur, chunks, chunkDurations, totalDur ->
                                    activeVideoToPlay = ActiveVideoPlayInfo(url, title, author, dur, chunks, chunkDurations, totalDur)
                                },
                                onOpenNasConfig = { viewModel.setSelectedTab(FeedTab.NAS_SETTINGS) },
                                onOpenErrorLogs = { viewModel.openErrorLogsDialog() },
                                onSendComment = { postId, text -> viewModel.addCommentToPost(postId, text) },
                                hasMorePosts = hasMoreFeedPosts,
                                isLoadingMore = isLoadingMoreFeed,
                                onLoadMore = { viewModel.loadMoreFeed() },
                                onToggleSaveOffline = { post -> viewModel.toggleSavePostOffline(post) },
                                savedOfflineOnly = savedOfflineOnly,
                                onClearOfflineFilter = { viewModel.setSavedOfflineFilter(false) }
                            )
                        }
                        FeedTab.WATCH -> {
                            WatchScreen(
                                posts = posts,
                                onReactionClick = { postId, rx -> viewModel.reactToPost(postId, rx) },
                                onCommentClick = { post -> viewModel.openCommentsSheet(post) },
                                onTogglePin = { postId -> viewModel.togglePinPost(postId) },
                                onDeletePost = { postId -> viewModel.deletePost(postId) },
                                onOpenCreatePost = { viewModel.openCreatePostDialog() },
                                onPlayVideo = { url, title, author, dur, chunks, chunkDurations, totalDur ->
                                    activeVideoToPlay = ActiveVideoPlayInfo(url, title, author, dur, chunks, chunkDurations, totalDur)
                                },
                                onToggleSaveOffline = { post -> viewModel.toggleSavePostOffline(post) }
                            )
                        }
                        FeedTab.SHORTS -> {
                            ShortsScreen(
                                shorts = shorts,
                                members = familyMembers,
                                baseUrl = nasConfig.baseUrl,
                                onLikePost = { postId -> viewModel.reactToPost(postId, ReactionType.LIKE) },
                                onOpenComments = { post -> viewModel.openCommentsSheet(post) },
                                onOpenCreateShort = { viewModel.openCreatePostDialog(isShortMode = true) },
                                onRefresh = { viewModel.refreshShorts() },
                                onShortViewed = { viewModel.markShortAsViewed(it) },
                                isScreenActive = (selectedTab == FeedTab.SHORTS && pagerState.currentPage == 2),
                                onBackClick = { viewModel.setSelectedTab(FeedTab.FEED) }
                            )
                        }
                        FeedTab.GROUPS -> {
                            GroupsScreen(
                                currentMember = currentMember,
                                groups = groups,
                                selectedGroup = selectedGroupForViewing,
                                groupPosts = groupPosts,
                                baseUrl = nasConfig.baseUrl,
                                onSelectGroup = { group -> viewModel.openGroup(group) },
                                onCloseGroupDetail = { viewModel.closeGroup() },
                                onOpenScanDialog = { viewModel.openScanFoldersDialog() },
                                onRefreshGroups = { viewModel.refreshGroups() },
                                onReactionClick = { postId, rx -> viewModel.reactToPost(postId, rx) },
                                onCommentClick = { post -> viewModel.openCommentsSheet(post) },
                                onPlayVideo = { url, title, author, dur, chunks, chunkDurations, totalDur ->
                                    activeVideoToPlay = ActiveVideoPlayInfo(url, title, author.name, dur, chunks, chunkDurations, totalDur)
                                },
                                onTogglePin = { postId -> viewModel.togglePinPost(postId) },
                                onDeletePost = { postId -> viewModel.deletePost(postId) },
                                onToggleSaveOffline = { post -> viewModel.toggleSavePostOffline(post) }
                            )
                        }
                        FeedTab.NOTIFICATIONS -> {
                            NotificationsScreen(
                                currentMember = currentMember,
                                notifications = notifications,
                                onMarkAllAsRead = { viewModel.markAllNotificationsAsRead() },
                                onNotificationClick = { notif ->
                                    viewModel.markNotificationAsRead(notif.id)
                                    if (notif.type == NotificationType.SHORT) {
                                        viewModel.setSelectedTab(FeedTab.SHORTS)
                                    } else {
                                        viewModel.setSelectedTab(FeedTab.FEED)
                                    }
                                }
                            )
                        }
                        FeedTab.MENU -> {
                            MenuScreen(
                                currentMember = currentMember,
                                nasConfig = nasConfig,
                                familyMembers = familyMembers,
                                groups = groups,
                                savedOfflineCount = savedOfflineCount,
                                onOpenGroup = { group ->
                                    viewModel.openGroup(group)
                                    viewModel.setSelectedTab(FeedTab.GROUPS)
                                },
                                onFilterMember = { member ->
                                    viewModel.setMemberFilter(member)
                                    viewModel.setSavedOfflineFilter(false)
                                    viewModel.setSelectedTab(FeedTab.FEED)
                                },
                                onNavigateToSavedOffline = {
                                    viewModel.setSavedOfflineFilter(true)
                                    viewModel.setSelectedTab(FeedTab.FEED)
                                },
                                onOpenCreatePost = { viewModel.openCreatePostDialog() },
                                onNavigateToNotifications = { viewModel.setSelectedTab(FeedTab.NOTIFICATIONS) },
                                onNavigateToPhotos = { viewModel.setSelectedTab(FeedTab.PHOTOS) },
                                onNavigateToNasConfig = { viewModel.setSelectedTab(FeedTab.NAS_SETTINGS) },
                                onNavigateToMembers = { viewModel.setSelectedTab(FeedTab.MEMBERS) },
                                onNavigateToWatch = { viewModel.setSelectedTab(FeedTab.WATCH) },
                                onNavigateToShorts = { viewModel.setSelectedTab(FeedTab.SHORTS) },
                                onNavigateToGroups = { viewModel.setSelectedTab(FeedTab.GROUPS) },
                                onNavigateToErrorLogs = { viewModel.openErrorLogsDialog() },
                                onOpenScanFolders = { viewModel.openScanFoldersDialog() },
                                onRunDiagnostics = { viewModel.runDatabaseDiagnostics() },
                                onRepairDatabase = { viewModel.repairNasDatabase() },
                                errorCount = errorLogs.size,
                                onOpenProfileSettings = { viewModel.openProfileDialog() },
                                onLogout = { viewModel.logout() }
                            )
                        }
                        else -> Unit
                    }
                }
            } else {
                // Pantallas secundarias abiertas desde el Menú (Miembros, Fotos, Ajustes NAS)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = innerPadding.calculateTopPadding())
                        .navigationBarsPadding()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { viewModel.setSelectedTab(FeedTab.MENU) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("secondary_back_to_menu")
                        ) {
                            IconButton(onClick = { viewModel.setSelectedTab(FeedTab.MENU) }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Volver al Menú"
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = selectedTab.label,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (selectedTab) {
                            FeedTab.MEMBERS -> {
                                FamilyMembersScreen(
                                    currentMember = currentMember,
                                    familyMembers = familyMembers,
                                    onSelectMember = { member ->
                                        viewModel.setMemberFilter(member)
                                        viewModel.setSelectedTab(FeedTab.FEED)
                                    },
                                    onViewMemberPosts = { member ->
                                        viewModel.setMemberFilter(member)
                                        viewModel.setSelectedTab(FeedTab.FEED)
                                    }
                                )
                            }
                            FeedTab.PHOTOS -> {
                                PhotosScreen(posts = posts)
                            }
                            FeedTab.NAS_SETTINGS -> {
                                NasConfigScreen(
                                    nasConfig = nasConfig,
                                    onSaveConfig = { updatedConfig -> viewModel.updateNasSettings(updatedConfig) },
                                    onTestConnection = { viewModel.testNasConnection() },
                                    onRepairDatabase = { viewModel.repairNasDatabase() },
                                    onOpenErrorLogs = { viewModel.openErrorLogsDialog() },
                                    onRunDiagnostics = { viewModel.runDatabaseDiagnostics() },
                                    errorCount = errorLogs.size,
                                    isRepairing = isRepairingDb,
                                    isRunningDiagnostics = isRunningDiagnostics
                                )
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }

        // Barra de Navegación Inferior flotante (se oculta al hacer scroll abajo o en Shorts)
        AnimatedVisibility(
            visible = showBottomBar,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            FacebookBottomNavBar(
                currentMember = currentMember,
                selectedTab = selectedTab,
                unreadNotificationsCount = unreadNotificationsCount,
                onTabSelected = { tab ->
                    isBottomBarVisible = true
                    viewModel.setSelectedTab(tab)
                }
            )
        }
    }

    // Modal Crear Publicación Real
    if (isCreatePostDialogOpen) {
        CreatePostDialog(
            currentMember = currentMember,
            initiallyShortMode = isCreateShortMode,
            onDismiss = { viewModel.closeCreatePostDialog() },
            onSubmitPost = { text, mediaUri, mediaUris, feeling, loc, thumbFile, durLabel, isShort ->
                viewModel.createPost(
                    text = text,
                    mediaUri = mediaUri,
                    mediaUris = mediaUris,
                    feeling = feeling,
                    location = loc,
                    thumbnailFile = thumbFile,
                    durationLabel = durLabel,
                    isShort = isShort
                )
            }
        )
    }

    // Modal Crear Historia Independiente Real
    if (isCreateStoryDialogOpen) {
        CreateStoryDialog(
            currentMember = currentMember,
            onDismiss = { viewModel.closeCreateStoryDialog() },
            onSubmitStory = { caption, storyType, bgColorHex, mediaUri ->
                viewModel.createStory(caption, storyType, bgColorHex, mediaUri)
            }
        )
    }

    // Modal Visor de Historias a pantalla completa
    selectedStoryGroupForViewing?.let { group ->
        StoryViewerDialog(
            group = group,
            onDismiss = { viewModel.closeStoryViewer() }
        )
    }

    // Modal Reproductor de Vídeo optimizado con reproducción continua de fragmentos
    activeVideoToPlay?.let { info ->
        VideoPlayerDialog(
            videoUrl = info.url,
            title = info.title,
            authorName = info.author,
            durationLabel = info.duration,
            chunks = info.chunks,
            chunkDurationsMs = info.chunkDurationsMs,
            totalDurationMsHint = info.totalDurationMs,
            onDismiss = { activeVideoToPlay = null }
        )
    }

    // Modal / Bottom Sheet de Comentarios Reactivo
    selectedPostForComments?.let { post ->
        CommentsSheet(
            post = post,
            currentMember = currentMember,
            onDismiss = { viewModel.closeCommentsSheet() },
            onSendComment = { text -> viewModel.addCommentToPost(post.id, text) },
            onRefreshComments = { viewModel.reloadComments(post.id) }
        )
    }

    // Modal Ajustes de Perfil (Nick, Avatar, Clave)
    if (isProfileDialogOpen) {
        ProfileSettingsDialog(
            currentMember = currentMember,
            onDismiss = { viewModel.closeProfileDialog() },
            onSaveProfile = { nick, emoji, colorHex, newPass ->
                viewModel.updateProfile(
                    displayName = nick,
                    avatarEmoji = emoji,
                    avatarColorHex = colorHex,
                    newPassword = newPass
                )
            }
        )
    }

    // Modal de Confirmación de IP del Synology NAS (si no se autodetecta en puerto 80)
    if (showNasIpConfirmation) {
        NasIpConfirmationDialog(
            initialIp = nasConfig.ip,
            initialPort = nasConfig.port,
            initialPath = nasConfig.path,
            isScanning = isAutoDetectingNas,
            onConfirm = { ip, port, path -> viewModel.confirmNasIp(ip, port, path) },
            onRetryAutoDetect = { viewModel.retryAutoDetectNas() },
            onDismiss = { viewModel.dismissNasIpConfirmation() }
        )
    }

    // Modal de Escaneo y Sincronización de Carpetas del NAS para el Administrador
    if (isScanFoldersDialogOpen) {
        AdminFolderScanDialog(
            scannedFolders = scannedFolders,
            isScanning = isScanningFolders,
            isImporting = isImportingFolders,
            importProgress = importProgress,
            detectedVolumes = detectedVolumes,
            onDetectVolumes = { viewModel.detectVolumes(autoScan = false) },
            onScanPath = { path -> viewModel.scanFolders(path) },
            onToggleFolder = { path -> viewModel.toggleFolderSelection(path) },
            onSelectAll = { select -> viewModel.selectAllFolders(select) },
            onImportSelected = { viewModel.importSelectedFolders() },
            onDismiss = { viewModel.closeScanFoldersDialog() }
        )
    }

    // Modal de Progreso Dual Secuencial (Fragmentación y Subida al NAS)
    videoUploadProgress?.let { prog ->
        com.example.ui.components.VideoUploadProgressDialog(progress = prog)
    }

    // Modal de Registro de Errores y Diagnóstico (PHP 7.3 y MariaDB 10)
    if (isErrorLogsDialogOpen) {
        ErrorLogsDialog(
            logs = errorLogs,
            nasConfig = nasConfig,
            isRepairing = isRepairingDb,
            onDismiss = { viewModel.closeErrorLogsDialog() },
            onClearLogs = { viewModel.clearErrorLogs() },
            onRepairDatabase = { viewModel.repairNasDatabase() },
            onRunDiagnostics = { viewModel.runDatabaseDiagnostics() },
            onExportLogs = { viewModel.exportErrorLogs() }
        )
    }

    // Modal de Diagnóstico Exhaustivo en Vivo de MariaDB 10
    diagnosticReport?.let { report ->
        com.example.ui.components.DatabaseDiagnosticDialog(
            report = report,
            isRunning = isRunningDiagnostics,
            onRerunDiagnostics = { viewModel.runDatabaseDiagnostics() },
            onDismiss = { viewModel.dismissDiagnosticReport() }
        )
    }
}
