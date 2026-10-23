package com.example.data

import android.content.Context
import android.net.Uri
import com.example.data.local.FamilyDatabase
import com.example.data.local.PostEntity
import com.example.data.remote.NasApiClient
import com.example.model.Comment
import com.example.model.FamilyMember
import com.example.model.FamilyStory
import com.example.model.MediaItem
import com.example.model.MediaType
import com.example.model.NasConfig
import com.example.model.Post
import com.example.model.ReactionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FamilyRepository(
    private val context: Context,
    private val apiClient: NasApiClient = NasApiClient(),
    private val database: FamilyDatabase = FamilyDatabase.getInstance(context)
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    // Miembros familiares predefinidos (sincronizables con MariaDB 10)
    val defaultFamilyMembers = listOf(
        FamilyMember(1, "Carlos (Papá)", "Papá", "👨‍💼", 0xFF1E40AF, "1234", "Jefe del asador de los domingos 🍖"),
        FamilyMember(2, "Laura (Mamá)", "Mamá", "👩‍🌾", 0xFF9D174D, "1234", "Amante de las orquídeas y repostería 🌸"),
        FamilyMember(3, "Sofía", "Hija Mayor", "👧", 0xFF7C3AED, "1234", "Toco guitarra 🎸 y estudiante"),
        FamilyMember(4, "Mateo", "Hijo Menor", "👦", 0xFF0D9488, "1234", "Fútbol y experimentos ⚽"),
        FamilyMember(5, "Rosa (Abuela)", "Abuela", "👵", 0xFFB45309, "1234", "Recetas tradicionales familiares 🥧")
    )

    private val _familyMembers = MutableStateFlow(defaultFamilyMembers)
    val familyMembers: StateFlow<List<FamilyMember>> = _familyMembers.asStateFlow()

    private val _currentMember = MutableStateFlow(defaultFamilyMembers[0])
    val currentMember: StateFlow<FamilyMember> = _currentMember.asStateFlow()

    private val _nasConfig = MutableStateFlow(NasConfig(isConnected = false, lastPingMs = -1L))
    val nasConfig: StateFlow<NasConfig> = _nasConfig.asStateFlow()

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts.asStateFlow()

    private val _stories = MutableStateFlow<List<FamilyStory>>(emptyList())
    val stories: StateFlow<List<FamilyStory>> = _stories.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        // Cargar publicaciones en caché local (Room) primero
        scope.launch {
            database.postDao().getAllPosts().collect { cachedList ->
                if (_posts.value.isEmpty() && cachedList.isNotEmpty()) {
                    _posts.value = cachedList.map { it.toDomainPost(findMember(it.authorId)) }
                }
            }
        }
    }

    private fun findMember(userId: Int): FamilyMember {
        return _familyMembers.value.find { it.id == userId }
            ?: FamilyMember(userId, "Familiar #$userId", "Familiar", "👤", 0xFF1E40AF)
    }

    fun setCurrentMember(member: FamilyMember) {
        _currentMember.value = member
    }

    fun updateNasConfig(ip: String, port: Int, path: String, dbPort: Int = 3307) {
        _nasConfig.update { it.copy(ip = ip, port = port, path = path, dbPort = dbPort) }
        scope.launch {
            pingNas()
            refreshFeed()
        }
    }

    /**
     * Prueba de conexión real por red LAN al Synology NAS.
     */
    suspend fun pingNas(): Long {
        val config = _nasConfig.value
        val result = apiClient.pingNas(config.ip, config.port)
        return result.fold(
            onSuccess = { ms ->
                _nasConfig.update { it.copy(isConnected = true, lastPingMs = ms, lastError = null) }
                ms
            },
            onFailure = { error ->
                _nasConfig.update { it.copy(isConnected = false, lastPingMs = -1L, lastError = error.localizedMessage ?: "Error de conexión") }
                -1L
            }
        )
    }

    /**
     * Sincronización real con Synology Web Station y MariaDB 10.
     */
    suspend fun refreshFeed(): Result<List<Post>> {
        _isLoading.value = true
        val config = _nasConfig.value

        // Sincronizar usuarios de MariaDB si están disponibles
        apiClient.fetchUsers(config.ip, config.port, config.path).onSuccess { realUsers ->
            if (realUsers.isNotEmpty()) {
                _familyMembers.value = realUsers
            }
        }

        // Obtener publicaciones reales del NAS
        val result = apiClient.fetchFeed(config.ip, config.port, config.path)
        _isLoading.value = false

        return result.fold(
            onSuccess = { realPosts ->
                _posts.value = realPosts
                _nasConfig.update { it.copy(isConnected = true, lastError = null) }

                // Guardar en la base de datos local Room
                scope.launch {
                    val entities = realPosts.map { it.toEntity() }
                    database.postDao().clearAll()
                    database.postDao().insertPosts(entities)
                }

                // Generar historias a partir de publicaciones con fotos recientes
                val newStories = realPosts.filter { it.mediaItems.isNotEmpty() }.take(5).mapIndexed { idx, post ->
                    FamilyStory(
                        id = "story_${post.id}",
                        author = post.author,
                        title = post.contentText.take(20) + "...",
                        imageUrl = post.mediaItems.first().url,
                        timestamp = post.createdAt
                    )
                }
                _stories.value = newStories

                Result.success(realPosts)
            },
            onFailure = { error ->
                _nasConfig.update { it.copy(isConnected = false, lastError = error.localizedMessage) }
                Result.failure(error)
            }
        )
    }

    /**
     * Publicación real con archivo real subido al Synology NAS.
     */
    suspend fun addPost(
        contentText: String,
        mediaUri: Uri? = null,
        feeling: String? = null,
        location: String? = null
    ): Result<Int> {
        val config = _nasConfig.value
        val author = _currentMember.value

        val result = apiClient.createPost(
            ip = config.ip,
            port = config.port,
            path = config.path,
            context = context,
            userId = author.id,
            text = contentText,
            feeling = feeling,
            location = location,
            mediaUri = mediaUri
        )

        return result.fold(
            onSuccess = { postId ->
                // Refrescar feed inmediatamente desde el NAS
                scope.launch { refreshFeed() }
                Result.success(postId)
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }

    /**
     * Reacción real a MariaDB 10.
     */
    suspend fun toggleReaction(postId: Int, reactionType: ReactionType) {
        val config = _nasConfig.value
        val author = _currentMember.value

        // Actualización optimista en memoria
        _posts.update { current ->
            current.map { post ->
                if (post.id == postId) {
                    val isSame = post.userReaction == reactionType
                    val map = post.reactions.toMutableMap()
                    if (isSame) {
                        map.remove(reactionType)
                        post.copy(reactions = map, userReaction = null)
                    } else {
                        map[reactionType] = (map[reactionType] ?: 0) + 1
                        post.copy(reactions = map, userReaction = reactionType)
                    }
                } else post
            }
        }

        // Llamada de red real al NAS
        apiClient.sendReaction(config.ip, config.port, config.path, postId, author.id, reactionType.name)
    }

    /**
     * Comentario real en MariaDB 10.
     */
    suspend fun addComment(postId: Int, text: String): Result<Int> {
        val config = _nasConfig.value
        val author = _currentMember.value

        val result = apiClient.sendComment(config.ip, config.port, config.path, postId, author.id, text)
        result.onSuccess {
            // Actualizar feed para mostrar el nuevo comentario
            refreshFeed()
        }
        return result
    }

    /**
     * Eliminar publicación real del NAS.
     */
    suspend fun deletePost(postId: Int): Result<Unit> {
        val config = _nasConfig.value
        val result = apiClient.deletePost(config.ip, config.port, config.path, postId)
        result.onSuccess {
            database.postDao().deletePost(postId)
            _posts.update { current -> current.filterNot { it.id == postId } }
        }
        return result
    }

    fun togglePinPost(postId: Int) {
        _posts.update { current ->
            current.map { if (it.id == postId) it.copy(isPinned = !it.isPinned) else it }
                .sortedWith(compareByDescending<Post> { it.isPinned }.thenByDescending { it.createdAt })
        }
    }
}

private fun PostEntity.toDomainPost(author: FamilyMember): Post {
    val mediaList = if (!mediaUrl.isNullOrBlank()) {
        listOf(
            MediaItem(
                id = "m_$id",
                type = if (mediaType.equals("VIDEO", ignoreCase = true)) MediaType.VIDEO else MediaType.IMAGE,
                url = mediaUrl,
                caption = mediaCaption ?: "",
                durationLabel = mediaDuration
            )
        )
    } else emptyList()

    val rxMap = if (likesCount > 0) mapOf(ReactionType.LIKE to likesCount) else emptyMap()

    return Post(
        id = id,
        author = author,
        contentText = contentText,
        createdAt = createdAt,
        mediaItems = mediaList,
        reactions = rxMap,
        feeling = feeling,
        location = location,
        isPinned = isPinned
    )
}

private fun Post.toEntity(): PostEntity {
    val firstMedia = mediaItems.firstOrNull()
    return PostEntity(
        id = id,
        authorId = author.id,
        authorName = author.name,
        authorRole = author.role,
        authorEmoji = author.avatarEmoji,
        authorColorHex = author.avatarColorHex,
        contentText = contentText,
        createdAt = createdAt,
        mediaUrl = firstMedia?.url,
        mediaType = firstMedia?.type?.name,
        mediaCaption = firstMedia?.caption,
        mediaDuration = firstMedia?.durationLabel,
        feeling = feeling,
        location = location,
        isPinned = isPinned,
        likesCount = reactions.values.sum(),
        commentsCount = comments.size,
        userReaction = userReaction?.name
    )
}
