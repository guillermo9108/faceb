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
import com.example.ui.components.CommentsSheet
import com.example.ui.components.CreatePostDialog
import com.example.ui.components.CreateStoryDialog
import com.example.ui.components.HeaderBar
import com.example.ui.components.MemberSwitchDialog
import com.example.ui.components.StoryViewerDialog
import com.example.ui.components.VideoPlayerDialog
import com.example.ui.screens.FamilyMembersScreen
import com.example.ui.screens.FeedScreen
import com.example.ui.screens.MenuScreen
import com.example.ui.screens.NasConfigScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.PhotosScreen
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
    val isCreateStoryDialogOpen by viewModel.isCreateStoryDialogOpen.collectAsState()
    val selectedStoryGroupForViewing by viewModel.selectedStoryGroupForViewing.collectAsState()
    val isMemberSwitchDialogOpen by viewModel.isMemberSwitchDialogOpen.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var activeVideoToPlay by remember { mutableStateOf<Triple<String, String, String?>?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    val familyMembers by viewModel.familyMembers.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Mostrar snackbar cuando haya un mensaje de estado
    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatus()
        }
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
                onOpenMemberSwitch = { viewModel.openMemberSwitchDialog() }
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
                        onPlayVideo = { url, title, author, dur ->
                            activeVideoToPlay = Triple(url, title, dur)
                        },
                        onOpenNasConfig = { viewModel.setSelectedTab(FeedTab.NAS_SETTINGS) }
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
                        onPlayVideo = { url, title, author, dur ->
                            activeVideoToPlay = Triple(url, title, dur)
                        }
                    )
                }
                FeedTab.MEMBERS -> {
                    FamilyMembersScreen(
                        currentMember = currentMember,
                        familyMembers = familyMembers,
                        onSelectMember = { viewModel.switchMember(it) },
                        onViewMemberPosts = { member ->
                            viewModel.setMemberFilter(member)
                            viewModel.setSelectedTab(FeedTab.FEED)
                        }
                    )
                }
                FeedTab.NOTIFICATIONS -> {
                    NotificationsScreen(
                        currentMember = currentMember,
                        familyMembers = familyMembers,
                        onNotificationClick = { viewModel.setSelectedTab(FeedTab.FEED) }
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
                        onOpenMemberSwitch = { viewModel.openMemberSwitchDialog() }
                    )
                }
                FeedTab.PHOTOS -> {
                    PhotosScreen(posts = posts)
                }
                FeedTab.NAS_SETTINGS -> {
                    NasConfigScreen(
                        nasConfig = nasConfig,
                        onSaveConfig = { updatedConfig -> viewModel.updateNasSettings(updatedConfig) },
                        onTestConnection = { viewModel.testNasConnection() }
                    )
                }
            }
        }
    }

    // Modal Crear Publicación Real
    if (isCreatePostDialogOpen) {
        CreatePostDialog(
            currentMember = currentMember,
            onDismiss = { viewModel.closeCreatePostDialog() },
            onSubmitPost = { text, mediaUri, feeling, loc, thumbFile, durLabel ->
                viewModel.createPost(text, mediaUri, feeling, loc, thumbFile, durLabel)
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

    // Modal Reproductor de Vídeo optimizado
    activeVideoToPlay?.let { (videoUrl, title, duration) ->
        VideoPlayerDialog(
            videoUrl = videoUrl,
            title = title,
            durationLabel = duration,
            onDismiss = { activeVideoToPlay = null }
        )
    }

    // Modal Cambiar de Familiar
    if (isMemberSwitchDialogOpen) {
        MemberSwitchDialog(
            currentMember = currentMember,
            familyMembers = familyMembers,
            onSelectMember = { viewModel.switchMember(it) },
            onDismiss = { viewModel.closeMemberSwitchDialog() }
        )
    }

    // Modal / Bottom Sheet de Comentarios Reactivo
    selectedPostForComments?.let { post ->
        CommentsSheet(
            post = post,
            currentMember = currentMember,
            onDismiss = { viewModel.closeCommentsSheet() },
            onSendComment = { text -> viewModel.addCommentToPost(post.id, text) }
        )
    }
}
