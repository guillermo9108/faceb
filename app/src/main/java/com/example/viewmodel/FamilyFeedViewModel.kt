package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FamilyRepository
import com.example.model.FamilyMember
import com.example.model.FamilyStory
import com.example.model.NasConfig
import com.example.model.Post
import com.example.model.ReactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FeedTab(val label: String) {
    FEED("Inicio"),
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
    val nasConfig = repository.nasConfig
    val stories = repository.stories
    val isLoading = repository.isLoading

    private val _selectedTab = MutableStateFlow(FeedTab.FEED)
    val selectedTab: StateFlow<FeedTab> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedMemberFilter = MutableStateFlow<FamilyMember?>(null)
    val selectedMemberFilter: StateFlow<FamilyMember?> = _selectedMemberFilter.asStateFlow()

    private val _selectedPostForComments = MutableStateFlow<Post?>(null)
    val selectedPostForComments: StateFlow<Post?> = _selectedPostForComments.asStateFlow()

    private val _isCreatePostDialogOpen = MutableStateFlow(false)
    val isCreatePostDialogOpen: StateFlow<Boolean> = _isCreatePostDialogOpen.asStateFlow()

    private val _isMemberSwitchDialogOpen = MutableStateFlow(false)
    val isMemberSwitchDialogOpen: StateFlow<Boolean> = _isMemberSwitchDialogOpen.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        // Intento de conexión inicial y refresco del feed real
        viewModelScope.launch {
            repository.pingNas()
            repository.refreshFeed()
        }
    }

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

    fun openCreatePostDialog() {
        _isCreatePostDialogOpen.value = true
    }

    fun closeCreatePostDialog() {
        _isCreatePostDialogOpen.value = false
    }

    fun openMemberSwitchDialog() {
        _isMemberSwitchDialogOpen.value = true
    }

    fun closeMemberSwitchDialog() {
        _isMemberSwitchDialogOpen.value = false
    }

    fun switchMember(member: FamilyMember) {
        repository.setCurrentMember(member)
        _isMemberSwitchDialogOpen.value = false
        showStatus("Sesión cambiada a: ${member.name}")
    }

    fun openCommentsSheet(post: Post) {
        _selectedPostForComments.value = post
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

    fun createPost(
        text: String,
        mediaUri: Uri? = null,
        feeling: String? = null,
        location: String? = null
    ) {
        if (text.isBlank() && mediaUri == null) return

        viewModelScope.launch {
            _isCreatePostDialogOpen.value = false
            showStatus("Subiendo publicación al Synology NAS...")

            val result = repository.addPost(
                contentText = text,
                mediaUri = mediaUri,
                feeling = feeling,
                location = location
            )

            result.fold(
                onSuccess = { postId ->
                    showStatus("Publicado en Synology NAS (ID #$postId)")
                },
                onFailure = { error ->
                    showStatus("Error al subir al NAS: ${error.localizedMessage}")
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
            result.fold(
                onSuccess = {
                    showStatus("Comentario guardado en MariaDB 10")
                    _selectedPostForComments.value = repository.posts.value.find { it.id == postId }
                },
                onFailure = { error ->
                    showStatus("Error al comentar: ${error.localizedMessage}")
                }
            )
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

    fun updateNasSettings(ip: String, port: Int, path: String, dbPort: Int = 3307) {
        repository.updateNasConfig(ip, port, path, dbPort)
        showStatus("Configuración de Synology actualizada (Web: $port, BD: $dbPort)")
    }

    fun testNasConnection() {
        viewModelScope.launch {
            val ping = repository.pingNas()
            if (ping > 0) {
                showStatus("Conexión con Synology Web Station exitosa (${ping} ms)")
            } else {
                showStatus("No se pudo conectar con el NAS. Verifica IP y puerto.")
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
