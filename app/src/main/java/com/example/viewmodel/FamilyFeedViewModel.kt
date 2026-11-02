package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FamilyRepository
import com.example.model.FamilyGroup
import com.example.model.FamilyMember
import com.example.model.FamilyStory
import com.example.model.NasConfig
import com.example.model.Post
import com.example.model.ReactionType
import com.example.model.ScannedFolder
import com.example.model.UploadStage
import com.example.model.VideoUploadProgress
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

    private val _selectedTab = MutableStateFlow(FeedTab.FEED)
    val selectedTab: StateFlow<FeedTab> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedMemberFilter = MutableStateFlow<FamilyMember?>(null)
    val selectedMemberFilter: StateFlow<FamilyMember?> = _selectedMemberFilter.asStateFlow()

    private val _selectedPostForComments = MutableStateFlow<Post?>(null)
    val selectedPostForComments: StateFlow<Post?> = _selectedPostForComments.asStateFlow()

    // Comentarios reactivos: se actualizan en vivo al recibir datos de MariaDB
    val activePostForComments: StateFlow<Post?> = combine(
        _selectedPostForComments,
        repository.posts
    ) { selected, posts ->
        if (selected == null) null
        else posts.find { it.id == selected.id } ?: selected
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isCreatePostDialogOpen = MutableStateFlow(false)
    val isCreatePostDialogOpen: StateFlow<Boolean> = _isCreatePostDialogOpen.asStateFlow()

    private val _isCreateShortMode = MutableStateFlow(false)
    val isCreateShortMode: StateFlow<Boolean> = _isCreateShortMode.asStateFlow()

    private val _isCreateStoryDialogOpen = MutableStateFlow(false)
    val isCreateStoryDialogOpen: StateFlow<Boolean> = _isCreateStoryDialogOpen.asStateFlow()

    private val _selectedStoryGroupForViewing = MutableStateFlow<com.example.model.UserStoryGroup?>(null)
    val selectedStoryGroupForViewing: StateFlow<com.example.model.UserStoryGroup?> = _selectedStoryGroupForViewing.asStateFlow()

    val groups = repository.groups

    private val _selectedGroupForViewing = MutableStateFlow<FamilyGroup?>(null)
    val selectedGroupForViewing: StateFlow<FamilyGroup?> = _selectedGroupForViewing.asStateFlow()

    private val _isRepairingDb = MutableStateFlow(false)
    val isRepairingDb: StateFlow<Boolean> = _isRepairingDb.asStateFlow()

    private val _groupPosts = MutableStateFlow<List<Post>>(emptyList())
    val groupPosts: StateFlow<List<Post>> = _groupPosts.asStateFlow()

    private val _isScanFoldersDialogOpen = MutableStateFlow(false)
    val isScanFoldersDialogOpen: StateFlow<Boolean> = _isScanFoldersDialogOpen.asStateFlow()

    private val _scannedFolders = MutableStateFlow<List<ScannedFolder>>(emptyList())
    val scannedFolders: StateFlow<List<ScannedFolder>> = _scannedFolders.asStateFlow()

    private val _isScanningFolders = MutableStateFlow(false)
    val isScanningFolders: StateFlow<Boolean> = _isScanningFolders.asStateFlow()

    private val _isImportingFolders = MutableStateFlow(false)
    val isImportingFolders: StateFlow<Boolean> = _isImportingFolders.asStateFlow()

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
        _selectedGroupForViewing.value = group
        viewModelScope.launch {
            val res = repository.fetchGroupPosts(group.id)
            res.onSuccess { posts ->
                _groupPosts.value = posts
            }
        }
    }

    fun closeGroup() {
        _selectedGroupForViewing.value = null
        _groupPosts.value = emptyList()
    }

    fun openScanFoldersDialog() {
        _isScanFoldersDialogOpen.value = true
        scanFolders("/volume1/photo")
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
            val res = repository.importNasFolders(selected)
            _isImportingFolders.value = false
            res.fold(
                onSuccess = { count ->
                    showStatus("¡Se importaron $count grupos familiares con sus fotos!")
                    closeScanFoldersDialog()
                    refreshGroups()
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

    // Feed filtrado dinámico según búsqueda y autor seleccionado
    val filteredPosts: StateFlow<List<Post>> = combine(
        repository.posts,
        _searchQuery,
        _selectedMemberFilter
    ) { posts, query, memberFilter ->
        posts.filter { post ->
            val matchesQuery = query.isBlank() ||
                    post.contentText.contains(query, ignoreCase = true) ||
                    post.author.name.contains(query, ignoreCase = true) ||
                    (post.feeling?.contains(query, ignoreCase = true) == true) ||
                    (post.location?.contains(query, ignoreCase = true) == true)

            val matchesMember = memberFilter == null || post.author.id == memberFilter.id

            matchesQuery && matchesMember
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSelectedTab(tab: FeedTab) {
        _selectedTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setMemberFilter(member: FamilyMember?) {
        _selectedMemberFilter.value = member
    }

    fun openCreatePostDialog(isShortMode: Boolean = false) {
        _isCreateShortMode.value = isShortMode
        _isCreatePostDialogOpen.value = true
    }

    fun closeCreatePostDialog() {
        _isCreatePostDialogOpen.value = false
        _isCreateShortMode.value = false
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

    fun createPost(
        text: String,
        mediaUri: Uri? = null,
        feeling: String? = null,
        location: String? = null,
        thumbnailFile: File? = null,
        durationLabel: String? = null,
        isShort: Boolean = false,
        groupId: Int? = null
    ) {
        if (text.isBlank() && mediaUri == null) return

        viewModelScope.launch {
            _isCreatePostDialogOpen.value = false

            val mimeType = if (mediaUri != null) {
                getApplication<Application>().contentResolver.getType(mediaUri) ?: ""
            } else ""
            val isVideo = mimeType.startsWith("video")

            if (isVideo && mediaUri != null) {
                _uploadProgress.value = "Iniciando preparación del vídeo..."
                _uploadProgressFraction.value = 0.02f
                showStatus(if (isShort) "Fragmentando y subiendo Short familiar al NAS..." else "Fragmentando y subiendo vídeo al NAS...")

                val result = repository.uploadChunkedVideoPost(
                    contentText = text,
                    videoUri = mediaUri,
                    feeling = feeling,
                    location = location,
                    isShort = isShort,
                    groupId = groupId,
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
                showStatus(if (isShort) "Subiendo Short familiar al Synology NAS..." else "Subiendo publicación al Synology NAS...")

                val result = repository.addPost(
                    contentText = text,
                    mediaUri = mediaUri,
                    feeling = feeling,
                    location = location,
                    thumbnailFile = thumbnailFile,
                    durationLabel = durationLabel,
                    isShort = isShort
                )

                result.fold(
                    onSuccess = { postId ->
                        showStatus(if (isShort) "¡Short publicado en la sección Shorts!" else "Publicado en Synology NAS (ID #$postId)")
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

    fun clearStatus() {
        _statusMessage.value = null
    }

    private fun showStatus(msg: String) {
        _statusMessage.value = msg
    }
}
