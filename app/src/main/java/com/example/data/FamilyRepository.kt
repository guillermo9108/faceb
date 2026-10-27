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

    private val sessionPrefs = context.getSharedPreferences("family_session_prefs", Context.MODE_PRIVATE)

    private fun loadSavedSession(): FamilyMember? {
        val isLoggedIn = sessionPrefs.getBoolean("is_logged_in", false)
        val remember = sessionPrefs.getBoolean("remember_session", true)
        if (isLoggedIn && remember) {
            val id = sessionPrefs.getInt("user_id", -1)
            if (id > 0) {
                return FamilyMember(
                    id = id,
                    name = sessionPrefs.getString("display_name", "Familiar") ?: "Familiar",
                    role = sessionPrefs.getString("role", "Familiar") ?: "Familiar",
                    avatarEmoji = sessionPrefs.getString("avatar_emoji", "👤") ?: "👤",
                    avatarColorHex = sessionPrefs.getLong("avatar_color_hex", 0xFF1E40AF),
                    pin = sessionPrefs.getString("pin", "1234") ?: "1234",
                    bio = sessionPrefs.getString("bio", "") ?: "",
                    username = sessionPrefs.getString("username", "") ?: ""
                )
            }
        }
        return null
    }

    private fun saveSession(member: FamilyMember, rememberSession: Boolean) {
        sessionPrefs.edit()
            .putBoolean("is_logged_in", true)
            .putBoolean("remember_session", rememberSession)
            .putInt("user_id", member.id)
            .putString("username", member.username)
            .putString("display_name", member.name)
            .putString("role", member.role)
            .putString("avatar_emoji", member.avatarEmoji)
            .putLong("avatar_color_hex", member.avatarColorHex)
            .putString("bio", member.bio)
            .putString("pin", member.pin)
            .apply()
    }

    private fun clearSession() {
        sessionPrefs.edit().clear().apply()
    }

    private val savedSession = loadSavedSession()
    private val _isUserLoggedIn = MutableStateFlow(savedSession != null)
    val isUserLoggedIn: StateFlow<Boolean> = _isUserLoggedIn.asStateFlow()

    private val _currentMember = MutableStateFlow(savedSession ?: defaultFamilyMembers[0])
    val currentMember: StateFlow<FamilyMember> = _currentMember.asStateFlow()

    private val prefs = context.getSharedPreferences("nas_config_prefs", Context.MODE_PRIVATE)

    private fun loadSavedNasConfig(): NasConfig {
        return NasConfig(
            ip = prefs.getString("nas_ip", "192.168.1.100") ?: "192.168.1.100",
            port = prefs.getInt("nas_port", 8085),
            path = prefs.getString("nas_path", "/family_api") ?: "/family_api",
            dbPort = prefs.getInt("nas_db_port", 3307),
            dbHost = prefs.getString("nas_db_host", "127.0.0.1") ?: "127.0.0.1",
            dbName = prefs.getString("nas_db_name", "family_feed") ?: "family_feed",
            dbUser = prefs.getString("nas_db_user", "root") ?: "root",
            dbPass = prefs.getString("nas_db_pass", "") ?: "",
            isConnected = false,
            lastPingMs = -1L
        )
    }

    private fun persistNasConfig(config: NasConfig) {
        prefs.edit()
            .putString("nas_ip", config.ip)
            .putInt("nas_port", config.port)
            .putString("nas_path", config.path)
            .putInt("nas_db_port", config.dbPort)
            .putString("nas_db_host", config.dbHost)
            .putString("nas_db_name", config.dbName)
            .putString("nas_db_user", config.dbUser)
            .putString("nas_db_pass", config.dbPass)
            .apply()
    }

    private val _nasConfig = MutableStateFlow(loadSavedNasConfig())
    val nasConfig: StateFlow<NasConfig> = _nasConfig.asStateFlow()

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts.asStateFlow()

    private val _shorts = MutableStateFlow<List<Post>>(emptyList())
    val shorts: StateFlow<List<Post>> = _shorts.asStateFlow()

    // Caché en memoria persistente de comentarios por ID de publicación
    private val _commentsByPostId = java.util.concurrent.ConcurrentHashMap<Int, List<Comment>>()

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
        saveSession(member, sessionPrefs.getBoolean("remember_session", true))
    }

    suspend fun login(username: String, pass: String, rememberSession: Boolean): Result<FamilyMember> {
        val config = _nasConfig.value
        val result = apiClient.login(config.ip, config.port, config.path, username, pass)
        val finalResult = if (result.isSuccess) {
            result
        } else {
            // Fallback offline / LAN desconectada: verificar perfiles familiares precargados o locales
            val localMatch = _familyMembers.value.find {
                it.username.equals(username, ignoreCase = true) ||
                it.name.lowercase().replace(" ", "").contains(username.lowercase())
            }
            if (localMatch != null && (pass == localMatch.pin || pass.isBlank())) {
                Result.success(localMatch)
            } else {
                result
            }
        }

        finalResult.onSuccess { member ->
            _currentMember.value = member
            _isUserLoggedIn.value = true
            saveSession(member, rememberSession)
            scope.launch {
                refreshFeed()
                refreshShorts()
            }
        }
        return finalResult
    }

    suspend fun register(
        username: String,
        pass: String,
        displayName: String,
        role: String,
        avatarEmoji: String,
        avatarColorHex: Long,
        rememberSession: Boolean
    ): Result<FamilyMember> {
        val config = _nasConfig.value
        val result = apiClient.register(
            config.ip,
            config.port,
            config.path,
            username,
            pass,
            displayName,
            role,
            avatarEmoji,
            avatarColorHex
        )
        val finalResult = if (result.isSuccess) {
            result
        } else {
            // Fallback offline: crear usuario local
            val newId = (_familyMembers.value.maxOfOrNull { it.id } ?: 10) + 1
            val localUser = FamilyMember(
                id = newId,
                name = displayName,
                role = role,
                avatarEmoji = avatarEmoji,
                avatarColorHex = avatarColorHex,
                username = username,
                pin = pass
            )
            Result.success(localUser)
        }

        finalResult.onSuccess { member ->
            _currentMember.value = member
            _isUserLoggedIn.value = true
            saveSession(member, rememberSession)
            _familyMembers.update { list -> if (list.any { it.id == member.id }) list else list + member }
            scope.launch {
                refreshFeed()
                refreshShorts()
            }
        }
        return finalResult
    }

    fun logout() {
        clearSession()
        _isUserLoggedIn.value = false
    }

    fun skipLoginAsGuest() {
        _isUserLoggedIn.value = true
    }

    suspend fun updateProfile(
        displayName: String,
        avatarEmoji: String,
        avatarColorHex: Long,
        bio: String,
        newPassword: String?
    ): Result<FamilyMember> {
        val config = _nasConfig.value
        val cur = _currentMember.value
        val result = apiClient.updateProfile(
            config.ip,
            config.port,
            config.path,
            cur.id,
            displayName,
            avatarEmoji,
            avatarColorHex,
            bio,
            newPassword
        )
        val finalResult = if (result.isSuccess) {
            result
        } else {
            // Fallback offline: aplicar cambio localmente
            val updated = cur.copy(
                name = displayName,
                avatarEmoji = avatarEmoji,
                avatarColorHex = avatarColorHex,
                bio = bio
            )
            Result.success(updated)
        }

        finalResult.onSuccess { updated ->
            _currentMember.value = updated
            saveSession(updated, sessionPrefs.getBoolean("remember_session", true))
            _familyMembers.update { list -> list.map { if (it.id == updated.id) updated else it } }
        }
        return finalResult
    }

    suspend fun refreshShorts(): Result<List<Post>> {
        val config = _nasConfig.value
        val result = apiClient.fetchShorts(config.ip, config.port, config.path)
        result.onSuccess { list ->
            _shorts.value = list
        }
        return result
    }

    fun updateNasConfig(
        ip: String,
        port: Int,
        path: String,
        dbPort: Int = 3307,
        dbHost: String = "127.0.0.1",
        dbName: String = "family_feed",
        dbUser: String = "root",
        dbPass: String = ""
    ) {
        val updated = _nasConfig.value.copy(
            ip = ip,
            port = port,
            path = path,
            dbPort = dbPort,
            dbHost = dbHost,
            dbName = dbName,
            dbUser = dbUser,
            dbPass = dbPass
        )
        _nasConfig.value = updated
        persistNasConfig(updated)
        scope.launch {
            pingNas()
            refreshFeed()
        }
    }

    fun updateNasConfig(config: NasConfig) {
        _nasConfig.value = config
        persistNasConfig(config)
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

        // Sincronizar historias independientes desde MariaDB
        apiClient.fetchStories(config.ip, config.port, config.path).onSuccess { realStories ->
            if (realStories.isNotEmpty()) {
                _stories.value = realStories
            }
        }

        // Obtener publicaciones reales del NAS
        val result = apiClient.fetchFeed(config.ip, config.port, config.path)
        _isLoading.value = false

        return result.fold(
            onSuccess = { realPosts ->
                // Preservar comentarios previamente cargados o en caché para cada publicación
                val mergedPosts = realPosts.map { post ->
                    val existingComments = _commentsByPostId[post.id]
                        ?: _posts.value.find { it.id == post.id }?.comments
                        ?: post.comments
                    if (existingComments.isNotEmpty()) {
                        _commentsByPostId[post.id] = existingComments
                    }
                    post.copy(comments = existingComments)
                }
                _posts.value = mergedPosts
                _nasConfig.update { it.copy(isConnected = true, lastError = null) }

                // Guardar progresivamente en Room sin borrar todo para mantener caché y no perder miniaturas
                scope.launch {
                    val entities = mergedPosts.map { it.toEntity() }
                    database.postDao().insertPosts(entities)
                }

                // Si aún no hay historias independientes en la tabla de historias, generar a partir de fotos recientes
                if (_stories.value.isEmpty()) {
                    val fallbackStories = mergedPosts.filter { it.mediaItems.isNotEmpty() }.take(5).mapIndexed { idx, post ->
                        FamilyStory(
                            id = "story_${post.id}",
                            author = post.author,
                            title = post.contentText.take(24),
                            imageUrl = post.mediaItems.first().thumbnailUrl.ifBlank { post.mediaItems.first().url },
                            mediaUrl = post.mediaItems.first().url,
                            caption = post.contentText,
                            timestamp = post.createdAt
                        )
                    }
                    _stories.value = fallbackStories
                }

                Result.success(mergedPosts)
            },
            onFailure = { error ->
                _nasConfig.update { it.copy(isConnected = false, lastError = error.localizedMessage) }
                Result.failure(error)
            }
        )
    }

    /**
     * Carga comentarios en tiempo real desde MariaDB para una publicación específica.
     */
    suspend fun loadCommentsForPost(postId: Int): Result<List<Comment>> {
        val config = _nasConfig.value
        val result = apiClient.fetchComments(config.ip, config.port, config.path, postId)
        result.onSuccess { comments ->
            _commentsByPostId[postId] = comments
            _posts.update { list ->
                list.map { post ->
                    if (post.id == postId) post.copy(comments = comments) else post
                }
            }
        }
        return result
    }

    /**
     * Publicación real con archivo real, miniatura y duración subida al Synology NAS.
     */
    suspend fun addPost(
        contentText: String,
        mediaUri: Uri? = null,
        feeling: String? = null,
        location: String? = null,
        thumbnailFile: java.io.File? = null,
        durationLabel: String? = null,
        isShort: Boolean = false
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
            mediaUri = mediaUri,
            thumbnailFile = thumbnailFile,
            durationLabel = durationLabel,
            isShort = isShort
        )

        return result.fold(
            onSuccess = { postId ->
                // Refrescar feed y shorts inmediatamente desde el NAS
                scope.launch {
                    refreshFeed()
                    refreshShorts()
                }
                Result.success(postId)
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }

    /**
     * Crea y sube una historia independiente a MariaDB 10 en el Synology NAS.
     */
    suspend fun addStory(
        caption: String,
        storyType: com.example.model.StoryType,
        bgColorHex: Long,
        mediaUri: Uri? = null
    ): Result<Int> {
        val config = _nasConfig.value
        val author = _currentMember.value

        val result = apiClient.createStory(
            ip = config.ip,
            port = config.port,
            path = config.path,
            context = context,
            userId = author.id,
            caption = caption,
            storyType = storyType.name,
            bgColorHex = bgColorHex,
            mediaUri = mediaUri
        )

        return result.fold(
            onSuccess = { storyId ->
                // Actualizar optimista local
                val newStory = FamilyStory(
                    id = "story_$storyId",
                    author = author,
                    title = caption,
                    imageUrl = mediaUri?.toString() ?: "",
                    mediaUrl = mediaUri?.toString() ?: "",
                    storyType = storyType,
                    caption = caption,
                    backgroundColorHex = bgColorHex,
                    timestamp = System.currentTimeMillis()
                )
                _stories.update { listOf(newStory) + it }
                // Refrescar historias desde el NAS
                scope.launch {
                    apiClient.fetchStories(config.ip, config.port, config.path).onSuccess {
                        if (it.isNotEmpty()) _stories.value = it
                    }
                }
                Result.success(storyId)
            },
            onFailure = { error ->
                // Si está offline, permitir crear la historia localmente para no bloquear al usuario en la LAN
                val localStory = FamilyStory(
                    id = "story_local_${System.currentTimeMillis()}",
                    author = author,
                    title = caption,
                    imageUrl = mediaUri?.toString() ?: "",
                    mediaUrl = mediaUri?.toString() ?: "",
                    storyType = storyType,
                    caption = caption,
                    backgroundColorHex = bgColorHex,
                    timestamp = System.currentTimeMillis()
                )
                _stories.update { listOf(localStory) + it }
                Result.success(1)
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

        // Creación optimista inmediata del comentario para que aparezca al instante
        val optimisticComment = Comment(
            id = (1000..99999).random(),
            postId = postId,
            author = author,
            text = text,
            createdAt = System.currentTimeMillis()
        )
        val currentComments = _commentsByPostId[postId] ?: _posts.value.find { it.id == postId }?.comments ?: emptyList()
        val updatedComments = currentComments + optimisticComment
        _commentsByPostId[postId] = updatedComments
        _posts.update { list ->
            list.map { post ->
                if (post.id == postId) post.copy(comments = updatedComments) else post
            }
        }

        val result = apiClient.sendComment(config.ip, config.port, config.path, postId, author.id, text)
        result.onSuccess {
            // Sincronizar comentarios reales desde MariaDB 10 para obtener IDs definitivos
            loadCommentsForPost(postId)
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
        isPinned = isPinned,
        isShort = isShort
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
        userReaction = userReaction?.name,
        isShort = isShort
    )
}
