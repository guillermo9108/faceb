package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.ReactionType
import com.example.model.NotificationType
import com.example.ui.components.AdminFolderScanDialog
import com.example.ui.components.CommentsSheet
import com.example.ui.components.CreatePostDialog
import com.example.ui.components.CreateStoryDialog
import com.example.ui.components.ErrorLogsDialog
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
        val chunks: List<String> = emptyList()
    )

    var activeVideoToPlay by remember { mutableStateOf<ActiveVideoPlayInfo?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    val familyMembers by viewModel.familyMembers.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
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

    // Manejo de retroceso para volver al feed principal
    BackHandler(enabled = selectedTab != FeedTab.FEED) {
        viewModel.setSelectedTab(FeedTab.FEED)
    }

    Scaffold(
        topBar = {
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
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
                        onPlayVideo = { url, title, author, dur, chunks ->
                            activeVideoToPlay = ActiveVideoPlayInfo(url, title, author, dur, chunks)
                        },
                        onOpenNasConfig = { viewModel.setSelectedTab(FeedTab.NAS_SETTINGS) },
                        onOpenErrorLogs = { viewModel.openErrorLogsDialog() }
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
                        onPlayVideo = { url, title, author, dur, chunks ->
                            activeVideoToPlay = ActiveVideoPlayInfo(url, title, author, dur, chunks)
                        }
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
                        onShortViewed = { viewModel.markShortAsViewed(it) }
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
                        onPlayVideo = { url, title, author, dur, chunks ->
                            activeVideoToPlay = ActiveVideoPlayInfo(url, title, author.name, dur, chunks)
                        },
                        onTogglePin = { postId -> viewModel.togglePinPost(postId) },
                        onDeletePost = { postId -> viewModel.deletePost(postId) }
                    )
                }
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
                        onNavigateToPhotos = { viewModel.setSelectedTab(FeedTab.PHOTOS) },
                        onNavigateToNasConfig = { viewModel.setSelectedTab(FeedTab.NAS_SETTINGS) },
                        onNavigateToMembers = { viewModel.setSelectedTab(FeedTab.MEMBERS) },
                        onNavigateToWatch = { viewModel.setSelectedTab(FeedTab.WATCH) },
                        onNavigateToShorts = { viewModel.setSelectedTab(FeedTab.SHORTS) },
                        onNavigateToGroups = { viewModel.setSelectedTab(FeedTab.GROUPS) },
                        onNavigateToErrorLogs = { viewModel.openErrorLogsDialog() },
                        errorCount = errorLogs.size,
                        onOpenProfileSettings = { viewModel.openProfileDialog() },
                        onLogout = { viewModel.logout() }
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
            }
        }
    }

    // Modal Crear Publicación Real
    if (isCreatePostDialogOpen) {
        CreatePostDialog(
            currentMember = currentMember,
            initiallyShortMode = isCreateShortMode,
            onDismiss = { viewModel.closeCreatePostDialog() },
            onSubmitPost = { text, mediaUri, feeling, loc, thumbFile, durLabel, isShort ->
                viewModel.createPost(text, mediaUri, feeling, loc, thumbFile, durLabel, isShort)
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
