package com.example.util

import com.example.model.FamilyGroup
import com.example.model.FamilyMember
import com.example.model.GroupOrganizationMode
import com.example.model.Post
import kotlin.math.abs
import kotlin.random.Random

sealed interface FeedStreamEntry {
    val stableKey: String

    data class PostEntry(val post: Post) : FeedStreamEntry {
        override val stableKey: String = "post_${post.id}"
    }

    data class ShortsGroupEntry(
        val groupIndex: Int,
        val shorts: List<Post>
    ) : FeedStreamEntry {
        override val stableKey: String = "shorts_group_$groupIndex"
    }

    /**
     * 1) Separador visual que marca dónde terminan las publicaciones nuevas/con actividad
     * y empiezan las publicaciones que el usuario ya vio anteriormente.
     */
    data class CaughtUpDividerEntry(
        val unseenCount: Int,
        val seenCount: Int
    ) : FeedStreamEntry {
        override val stableKey: String = "caught_up_divider"
    }

    /**
     * 3a) Tarjeta intercalada de recuerdos familiares del NAS.
     */
    data class MemoriesCardEntry(
        val title: String,
        val subtitle: String,
        val memoryPosts: List<Post>
    ) : FeedStreamEntry {
        override val stableKey: String = "memories_card"
    }

    /**
     * 3b) Publicación destacada por tener conversación familiar activa (comentarios recientes).
     */
    data class ActiveConversationEntry(
        val post: Post,
        val badgeText: String
    ) : FeedStreamEntry {
        override val stableKey: String = "active_conv_${post.id}"
    }

    /**
     * 3c) Tarjeta "Continúa viendo" para grupos configurados en modo Series.
     */
    data class ContinueSeriesEntry(
        val group: FamilyGroup,
        val nextPost: Post,
        val totalEpisodes: Int
    ) : FeedStreamEntry {
        override val stableKey: String = "continue_series_${group.id}"
    }
}

enum class FeedMediaFormat {
    TEXT,
    SINGLE_IMAGE,
    MOSAIC,
    VIDEO
}

object FeedStreamHelper {

    fun classifyMediaFormat(post: Post): FeedMediaFormat {
        if (post.mediaItems.isEmpty()) return FeedMediaFormat.TEXT
        if (post.mediaItems.any { it.type == com.example.model.MediaType.VIDEO }) {
            return FeedMediaFormat.VIDEO
        }
        return if (post.mediaItems.size > 1) FeedMediaFormat.MOSAIC else FeedMediaFormat.SINGLE_IMAGE
    }

    /**
     * Filtra las publicaciones del muro para mostrar únicamente:
     * 1) Publicaciones del propio usuario
     * 2) Publicaciones de otros usuarios que sigue
     * 3) Publicaciones pertenecientes a grupos que sigue
     */
    fun filterFeedByFollows(
        posts: List<Post>,
        currentUserId: Int,
        followedUserIds: Set<Int>,
        followedGroupIds: Set<Int>
    ): List<Post> {
        return posts.filter { post ->
            post.author.id == currentUserId ||
                followedUserIds.contains(post.author.id) ||
                (post.groupId != null && followedGroupIds.contains(post.groupId))
        }
    }

    /**
     * 1) Determina si una publicación es nueva o tiene comentarios nuevos desde la última vez que se vio.
     */
    fun isPostUnseenOrHasNewActivity(
        post: Post,
        seenCommentsSnapshot: Map<Int, Int>
    ): Boolean {
        val lastSeenComments = seenCommentsSnapshot[post.id] ?: return true
        return post.comments.size > lastSeenComments
    }

    /**
     * Puntuación ligera estilo ranking de Facebook combinando novedad, frescura,
     * conversación/reacciones, afinidad familiar y disponibilidad offline en caché local.
     */
    fun computeRelevanceScore(
        post: Post,
        seenCommentsSnapshot: Map<Int, Int> = emptyMap(),
        preferredAuthorIds: Set<Int> = emptySet(),
        preferredGroupIds: Set<Int> = emptySet(),
        isOfflineOrSlow: Boolean = false,
        cachedPostIds: Set<Int> = emptySet(),
        referenceTimeMs: Long = post.createdAt.coerceAtLeast(1L)
    ): Double {
        val lastSeenCount = seenCommentsSnapshot[post.id]
        val isUnseen = lastSeenCount == null
        val newCommentsDelta = if (lastSeenCount != null) (post.comments.size - lastSeenCount).coerceAtLeast(0) else 0

        var score = 0.0
        if (isUnseen) {
            score += 45.0
        } else if (newCommentsDelta > 0) {
            score += 36.0 + (newCommentsDelta * 4.0).coerceAtMost(20.0)
        }

        // Decaimiento suave de frescura por horas (evita congelamiento cronológico de una sola carpeta)
        val ageHours = ((referenceTimeMs - post.createdAt).coerceAtLeast(0L)) / 3_600_000.0
        score += 35.0 / (1.0 + (ageHours / 12.0))

        // Señales de interacción familiar
        val totalReactions = post.reactions.values.sum()
        score += (totalReactions * 1.6).coerceAtMost(18.0)
        score += (post.comments.size * 3.2).coerceAtMost(24.0)

        // Afinidad personal y de grupo
        if (preferredAuthorIds.contains(post.author.id)) score += 12.0
        if (post.groupId != null && preferredGroupIds.contains(post.groupId)) score += 10.0

        // Pequeño impulso a álbumes mosaico variados frente a ítems individuales repetitivos
        if (classifyMediaFormat(post) == FeedMediaFormat.MOSAIC) score += 4.0

        // Bonus offline cuando no hay conexión LAN con el NAS
        if (isOfflineOrSlow && (post.isSavedOffline || cachedPostIds.contains(post.id))) {
            score += 30.0
        }

        return score
    }

    /**
     * Regla anti-monotonía + espaciado + mezcla de formatos estilo Facebook:
     * evita más de [maxConsecutiveAuthor] del mismo autor, [maxConsecutiveGroup] del mismo grupo,
     * aplica enfriamiento de grupo de [minGroupCooldown] pasos y evita dos vídeos seguidos en el muro
     * cuando hay variedad disponible dentro de [lookahead].
     */
    fun diversifyPosts(
        posts: List<Post>,
        maxConsecutiveAuthor: Int = 1,
        maxConsecutiveGroup: Int = 1,
        minGroupCooldown: Int = 2,
        avoidConsecutiveVideo: Boolean = true,
        lookahead: Int = 16,
        initialHistory: List<Post> = emptyList()
    ): List<Post> {
        if (posts.isEmpty()) return emptyList()
        if (posts.size == 1 && initialHistory.isEmpty()) return posts
        val remaining = posts.toMutableList()
        val combinedHistory = initialHistory.toMutableList()
        val result = ArrayList<Post>(posts.size)

        while (remaining.isNotEmpty()) {
            val pickedIndex = pickDiversePostIndex(
                remaining = remaining,
                history = combinedHistory,
                maxConsecutiveAuthor = maxConsecutiveAuthor,
                maxConsecutiveGroup = maxConsecutiveGroup,
                minGroupCooldown = minGroupCooldown,
                avoidConsecutiveVideo = avoidConsecutiveVideo,
                lookahead = lookahead
            )
            val chosen = remaining.removeAt(pickedIndex)
            combinedHistory.add(chosen)
            result.add(chosen)
        }
        return result
    }

    private fun pickDiversePostIndex(
        remaining: List<Post>,
        history: List<Post>,
        maxConsecutiveAuthor: Int,
        maxConsecutiveGroup: Int,
        minGroupCooldown: Int,
        avoidConsecutiveVideo: Boolean,
        lookahead: Int
    ): Int {
        val searchLimit = minOf(remaining.size, lookahead.coerceAtLeast(1))

        fun authorConsecutiveViolated(cand: Post): Boolean {
            if (history.size < maxConsecutiveAuthor) return false
            return history.takeLast(maxConsecutiveAuthor).all { it.author.id == cand.author.id }
        }

        fun groupConsecutiveViolated(cand: Post): Boolean {
            val gId = cand.groupId ?: return false
            if (history.size < maxConsecutiveGroup) return false
            return history.takeLast(maxConsecutiveGroup).all { it.groupId == gId }
        }

        fun groupCooldownViolated(cand: Post): Boolean {
            val gId = cand.groupId ?: return false
            if (minGroupCooldown <= 0 || history.isEmpty()) return false
            return history.takeLast(minGroupCooldown).any { it.groupId == gId }
        }

        fun videoBackToBackViolated(cand: Post): Boolean {
            if (!avoidConsecutiveVideo || history.isEmpty()) return false
            return classifyMediaFormat(history.last()) == FeedMediaFormat.VIDEO &&
                classifyMediaFormat(cand) == FeedMediaFormat.VIDEO
        }

        // Pasada 1: autor + enfriamiento de grupo + mezcla de formato
        for (i in 0 until searchLimit) {
            val c = remaining[i]
            if (!authorConsecutiveViolated(c) && !groupCooldownViolated(c) && !videoBackToBackViolated(c)) {
                return i
            }
        }
        // Pasada 2: evitar autor consecutivo y grupo consecutivo
        for (i in 0 until searchLimit) {
            val c = remaining[i]
            if (!authorConsecutiveViolated(c) && !groupConsecutiveViolated(c)) {
                return i
            }
        }
        // Pasada 3: evitar al menos mismo grupo consecutivo
        for (i in 0 until searchLimit) {
            if (!groupConsecutiveViolated(remaining[i])) {
                return i
            }
        }
        return 0
    }

    /**
     * Protege grupos en modo SERIES dentro de un bloque para priorizar el episodio más antiguo no visto
     * antes que los posteriores, evitando spoilers al mostrar contenido en el muro principal.
     */
    private fun applySeriesProgressionOrder(
        posts: List<Post>,
        seriesGroupIds: Set<Int>,
        watchedPostIds: Set<Int> = emptySet()
    ): List<Post> {
        if (seriesGroupIds.isEmpty() || posts.size <= 1) return posts
        val groupedQueues = posts
            .filter { it.groupId != null && seriesGroupIds.contains(it.groupId) }
            .groupBy { it.groupId!! }
            .mapValues { (_, list) ->
                val sorted = list.sortedWith(SeriesEpisodeHelper.seriesComparator())
                if (watchedPostIds.isEmpty()) {
                    sorted.toMutableList()
                } else {
                    val unwatched = sorted.filter { !watchedPostIds.contains(it.id) }
                    val watched = sorted.filter { watchedPostIds.contains(it.id) }
                    (unwatched + watched).toMutableList()
                }
            }
        if (groupedQueues.isEmpty()) return posts

        return posts.map { post ->
            val gid = post.groupId
            if (gid != null && groupedQueues.containsKey(gid)) {
                groupedQueues.getValue(gid).removeAt(0)
            } else {
                post
            }
        }
    }

    /**
     * Construye el flujo inteligente del muro al estilo Facebook sin agrupar en tarjetas colapsables:
     * 1) Prioridad por puntuación de relevancia + novedad + afinidad + modo offline
     * 2) Protección secuencial de grupos en modo SERIES (episodio más antiguo sin ver primero)
     * 3) Diversidad estricta y espaciado anti-ráfaga (evita monopolio de autor/grupo y mezcla formatos)
     * 4) Bloques intercalados (Shorts, Continúa viendo, Conversación activa, Recuerdos) y separador "Ya estás al día"
     */
    fun buildSmartFeedEntries(
        posts: List<Post>,
        shorts: List<Post>,
        groups: List<FamilyGroup> = emptyList(),
        followedGroupIds: Set<Int> = emptySet(),
        seenCommentsSnapshot: Map<Int, Int> = emptyMap(),
        preferredAuthorIds: Set<Int> = emptySet(),
        preferredGroupIds: Set<Int> = emptySet(),
        isOfflineOrSlow: Boolean = false,
        cachedPostIds: Set<Int> = emptySet(),
        watchedPostIds: Set<Int> = emptySet(),
        sessionSeed: Long = 0L,
        interval: Int = 4,
        groupSize: Int = 6
    ): List<FeedStreamEntry> {
        if (posts.isEmpty()) return emptyList()

        val referenceTimeMs = posts.maxOfOrNull { it.createdAt } ?: System.currentTimeMillis()
        val seriesGroupIds = groups
            .filter { it.organizationMode == GroupOrganizationMode.SERIES }
            .map { it.id }
            .toSet()

        val pinnedPosts = posts.filter { it.isPinned }
        val unpinnedPosts = posts.filter { !it.isPinned }

        val hasSeenHistory = seenCommentsSnapshot.isNotEmpty()
        val unseenRaw = if (hasSeenHistory) {
            unpinnedPosts.filter { isPostUnseenOrHasNewActivity(it, seenCommentsSnapshot) }
        } else {
            unpinnedPosts
        }
        val seenRaw = if (hasSeenHistory) {
            unpinnedPosts.filter { !isPostUnseenOrHasNewActivity(it, seenCommentsSnapshot) }
        } else {
            emptyList()
        }

        // Ordenar por relevancia dentro de no vistos
        val unseenRanked = unseenRaw.sortedWith(
            compareByDescending<Post> {
                computeRelevanceScore(
                    post = it,
                    seenCommentsSnapshot = seenCommentsSnapshot,
                    preferredAuthorIds = preferredAuthorIds,
                    preferredGroupIds = preferredGroupIds,
                    isOfflineOrSlow = isOfflineOrSlow,
                    cachedPostIds = cachedPostIds,
                    referenceTimeMs = referenceTimeMs
                )
            }.thenByDescending { it.createdAt }
        )

        val unseenSeriesProtected = applySeriesProgressionOrder(unseenRanked, seriesGroupIds, watchedPostIds)

        // Rotación ligera para contenido ya visto al refrescar
        val seenRanked = seenRaw.sortedWith(
            compareByDescending<Post> {
                computeRelevanceScore(
                    post = it,
                    seenCommentsSnapshot = seenCommentsSnapshot,
                    preferredAuthorIds = preferredAuthorIds,
                    preferredGroupIds = preferredGroupIds,
                    isOfflineOrSlow = isOfflineOrSlow,
                    cachedPostIds = cachedPostIds,
                    referenceTimeMs = referenceTimeMs
                )
            }.thenByDescending { it.createdAt }
        )
        val seenRotated = rotateSeenWithSessionSeed(seenRanked, sessionSeed)

        // Detectar conversación familiar activa fuera de los 2 primeros posts para no duplicar arriba
        val activeConvCandidate: Post? = selectActiveConversationCandidate(
            unseenPosts = unseenSeriesProtected,
            seenPosts = seenRotated,
            pinnedIds = pinnedPosts.map { it.id }.toSet(),
            sessionSeed = sessionSeed
        )

        val unseenFiltered = if (activeConvCandidate != null) {
            unseenSeriesProtected.filter { it.id != activeConvCandidate.id }
        } else unseenSeriesProtected
        val seenFiltered = if (activeConvCandidate != null) {
            seenRotated.filter { it.id != activeConvCandidate.id }
        } else seenRotated

        // Espaciado contextual estricto + mezcla de formatos (sin tarjetas BurstCluster)
        val unseenDiversified = diversifyPosts(
            posts = unseenFiltered,
            maxConsecutiveAuthor = 1,
            maxConsecutiveGroup = 1,
            minGroupCooldown = 2,
            avoidConsecutiveVideo = true,
            lookahead = 16
        )
        val seenDiversified = diversifyPosts(
            posts = seenFiltered,
            maxConsecutiveAuthor = 1,
            maxConsecutiveGroup = 1,
            minGroupCooldown = 2,
            avoidConsecutiveVideo = true,
            lookahead = 16
        )

        val unseenBaseEntries = unseenDiversified.map { FeedStreamEntry.PostEntry(it) }
        val seenBaseEntries = seenDiversified.map { FeedStreamEntry.PostEntry(it) }

        val timelineEntries = ArrayList<FeedStreamEntry>(pinnedPosts.size + unseenBaseEntries.size + seenBaseEntries.size + 6)
        pinnedPosts.forEach { timelineEntries.add(FeedStreamEntry.PostEntry(it)) }
        timelineEntries.addAll(unseenBaseEntries)

        if (hasSeenHistory && seenBaseEntries.isNotEmpty()) {
            timelineEntries.add(
                FeedStreamEntry.CaughtUpDividerEntry(
                    unseenCount = unseenBaseEntries.size,
                    seenCount = seenBaseEntries.size
                )
            )
        }
        timelineEntries.addAll(seenBaseEntries)

        val validShorts = shorts.filter { it.isShort || it.mediaItems.isNotEmpty() }
        val continueSeriesEntry = buildContinueSeriesEntry(
            groups = groups,
            followedGroupIds = followedGroupIds,
            posts = posts,
            seenCommentsSnapshot = seenCommentsSnapshot,
            watchedPostIds = watchedPostIds,
            sessionSeed = sessionSeed
        )
        val memoriesEntry = buildMemoriesEntry(
            posts = posts,
            sessionSeed = sessionSeed
        )
        val activeConvEntry = activeConvCandidate?.let { post ->
            val commentsCount = post.comments.size
            FeedStreamEntry.ActiveConversationEntry(
                post = post,
                badgeText = if (commentsCount == 1) "💬 Conversación activa · 1 comentario" else "💬 Conversación activa · $commentsCount comentarios"
            )
        }

        val safeInterval = interval.coerceAtLeast(1)
        val safeGroupSize = groupSize.coerceAtLeast(1)
        val initialShortOffset = if (validShorts.isNotEmpty()) {
            ((abs(sessionSeed) % validShorts.size.toLong()).toInt())
        } else 0

        var shortsOffset = initialShortOffset
        var groupCounter = 0
        var seriesInserted = false
        var activeConvInserted = false
        var memoriesInserted = false

        val result = ArrayList<FeedStreamEntry>(timelineEntries.size + 8)
        var cardCounter = 0

        timelineEntries.forEachIndexed { idx, entry ->
            result.add(entry)
            if (entry is FeedStreamEntry.CaughtUpDividerEntry) {
                return@forEachIndexed
            }
            cardCounter++

            val hasMoreAfter = idx < timelineEntries.lastIndex
            if (hasMoreAfter && cardCounter % safeInterval == 0 && validShorts.isNotEmpty()) {
                val slice = sliceShortsBatch(validShorts, shortsOffset, safeGroupSize)
                if (slice.isNotEmpty()) {
                    result.add(FeedStreamEntry.ShortsGroupEntry(groupIndex = groupCounter++, shorts = slice))
                    shortsOffset += slice.size
                }
            } else if (hasMoreAfter && cardCounter == 3 && !seriesInserted && continueSeriesEntry != null) {
                result.add(continueSeriesEntry)
                seriesInserted = true
            } else if (hasMoreAfter && cardCounter == 5 && !activeConvInserted && activeConvEntry != null) {
                result.add(activeConvEntry)
                activeConvInserted = true
            } else if (hasMoreAfter && cardCounter == 7 && !memoriesInserted && memoriesEntry != null) {
                result.add(memoriesEntry)
                memoriesInserted = true
            }
        }

        if (!seriesInserted && continueSeriesEntry != null) {
            result.add(continueSeriesEntry)
        }
        if (!activeConvInserted && activeConvEntry != null) {
            result.add(activeConvEntry)
        }
        if (!memoriesInserted && memoriesEntry != null && result.size >= 3) {
            result.add(memoriesEntry)
        }
        if (validShorts.isNotEmpty() && groupCounter == 0) {
            val slice = sliceShortsBatch(validShorts, initialShortOffset, safeGroupSize)
            if (slice.isNotEmpty()) {
                result.add(FeedStreamEntry.ShortsGroupEntry(groupIndex = groupCounter, shorts = slice))
            }
        }

        return result
    }

    /**
     * Formatea y diversifica EXCLUSIVAMENTE un lote recién paginado al hacer scroll hacia abajo,
     * para anexarlo estrictamente al final del muro sin mover ni reordenar las publicaciones ya visibles arriba.
     */
    fun buildAppendedFeedEntries(
        newPosts: List<Post>,
        shorts: List<Post>,
        groups: List<FamilyGroup> = emptyList(),
        seenCommentsSnapshot: Map<Int, Int> = emptyMap(),
        preferredAuthorIds: Set<Int> = emptySet(),
        preferredGroupIds: Set<Int> = emptySet(),
        isOfflineOrSlow: Boolean = false,
        cachedPostIds: Set<Int> = emptySet(),
        watchedPostIds: Set<Int> = emptySet(),
        previousTailPosts: List<Post> = emptyList(),
        existingShortsGroupCount: Int = 0,
        sessionSeed: Long = 0L,
        interval: Int = 4,
        groupSize: Int = 6
    ): List<FeedStreamEntry> {
        if (newPosts.isEmpty()) return emptyList()

        val referenceTimeMs = newPosts.maxOfOrNull { it.createdAt } ?: System.currentTimeMillis()
        val seriesGroupIds = groups
            .filter { it.organizationMode == GroupOrganizationMode.SERIES }
            .map { it.id }
            .toSet()

        val rankedNew = newPosts.sortedWith(
            compareByDescending<Post> {
                computeRelevanceScore(
                    post = it,
                    seenCommentsSnapshot = seenCommentsSnapshot,
                    preferredAuthorIds = preferredAuthorIds,
                    preferredGroupIds = preferredGroupIds,
                    isOfflineOrSlow = isOfflineOrSlow,
                    cachedPostIds = cachedPostIds,
                    referenceTimeMs = referenceTimeMs
                )
            }.thenByDescending { it.createdAt }
        )

        val seriesProtected = applySeriesProgressionOrder(rankedNew, seriesGroupIds, watchedPostIds)
        val diversified = diversifyPosts(
            posts = seriesProtected,
            maxConsecutiveAuthor = 1,
            maxConsecutiveGroup = 1,
            minGroupCooldown = 2,
            avoidConsecutiveVideo = true,
            lookahead = 16,
            initialHistory = previousTailPosts.takeLast(2)
        )

        val validShorts = shorts.filter { it.isShort || it.mediaItems.isNotEmpty() }
        val safeInterval = interval.coerceAtLeast(1)
        val safeGroupSize = groupSize.coerceAtLeast(1)
        var groupCounter = existingShortsGroupCount
        var shortsOffset = if (validShorts.isNotEmpty()) {
            (((abs(sessionSeed) + (existingShortsGroupCount * safeGroupSize)) % validShorts.size.toLong()).toInt())
        } else 0

        val result = ArrayList<FeedStreamEntry>(diversified.size + 3)
        diversified.forEachIndexed { idx, post ->
            result.add(FeedStreamEntry.PostEntry(post))
            val pos = idx + 1
            if (validShorts.isNotEmpty() && pos % safeInterval == 0 && idx < diversified.lastIndex) {
                val slice = sliceShortsBatch(validShorts, shortsOffset, safeGroupSize)
                if (slice.isNotEmpty()) {
                    result.add(FeedStreamEntry.ShortsGroupEntry(groupIndex = groupCounter++, shorts = slice))
                    shortsOffset += slice.size
                }
            }
        }
        return result
    }

    private fun rotateSeenWithSessionSeed(seenPosts: List<Post>, sessionSeed: Long): List<Post> {
        if (seenPosts.size <= 3 || sessionSeed == 0L) return seenPosts
        val rng = Random(sessionSeed)
        val first = seenPosts.first()
        val tail = seenPosts.drop(1)
        val rotated = ArrayList<Post>(seenPosts.size).apply { add(first) }
        tail.chunked(4).forEach { chunk ->
            rotated.addAll(chunk.shuffled(rng))
        }
        return rotated
    }

    private fun selectActiveConversationCandidate(
        unseenPosts: List<Post>,
        seenPosts: List<Post>,
        pinnedIds: Set<Int>,
        sessionSeed: Long
    ): Post? {
        val topExcludedIds = (unseenPosts.take(2) + seenPosts.take(1)).map { it.id }.toSet() + pinnedIds
        val allCandidates = (unseenPosts + seenPosts)
            .filter { it.id !in topExcludedIds && it.comments.isNotEmpty() }
            .sortedWith(compareByDescending<Post> { it.comments.size }.thenByDescending { it.createdAt })

        if (allCandidates.isEmpty()) return null
        val topPool = allCandidates.take(3)
        val pickIdx = (abs(sessionSeed) % topPool.size.toLong()).toInt()
        return topPool[pickIdx]
    }

    private fun buildContinueSeriesEntry(
        groups: List<FamilyGroup>,
        followedGroupIds: Set<Int>,
        posts: List<Post>,
        seenCommentsSnapshot: Map<Int, Int>,
        watchedPostIds: Set<Int>,
        sessionSeed: Long
    ): FeedStreamEntry.ContinueSeriesEntry? {
        val seriesGroups = groups.filter { g ->
            g.organizationMode == GroupOrganizationMode.SERIES &&
                (followedGroupIds.isEmpty() || followedGroupIds.contains(g.id))
        }
        if (seriesGroups.isEmpty()) return null

        val withPosts = seriesGroups.mapNotNull { group ->
            val episodes = posts.filter { it.groupId == group.id }.sortedWith(SeriesEpisodeHelper.seriesComparator())
            if (episodes.isEmpty()) null else group to episodes
        }
        if (withPosts.isEmpty()) return null

        val chosenIdx = (abs(sessionSeed) % withPosts.size.toLong()).toInt()
        val (group, episodes) = withPosts[chosenIdx]
        val nextEpisode = episodes.firstOrNull { !watchedPostIds.contains(it.id) }
            ?: episodes.firstOrNull { !seenCommentsSnapshot.containsKey(it.id) }
            ?: episodes.first()
        return FeedStreamEntry.ContinueSeriesEntry(
            group = group,
            nextPost = nextEpisode,
            totalEpisodes = episodes.size
        )
    }

    private fun buildMemoriesEntry(
        posts: List<Post>,
        sessionSeed: Long
    ): FeedStreamEntry.MemoriesCardEntry? {
        if (posts.size < 4) return null
        val candidates = posts.drop(3).filter { it.mediaItems.isNotEmpty() || it.contentText.isNotBlank() }
        if (candidates.size < 2) return null

        val rotatedPool = if (sessionSeed != 0L) {
            candidates.shuffled(Random(sessionSeed xor 0x5A5A5A5AL))
        } else candidates

        val selected = rotatedPool.take(4)
        val title = if (abs(sessionSeed) % 2L == 0L) "Recuerdos del NAS" else "Momentos para revivir"
        return FeedStreamEntry.MemoriesCardEntry(
            title = title,
            subtitle = "Álbum familiar rescatado de tu archivo local",
            memoryPosts = selected
        )
    }

    /**
     * Intercala grupos horizontales de Shorts publicados entre las publicaciones del muro,
     * respetando intacto el orden relativo de todas las publicaciones.
     */
    fun buildFeedEntries(
        posts: List<Post>,
        shorts: List<Post>,
        interval: Int = 4,
        groupSize: Int = 6
    ): List<FeedStreamEntry> {
        if (posts.isEmpty()) return emptyList()
        val safeInterval = interval.coerceAtLeast(1)
        val safeGroupSize = groupSize.coerceAtLeast(1)
        val validShorts = shorts.filter { it.isShort || it.mediaItems.isNotEmpty() }

        val result = ArrayList<FeedStreamEntry>(posts.size + 4)
        var shortsOffset = 0
        var groupCounter = 0

        posts.forEachIndexed { index, post ->
            result.add(FeedStreamEntry.PostEntry(post))

            val positionOneBased = index + 1
            if (validShorts.isNotEmpty() && positionOneBased % safeInterval == 0 && index < posts.lastIndex) {
                val slice = sliceShortsBatch(validShorts, shortsOffset, safeGroupSize)
                if (slice.isNotEmpty()) {
                    result.add(FeedStreamEntry.ShortsGroupEntry(groupIndex = groupCounter++, shorts = slice))
                    shortsOffset += slice.size
                }
            }
        }

        if (validShorts.isNotEmpty() && groupCounter == 0) {
            val slice = sliceShortsBatch(validShorts, 0, safeGroupSize)
            if (slice.isNotEmpty()) {
                result.add(FeedStreamEntry.ShortsGroupEntry(groupIndex = groupCounter, shorts = slice))
            }
        }

        return result
    }

    private fun sliceShortsBatch(shorts: List<Post>, offset: Int, count: Int): List<Post> {
        if (shorts.isEmpty()) return emptyList()
        if (shorts.size <= count) {
            return if (offset == 0) shorts else emptyList()
        }
        val start = offset % shorts.size
        val batch = ArrayList<Post>(count.coerceAtMost(shorts.size))
        for (i in 0 until count.coerceAtMost(shorts.size)) {
            batch.add(shorts[(start + i) % shorts.size])
        }
        return batch
    }
}
