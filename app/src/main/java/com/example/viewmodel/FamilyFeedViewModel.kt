package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FamilyRepository
import com.example.data.OfflineToggleAction
import com.example.model.DatabaseDiagnosticReport
import com.example.model.FamilyGroup
import com.example.model.FamilyMember
import com.example.model.FamilyStory
import com.example.model.GroupOrganizationMode
import com.example.model.NasConfig
import com.example.model.Post
import com.example.model.ReactionType
import com.example.model.ScannedFolder
import com.example.model.UploadStage
import com.example.model.VideoUploadProgress
import com.example.util.FeedStreamEntry
import com.example.util.FeedStreamHelper
import com.example.util.QueuedPostDownload
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class FeedTab(val label: String) {
    FEED("Inicio"),
    SHORTS("Shorts"),
    GROUPS("Grupos"),
    WATCH("Watch"),
    MEMBERS("Familia"),
    NOTIFICATIONS("Notificaciones"),
    MENU("Menú"),
    PHOTOS("Fotos"),
    NAS_SETTINGS("Synology NAS")
}

class FamilyFeedViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FamilyRepository(application.applicationContext)

    val familyMembers = repository.familyMembers
    val currentMember = repository.currentMember
    val isUserLoggedIn = repository.isUserLoggedIn
    val nasConfig = repository.nasConfig
    val stories = repository.stories
    val shorts = repository.shorts
    val notifications = repository.notifications
    val unreadNotificationsCount = repository.unreadNotificationsCount
    val isLoading = repository.isLoading
    val hasMoreFeedPosts = repository.hasMoreFeedPosts
    val isLoadingMoreFeed = repository.isLoadingMoreFeed
    val followedUserIds = repository.followedUserIds
    val followedGroupIds = repository.followedGroupIds
    val downloadStates: StateFlow<Map<Int, QueuedPostDownload>> = repository.downloadStates

    // Snapshot de vistos y semilla de sesión: se actualizan al abrir o refrescar para no mover el scroll en vivo
    private val _seenCommentsSnapshot = MutableStateFlow(repository.getSeenCommentsSnapshot())
    private val _sessionSeed = MutableStateFlow(System.currentTimeMillis())

    private val _selectedTab = MutableStateFlow(FeedTab.FEED)
    val selectedTab: StateFlow<FeedTab> = _selectedTab.asStateFlow()

    private val _targetShortId = MutableStateFlow<Int?>(null)
    val targetShortId: StateFlow<Int?> = _targetShortId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _globalSearchResults = MutableStateFlow<List<Post>>(emptyList())
    private val _isSearchingGlobal = MutableStateFlow(false)
    val isSearchingGlobal: StateFlow<Boolean> = _isSearchingGlobal.asStateFlow()
    private var globalSearchJob: Job? = null

    private val _selectedMemberFilter = MutableStateFlow<FamilyMember?>(null)
    val selectedMemberFilter: StateFlow<FamilyMember?> = _selectedMemberFilter.asStateFlow()

    private val _savedOfflineOnlyFilter = MutableStateFlow(false)
    val savedOfflineOnlyFilter: StateFlow<Boolean> = _savedOfflineOnlyFilter.asStateFlow()

    val savedOfflineCount: StateFlow<Int> = repository.posts
        .map { list -> list.count { it.isSavedOffline } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _selectedPostForComments = MutableStateFlow<Post?>(null)
    val selectedPostForComments: StateFlow<Post?> = _selectedPostForComments.asStateFlow()

    // Comentarios reactivos: se actualizan en vivo al recibir datos de MariaDB (Feed, Grupos o Búsqueda)
    val activePostForComments: StateFlow<Post?> = combine(
        _selectedPostForComments,
        repository.posts,
        repository.groupPosts,
        _globalSearchResults,
        repository.shorts
    ) { selected, posts, groupPosts, searchPosts, shortsList ->
        if (selected == null) null
        else posts.find { it.id == selected.id }
            ?: groupPosts.find { it.id == selected.id }
            ?: searchPosts.find { it.id == selected.id }
            ?: shortsList.find { it.id == selected.id }
            ?: selected
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isCreatePostDialogOpen = MutableStateFlow(false)
    val isCreatePostDialogOpen: StateFlow<Boolean> = _isCreatePostDialogOpen.asStateFlow()

    private val _isCreateShortMode = MutableStateFlow(false)
    val isCreateShortMode: StateFlow<Boolean> = _isCreateShortMode.asStateFlow()

    private val _targetPostGroup = MutableStateFlow<FamilyGroup?>(null)
    val targetPostGroup: StateFlow<FamilyGroup?> = _targetPostGroup.asStateFlow()

    private val _isCreateStoryDialogOpen = MutableStateFlow(false)
    val isCreateStoryDialogOpen: StateFlow<Boolean> = _isCreateStoryDialogOpen.asStateFlow()

    private val _selectedStoryGroupForViewing = MutableStateFlow<com.example.model.UserStoryGroup?>(null)
    val selectedStoryGroupForViewing: StateFlow<com.example.model.UserStoryGroup?> = _selectedStoryGroupForViewing.asStateFlow()

    val groups = repository.groups

    private val _selectedGroupForViewing = MutableStateFlow<FamilyGroup?>(null)
    val selectedGroupForViewing: StateFlow<FamilyGroup?> = _selectedGroupForViewing.asStateFlow()

    private val _isRepairingDb = MutableStateFlow(false)
    val isRepairingDb: StateFlow<Boolean> = _isRepairingDb.asStateFlow()

    val hasMoreGroupPosts = repository.hasMoreGroupPosts
    val isLoadingGroupPosts = repository.isLoadingGroupPosts
    val isLoadingMoreGroupPosts = repository.isLoadingMoreGroupPosts

    private val _groupSearchQuery = MutableStateFlow("")
    val groupSearchQuery: StateFlow<String> = _groupSearchQuery.asStateFlow()

    private val _groupSearchResults = MutableStateFlow<List<Post>>(emptyList())
    private val _isSearchingGroup = MutableStateFlow(false)
    val isSearchingGroup: StateFlow<Boolean> = _isSearchingGroup.asStateFlow()
    private var groupSearchJob: Job? = null

    val groupPosts: StateFlow<List<Post>> = combine(
        repository.groupPosts,
        _groupSearchQuery,
        _groupSearchResults
    ) { pagedPosts, query, searchResults ->
        if (query.isNotBlank()) searchResults else pagedPosts
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isScanFoldersDialogOpen = MutableStateFlow(false)
    val isScanFoldersDialogOpen: StateFlow<Boolean> = _isScanFoldersDialogOpen.asStateFlow()

    private val _scannedFolders = MutableStateFlow<List<ScannedFolder>>(emptyList())
    val scannedFolders: StateFlow<List<ScannedFolder>> = _scannedFolders.asStateFlow()

    private val _isScanningFolders = MutableStateFlow(false)
    val isScanningFolders: StateFlow<Boolean> = _isScanningFolders.asStateFlow()

    private val _isImportingFolders = MutableStateFlow(false)
    val isImportingFolders: StateFlow<Boolean> = _isImportingFolders.asStateFlow()

    private val _importProgress = MutableStateFlow<com.example.model.NasImportProgress?>(null)
    val importProgress: StateFlow<com.example.model.NasImportProgress?> = _importProgress.asStateFlow()

    val errorLogs = repository.errorLogs

    private val _isErrorLogsDialogOpen = MutableStateFlow(false)
    val isErrorLogsDialogOpen: StateFlow<Boolean> = _isErrorLogsDialogOpen.asStateFlow()

    private val _diagnosticReport = MutableStateFlow<DatabaseDiagnosticReport?>(null)
    val diagnosticReport: StateFlow<DatabaseDiagnosticReport?> = _diagnosticReport.asStateFlow()

    private val _isRunningDiagnostics = MutableStateFlow(false)
    val isRunningDiagnostics: StateFlow<Boolean> = _isRunningDiagnostics.asStateFlow()

    private val _isProfileDialogOpen = MutableStateFlow(false)
    val isProfileDialogOpen: StateFlow<Boolean> = _isProfileDialogOpen.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _uploadProgress = MutableStateFlow<String?>(null)
    val uploadProgress: StateFlow<String?> = _uploadProgress.asStateFlow()

    private val _uploadProgressFraction = MutableStateFlow(0f)
    val uploadProgressFraction: StateFlow<Float> = _uploadProgressFraction.asStateFlow()

    private val _videoUploadProgress = MutableStateFlow<VideoUploadProgress?>(null)
    val videoUploadProgress: StateFlow<VideoUploadProgress?> = _videoUploadProgress.asStateFlow()

    val isNasDetected = repository.isNasDetected
    val isAutoDetectingNas = repository.isAutoDetecting
    val showNasIpConfirmation = repository.showNasIpConfirmation

    init {
        // Intento de conexión inicial con autodetección en puerto 80 y refresco del feed real, shorts y grupos
        viewModelScope.launch {
            val autoDetectResult = repository.autoDetectNasOnPort80()
            if (autoDetectResult.isSuccess) {
                showStatus("¡Synology NAS detectado en ${autoDetectResult.getOrNull()} (Puerto 80)!")
            }
            repository.refreshFeed()
            repository.refreshShorts()
            repository.refreshGroups()
        }
    }

    fun refreshGroups() {
        viewModelScope.launch {
            repository.refreshGroups()
        }
    }

    fun openGroup(group: FamilyGroup) {
        groupSearchJob?.cancel()
        _groupSearchQuery.value = ""
        _groupSearchResults.value = emptyList()
        _isSearchingGroup.value = false
        _selectedGroupForViewing.value = group

        viewModelScope.launch {
            repository.loadGroupPostsInitial(group.id)
        }
    }

    fun loadMoreGroupPosts() {
        val group = _selectedGroupForViewing.value ?: return
        if (_groupSearchQuery.value.isNotBlank()) return
        viewModelScope.launch {
            repository.loadMoreGroupPosts(group.id)
        }
    }

    fun setGroupSearchQuery(query: String) {
        _groupSearchQuery.value = query
        groupSearchJob?.cancel()
        val group = _selectedGroupForViewing.value
        val trimmed = query.trim()
        if (trimmed.isBlank() || group == null) {
            _groupSearchResults.value = emptyList()
            _isSearchingGroup.value = false
            return
        }
        groupSearchJob = viewModelScope.launch {
            _isSearchingGroup.value = true
            delay(280)
            val res = repository.searchPosts(trimmed, group.id)
            _isSearchingGroup.value = false
            res.onSuccess { list ->
                if (_groupSearchQuery.value.trim() == trimmed && _selectedGroupForViewing.value?.id == group.id) {
                    _groupSearchResults.value = list
                }
            }
        }
    }

    fun closeGroup() {
        groupSearchJob?.cancel()
        _groupSearchQuery.value = ""
        _groupSearchResults.value = emptyList()
        _isSearchingGroup.value = false
        _selectedGroupForViewing.value = null
        repository.clearActiveGroup()
    }

    fun createGroup(
        name: String,
        description: String,
        allowUploads: Boolean,
        organizationMode: GroupOrganizationMode,
        coverUri: Uri? = null
    ) {
        viewModelScope.launch {
            showStatus("Creando grupo \"$name\" en el NAS...")
            val res = repository.createGroup(
                name = name,
                description = description,
                allowUploads = allowUploads,
                organizationMode = organizationMode,
                coverUri = coverUri
            )
            res.fold(
                onSuccess = { created ->
                    showStatus("¡Grupo \"${created.name}\" creado!")
                    openGroup(created)
                },
                onFailure = { err ->
                    showStatus("No se pudo crear el grupo: ${err.localizedMessage}")
                }
            )
        }
    }

    fun updateGroup(
        groupId: Int,
        name: String,
        description: String,
        allowUploads: Boolean,
        organizationMode: GroupOrganizationMode,
        coverUri: Uri? = null,
        coverPhotoUrl: String? = null
    ) {
        viewModelScope.launch {
            showStatus("Guardando ajustes del grupo...")
            val res = repository.updateGroup(
                groupId = groupId,
                name = name,
                description = description,
                allowUploads = allowUploads,
                organizationMode = organizationMode,
                coverUri = coverUri,
                coverPhotoUrl = coverPhotoUrl
            )
            res.fold(
                onSuccess = { updated ->
                    if (_selectedGroupForViewing.value?.id == updated.id) {
                        _selectedGroupForViewing.value = updated
                    }
                    showStatus("Grupo \"${updated.name}\" actualizado")
                },
                onFailure = { err ->
                    showStatus("Error al actualizar el grupo: ${err.localizedMessage}")
                }
            )
        }
    }

    fun deleteGroup(groupId: Int) {
        viewModelScope.launch {
            val res = repository.deleteGroup(groupId)
            res.fold(
                onSuccess = {
                    if (_selectedGroupForViewing.value?.id == groupId) {
                        closeGroup()
                    }
                    showStatus("Grupo eliminado")
                },
                onFailure = { err ->
                    showStatus("No se pudo eliminar el grupo: ${err.localizedMessage}")
                }
            )
        }
    }

    fun toggleFollowUser(targetUserId: Int) {
        viewModelScope.launch {
            repository.toggleFollowUser(targetUserId)
        }
    }

    fun toggleFollowGroup(targetGroupId: Int) {
        viewModelScope.launch {
            repository.toggleFollowGroup(targetGroupId)
        }
    }

    fun openShortInShortsTab(postId: Int) {
        _targetShortId.value = postId
        _selectedTab.value = FeedTab.SHORTS
    }

    fun onVideoPlayed(videoUrl: String, fallbackChunkUrl: String? = null, postId: Int? = null) {
        viewModelScope.launch {
            repository.onVideoPlayedForThumbnail(videoUrl, fallbackChunkUrl, postId)
        }
    }

    val detectedVolumes = repository.detectedVolumes

    fun detectVolumes(autoScan: Boolean = true) {
        viewModelScope.launch {
            val res = repository.detectNasVolumes()
            if (autoScan) {
                res.onSuccess { volumes ->
                    val preferred = volumes.find { it.volumeName == "volume3" }
                        ?: volumes.firstOrNull { it.mediaFolders.isNotEmpty() }
                        ?: volumes.firstOrNull()
                    val suggested = preferred?.mediaFolders?.firstOrNull()
                        ?: preferred?.path
                        ?: "/volume3/photo"
                    scanFolders(suggested)
                }.onFailure {
                    scanFolders("/volume3/photo")
                }
            }
        }
    }

    fun openScanFoldersDialog() {
        _isScanFoldersDialogOpen.value = true
        detectVolumes(autoScan = true)
    }

    fun closeScanFoldersDialog() {
        _isScanFoldersDialogOpen.value = false
        _scannedFolders.value = emptyList()
    }

    fun scanFolders(basePath: String) {
        viewModelScope.launch {
            _isScanningFolders.value = true
            val res = repository.scanNasFolders(basePath)
            _isScanningFolders.value = false
            res.fold(
                onSuccess = { list ->
                    _scannedFolders.value = list
                    if (list.isEmpty()) {
                        showStatus("No se encontraron subcarpetas en $basePath")
                    } else {
                        showStatus("Se encontraron ${list.size} carpetas")
                    }
                },
                onFailure = { err ->
                    showStatus("Error al escanear: ${err.message}")
                }
            )
        }
    }

    fun toggleFolderSelection(folderPath: String) {
        _scannedFolders.update { list ->
            list.map {
                if (it.folderPath == folderPath) it.copy(isSelected = !it.isSelected)
                else it
            }
        }
    }

    fun selectAllFolders(select: Boolean) {
        _scannedFolders.update { list ->
            list.map { it.copy(isSelected = select) }
        }
    }

    fun importSelectedFolders() {
        val selected = _scannedFolders.value.filter { it.isSelected }.map { it.folderPath }
        if (selected.isEmpty()) {
            showStatus("Selecciona al menos una carpeta para importar")
            return
        }

        viewModelScope.launch {
            _isImportingFolders.value = true
            _importProgress.value = com.example.model.NasImportProgress(
                processedFiles = 0,
                totalFiles = 0,
                statusMessage = "Iniciando escaneo y publicación de subcarpetas en segundo plano..."
            )
            val res = repository.importNasFolders(selected) { processed, total ->
                _importProgress.value = com.example.model.NasImportProgress(
                    processedFiles = processed,
                    totalFiles = total,
                    statusMessage = if (total > 0) "Publicando en segundo plano: $processed de $total archivos..." else "Publicando archivos: $processed..."
                )
            }
            _isImportingFolders.value = false
            _importProgress.value = null
            res.fold(
                onSuccess = { count ->
                    showStatus("¡Se importaron $count grupos familiares con todo su contenido y subcarpetas!")
                    closeScanFoldersDialog()
                    refreshGroups()
                    refreshFeed()
                },
                onFailure = { err ->
                    showStatus("Error al importar grupos: ${err.message}")
                }
            )
        }
    }

    fun retryAutoDetectNas() {
        viewModelScope.launch {
            showStatus("Buscando Synology NAS en la red LAN (puerto 80)...")
            val res = repository.autoDetectNasOnPort80()
            res.fold(
                onSuccess = { ip -> showStatus("¡Synology NAS detectado en $ip!") },
                onFailure = { showStatus("No se detectó el NAS automáticamente. Por favor confirma la IP.") }
            )
        }
    }

    fun confirmNasIp(ip: String, port: Int = 80, path: String = "/family_api") {
        viewModelScope.launch {
            showStatus("Conectando con $ip:$port...")
            val result = repository.testAndConfirmNasConfig(ip, port, path)
            result.fold(
                onSuccess = {
                    showStatus("¡Conexión exitosa con Synology NAS!")
                },
                onFailure = { error ->
                    showStatus("Error de conexión: ${error.localizedMessage}")
                }
            )
        }
    }

    fun dismissNasIpConfirmation() {
        repository.setNasIpConfirmationVisible(false)
    }

    // Historias agrupadas por usuario para mostrar en StoriesRow
    val groupedStories: StateFlow<List<com.example.model.UserStoryGroup>> = repository.stories.map { list ->
        list.groupBy { it.author.id }.map { (_, userStories) ->
            com.example.model.UserStoryGroup(
                author = userStories.first().author,
                stories = userStories.sortedByDescending { it.timestamp },
                hasUnseen = userStories.any { !it.isViewed }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Feed filtrado dinámico:
    // - Sin búsqueda: solo usuario actual, usuarios seguidos y grupos seguidos (orden intacto).
    // - Con búsqueda: resultados ligeros de TODA la base de datos.
    val filteredPosts: StateFlow<List<Post>> = combine(
        repository.posts,
        _globalSearchResults,
        _searchQuery,
        _selectedMemberFilter,
        _savedOfflineOnlyFilter,
        repository.currentMember,
        repository.followedUserIds,
        repository.followedGroupIds
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val posts = values[0] as List<Post>
        @Suppress("UNCHECKED_CAST")
        val dbSearchResults = values[1] as List<Post>
        val query = values[2] as String
        val memberFilter = values[3] as FamilyMember?
        val offlineOnly = values[4] as Boolean
        val curMember = values[5] as FamilyMember
        @Suppress("UNCHECKED_CAST")
        val followedUsers = values[6] as Set<Int>
        @Suppress("UNCHECKED_CAST")
        val followedGroups = values[7] as Set<Int>

        val source = if (query.isNotBlank()) {
            val localMatches = posts.filter { post ->
                post.contentText.contains(query, ignoreCase = true) ||
                    post.author.name.contains(query, ignoreCase = true) ||
                    (post.groupName?.contains(query, ignoreCase = true) == true) ||
                    (post.feeling?.contains(query, ignoreCase = true) == true) ||
                    (post.location?.contains(query, ignoreCase = true) == true)
            }
            if (dbSearchResults.isNotEmpty()) {
                (dbSearchResults + localMatches).distinctBy { it.id }
            } else {
                localMatches
            }
        } else {
            FeedStreamHelper.filterFeedByFollows(
                posts = posts,
                currentUserId = curMember.id,
                followedUserIds = followedUsers,
                followedGroupIds = followedGroups
            )
        }

        source.filter { post ->
            val matchesMember = memberFilter == null || post.author.id == memberFilter.id
            val matchesOffline = !offlineOnly || post.isSavedOffline
            matchesMember && matchesOffline
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Flujo inteligente del muro: no vistos primero + anti-monotonía + ráfagas + bloques ricos + rotación al refrescar
    val feedEntries: StateFlow<List<FeedStreamEntry>> = combine(
        filteredPosts,
        repository.shorts,
        repository.groups,
        repository.followedGroupIds,
        _seenCommentsSnapshot,
        _sessionSeed,
        _searchQuery,
        _selectedMemberFilter,
        _savedOfflineOnlyFilter
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val posts = values[0] as List<Post>
        @Suppress("UNCHECKED_CAST")
        val shortsList = values[1] as List<Post>
        @Suppress("UNCHECKED_CAST")
        val allGroups = values[2] as List<FamilyGroup>
        @Suppress("UNCHECKED_CAST")
        val followedGroups = values[3] as Set<Int>
        @Suppress("UNCHECKED_CAST")
        val seenSnap = values[4] as Map<Int, Int>
        val seed = values[5] as Long
        val query = values[6] as String
        val memberFilter = values[7] as FamilyMember?
        val offlineOnly = values[8] as Boolean

        if (query.isNotBlank() || memberFilter != null || offlineOnly) {
            posts.map { FeedStreamEntry.PostEntry(it) }
        } else {
            FeedStreamHelper.buildSmartFeedEntries(
                posts = posts,
                shorts = shortsList,
                groups = allGroups,
                followedGroupIds = followedGroups,
                seenCommentsSnapshot = seenSnap,
                sessionSeed = seed,
                interval = 4,
                groupSize = 6
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun markPostsSeen(visiblePosts: List<Post>) {
        repository.markPostsSeen(visiblePosts)
    }

    fun setSelectedTab(tab: FeedTab) {
        if (tab != FeedTab.SHORTS) {
            _targetShortId.value = null
        }
        _selectedTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        globalSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _globalSearchResults.value = emptyList()
            _isSearchingGlobal.value = false
            return
        }
        globalSearchJob = viewModelScope.launch {
            _isSearchingGlobal.value = true
            delay(280)
            val res = repository.searchPosts(trimmed, null)
            _isSearchingGlobal.value = false
            res.onSuccess { list ->
                if (_searchQuery.value.trim() == trimmed) {
                    _globalSearchResults.value = list
                }
            }
        }
    }

    fun setMemberFilter(member: FamilyMember?) {
        _selectedMemberFilter.value = member
    }

    fun setSavedOfflineFilter(enabled: Boolean) {
        _savedOfflineOnlyFilter.value = enabled
    }

    fun openCreatePostDialog(isShortMode: Boolean = false, targetGroup: FamilyGroup? = null) {
        _isCreateShortMode.value = isShortMode
        _targetPostGroup.value = targetGroup
        _isCreatePostDialogOpen.value = true
    }

    fun closeCreatePostDialog() {
        _isCreatePostDialogOpen.value = false
        _isCreateShortMode.value = false
        _targetPostGroup.value = null
    }

    fun openCreateStoryDialog() {
        _isCreateStoryDialogOpen.value = true
    }

    fun closeCreateStoryDialog() {
        _isCreateStoryDialogOpen.value = false
    }

    fun openStoryViewer(group: com.example.model.UserStoryGroup) {
        _selectedStoryGroupForViewing.value = group
    }

    fun closeStoryViewer() {
        _selectedStoryGroupForViewing.value = null
    }

    fun markAllNotificationsAsRead() {
        repository.markAllNotificationsAsRead()
    }

    fun markNotificationAsRead(id: String) {
        repository.markNotificationAsRead(id)
    }

    fun markShortAsViewed(postId: Int) {
        repository.markShortAsViewed(postId)
    }

    fun resetViewedShorts() {
        repository.resetViewedShorts()
    }

    fun openCommentsSheet(post: Post) {
        _selectedPostForComments.value = post
        // Carga inmediata de comentarios reales desde MariaDB 10
        viewModelScope.launch {
            repository.loadCommentsForPost(post.id)
        }
    }

    fun closeCommentsSheet() {
        _selectedPostForComments.value = null
    }

    fun refreshFeed() {
        _seenCommentsSnapshot.value = repository.getSeenCommentsSnapshot()
        _sessionSeed.value = System.currentTimeMillis()
        viewModelScope.launch {
            val result = repository.refreshFeed()
            result.fold(
                onSuccess = { posts ->
                    showStatus("Muro sincronizado con Synology (${posts.size} publicaciones)")
                },
                onFailure = { error ->
                    showStatus("Sin conexión con Synology: ${error.localizedMessage}")
                }
            )
        }
    }

    fun repairNasDatabase() {
        viewModelScope.launch {
            _isRepairingDb.value = true
            showStatus("Reparando y creando tablas en MariaDB...")
            val result = repository.repairDatabase()
            _isRepairingDb.value = false
            result.fold(
                onSuccess = { msg ->
                    showStatus("¡Éxito! $msg")
                },
                onFailure = { error ->
                    showStatus("Fallo al reparar tablas: ${error.localizedMessage}")
                }
            )
        }
    }

    fun runDatabaseDiagnostics(onComplete: ((DatabaseDiagnosticReport) -> Unit)? = null) {
        viewModelScope.launch {
            _isRunningDiagnostics.value = true
            showStatus("Ejecutando diagnóstico en vivo de MariaDB 10...")
            val result = repository.runDatabaseDiagnostics()
            _isRunningDiagnostics.value = false
            result.fold(
                onSuccess = { report ->
                    _diagnosticReport.value = report
                    if (report.mariadbAuthentication.success) {
                        showStatus("¡Conexión exitosa con MariaDB 10!")
                    } else {
                        showStatus("Diagnóstico completado: Se detectaron problemas de conexión.")
                    }
                    onComplete?.invoke(report)
                },
                onFailure = { err ->
                    val fallback = DatabaseDiagnosticReport(
                        status = "ERROR",
                        recommendation = "No se pudo obtener el diagnóstico del servidor: ${err.message}. Comprueba la IP y que Web Station esté activo en el puerto 80 u 8080."
                    )
                    _diagnosticReport.value = fallback
                    showStatus("Error al contactar el endpoint de diagnóstico.")
                    onComplete?.invoke(fallback)
                }
            )
        }
    }

    fun dismissDiagnosticReport() {
        _diagnosticReport.value = null
    }

    fun createPost(
        text: String,
        mediaUri: Uri? = null,
        mediaUris: List<Uri> = emptyList(),
        feeling: String? = null,
        location: String? = null,
        thumbnailFile: File? = null,
        durationLabel: String? = null,
        isShort: Boolean = false,
        groupId: Int? = null
    ) {
        val effectiveUris = if (mediaUris.isNotEmpty()) mediaUris else listOfNotNull(mediaUri)
        if (text.isBlank() && effectiveUris.isEmpty()) return

        val effectiveGroupId = groupId ?: _targetPostGroup.value?.id

        viewModelScope.launch {
            _isCreatePostDialogOpen.value = false
            _targetPostGroup.value = null

            val primaryUri = effectiveUris.firstOrNull()
            val mimeType = if (primaryUri != null) {
                getApplication<Application>().contentResolver.getType(primaryUri) ?: ""
            } else ""
            val isVideo = mimeType.startsWith("video")

            if (isVideo && primaryUri != null) {
                _uploadProgress.value = "Iniciando preparación del vídeo..."
                _uploadProgressFraction.value = 0.02f
                showStatus(if (isShort) "Fragmentando y subiendo Short familiar al NAS..." else "Fragmentando y subiendo vídeo al NAS...")

                val result = repository.uploadChunkedVideoPost(
                    contentText = text,
                    videoUri = primaryUri,
                    feeling = feeling,
                    location = location,
                    isShort = isShort,
                    groupId = effectiveGroupId,
                    onProgress = { step, fraction ->
                        _uploadProgress.value = step
                        _uploadProgressFraction.value = fraction
                        showStatus(step)
                    },
                    onDetailedProgress = { detailed ->
                        _videoUploadProgress.value = detailed
                    }
                )

                if (result.isSuccess) {
                    kotlinx.coroutines.delay(1000)
                }
                _videoUploadProgress.value = null
                _uploadProgress.value = null
                _uploadProgressFraction.value = 0f

                result.fold(
                    onSuccess = { postId ->
                        showStatus(if (isShort) "¡Short subido en fragmentos continuos!" else "¡Vídeo publicado en fragmentos continuos (ID #$postId)!")
                    },
                    onFailure = { error ->
                        showStatus("Error al subir fragmentos al NAS: ${error.localizedMessage}")
                    }
                )
            } else {
                val photoCount = effectiveUris.size
                showStatus(
                    if (isShort) "Subiendo Short familiar al Synology NAS..."
                    else if (photoCount > 1) "Subiendo $photoCount fotos familiares al Synology NAS..."
                    else "Subiendo publicación al Synology NAS..."
                )

                val result = repository.addPost(
                    contentText = text,
                    mediaUri = primaryUri,
                    mediaUris = effectiveUris,
                    feeling = feeling,
                    location = location,
                    thumbnailFile = thumbnailFile,
                    durationLabel = durationLabel,
                    isShort = isShort,
                    groupId = effectiveGroupId
                )

                result.fold(
                    onSuccess = { postId ->
                        showStatus(
                            if (isShort) "¡Short publicado en la sección Shorts!"
                            else if (photoCount > 1) "¡$photoCount fotos publicadas en el Muro Familiar (ID #$postId)!"
                            else "Publicado en Synology NAS (ID #$postId)"
                        )
                    },
                    onFailure = { error ->
                        showStatus("Error al subir al NAS: ${error.localizedMessage}")
                    }
                )
            }
        }
    }

    fun login(
        username: String,
        pass: String,
        rememberSession: Boolean = true,
        onResult: ((Boolean, String?) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val result = repository.login(username, pass, rememberSession)
            result.fold(
                onSuccess = { member ->
                    showStatus("¡Bienvenido/a, ${member.name}!")
                    onResult?.invoke(true, null)
                },
                onFailure = { error ->
                    val msg = error.localizedMessage ?: "Error al iniciar sesión"
                    showStatus(msg)
                    onResult?.invoke(false, msg)
                }
            )
        }
    }

    fun register(
        username: String,
        displayName: String,
        pass: String,
        avatarEmoji: String = "👨‍💼",
        avatarColorHex: Long = 0xFF1877F2L,
        role: String = "Familiar",
        rememberSession: Boolean = true,
        onResult: ((Boolean, String?) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val result = repository.register(username, pass, displayName, role, avatarEmoji, avatarColorHex, rememberSession)
            result.fold(
                onSuccess = { member ->
                    showStatus("¡Cuenta familiar creada para ${member.name}!")
                    onResult?.invoke(true, null)
                },
                onFailure = { error ->
                    val msg = error.localizedMessage ?: "Error al registrar usuario"
                    showStatus(msg)
                    onResult?.invoke(false, msg)
                }
            )
        }
    }

    fun logout() {
        repository.logout()
        showStatus("Sesión cerrada")
    }

    fun skipLoginAsGuest() {
        repository.skipLoginAsGuest()
    }

    fun openProfileDialog() {
        _isProfileDialogOpen.value = true
    }

    fun closeProfileDialog() {
        _isProfileDialogOpen.value = false
    }

    fun updateProfile(
        displayName: String,
        avatarEmoji: String,
        avatarColorHex: Long,
        newPassword: String? = null,
        bio: String = "",
        onResult: ((Boolean, String?) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val result = repository.updateProfile(displayName, avatarEmoji, avatarColorHex, bio, newPassword)
            result.fold(
                onSuccess = { member ->
                    showStatus("Perfil de ${member.name} actualizado")
                    _isProfileDialogOpen.value = false
                    onResult?.invoke(true, null)
                },
                onFailure = { error ->
                    val msg = error.localizedMessage ?: "Error al actualizar perfil"
                    showStatus(msg)
                    onResult?.invoke(false, msg)
                }
            )
        }
    }

    fun refreshShorts() {
        viewModelScope.launch {
            repository.refreshShorts()
        }
    }

    fun createStory(
        caption: String,
        storyType: com.example.model.StoryType,
        bgColorHex: Long,
        mediaUri: Uri? = null
    ) {
        viewModelScope.launch {
            _isCreateStoryDialogOpen.value = false
            showStatus("Subiendo historia familiar al Synology NAS...")

            val result = repository.addStory(
                caption = caption,
                storyType = storyType,
                bgColorHex = bgColorHex,
                mediaUri = mediaUri
            )

            result.fold(
                onSuccess = {
                    showStatus("¡Historia compartida con la familia!")
                },
                onFailure = { error ->
                    showStatus("Error al subir historia: ${error.localizedMessage}")
                }
            )
        }
    }

    fun reactToPost(postId: Int, reactionType: ReactionType) {
        viewModelScope.launch {
            repository.toggleReaction(postId, reactionType)
        }
    }

    fun addCommentToPost(postId: Int, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val result = repository.addComment(postId, text)
            // Actualizar inmediatamente el post activo para que el comentario nuevo aparezca al instante
            _selectedPostForComments.value = repository.posts.value.find { it.id == postId } ?: _selectedPostForComments.value
            result.fold(
                onSuccess = {
                    showStatus("Comentario guardado en MariaDB 10")
                    _selectedPostForComments.value = repository.posts.value.find { it.id == postId } ?: _selectedPostForComments.value
                },
                onFailure = { error ->
                    showStatus("Error al comentar: ${error.localizedMessage}")
                }
            )
        }
    }

    fun reloadComments(postId: Int) {
        viewModelScope.launch {
            repository.loadCommentsForPost(postId)
            _selectedPostForComments.value = repository.posts.value.find { it.id == postId } ?: _selectedPostForComments.value
        }
    }

    fun deletePost(postId: Int) {
        viewModelScope.launch {
            val result = repository.deletePost(postId)
            result.fold(
                onSuccess = {
                    showStatus("Publicación eliminada del Synology NAS")
                    if (_selectedPostForComments.value?.id == postId) {
                        _selectedPostForComments.value = null
                    }
                },
                onFailure = { error ->
                    showStatus("Error al eliminar: ${error.localizedMessage}")
                }
            )
        }
    }

    fun togglePinPost(postId: Int) {
        repository.togglePinPost(postId)
    }

    fun loadMoreFeed() {
        viewModelScope.launch {
            repository.loadMoreFeed()
        }
    }

    fun toggleSavePostOffline(post: Post) {
        viewModelScope.launch {
            val action = repository.toggleSavePostOffline(post)
            val message = when (action) {
                OfflineToggleAction.ENQUEUED_ACTIVE -> "Descargando en segundo plano..."
                OfflineToggleAction.ENQUEUED_WAITING -> "Añadida a la cola de descarga (en espera)"
                OfflineToggleAction.CANCELLED_FROM_QUEUE -> "Descarga cancelada de la cola"
                OfflineToggleAction.REMOVED_SAVED -> "Publicación quitada de guardados offline"
            }
            showStatus(message)
        }
    }

    fun cancelOfflineDownload(postId: Int) {
        repository.cancelOfflineDownload(postId)
        showStatus("Descarga cancelada")
    }

    fun updateNasSettings(config: NasConfig) {
        repository.updateNasConfig(config)
        showStatus("Configuración de Synology guardada permanentemente")
    }

    fun updateNasSettings(ip: String, port: Int, path: String, dbPort: Int = 3307) {
        repository.updateNasConfig(ip, port, path, dbPort)
        showStatus("Configuración de Synology guardada permanentemente")
    }

    fun testNasConnection() {
        viewModelScope.launch {
            val config = repository.nasConfig.value
            val probe = repository.testAndConfirmNasConfig(config.ip, config.port, config.path)
            if (probe.isSuccess) {
                val ping = repository.pingNas()
                val detectedPath = repository.nasConfig.value.path
                val pathDesc = if (detectedPath.isBlank()) "raíz (/web)" else detectedPath
                showStatus("¡Conexión con Synology Web Station exitosa en $pathDesc (${ping} ms)!")
            } else {
                val err = repository.nasConfig.value.lastError ?: "No se pudo conectar con el NAS."
                showStatus(err)
            }
        }
    }

    fun openErrorLogsDialog() {
        _isErrorLogsDialogOpen.value = true
    }

    fun closeErrorLogsDialog() {
        _isErrorLogsDialogOpen.value = false
    }

    fun clearErrorLogs() {
        repository.clearErrorLogs()
        showStatus("Registro de errores limpiado.")
    }

    fun exportErrorLogs(): String {
        return repository.exportErrorLogs()
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    private fun showStatus(msg: String) {
        _statusMessage.value = msg
    }
}
