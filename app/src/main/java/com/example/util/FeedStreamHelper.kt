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
     * 4) Agrupación automática de ráfagas (>= 3 publicaciones seguidas del mismo autor y grupo en ventana corta).
     */
    data class BurstClusterEntry(
        val clusterId: String,
        val author: FamilyMember,
        val groupId: Int?,
        val groupName: String?,
        val posts: List<Post>
    ) : FeedStreamEntry {
        override val stableKey: String = "burst_$clusterId"
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

object FeedStreamHelper {

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
     * 2) Regla anti-monotonía: evita más de [maxConsecutiveAuthor] publicaciones seguidas del mismo autor
     * o más de [maxConsecutiveGroup] del mismo grupo intercambiando candidatos cercanos dentro de [lookahead].
     */
    fun diversifyPosts(
        posts: List<Post>,
        maxConsecutiveAuthor: Int = 2,
        maxConsecutiveGroup: Int = 2,
        lookahead: Int = 12
    ): List<Post> {
        if (posts.size <= 2) return posts
        val remaining = posts.toMutableList()
        val result = ArrayList<Post>(posts.size)

        while (remaining.isNotEmpty()) {
            val pickedIndex = pickDiverseIndex(
                remaining = remaining,
                history = result,
                maxConsecutiveAuthor = maxConsecutiveAuthor,
                maxConsecutiveGroup = maxConsecutiveGroup,
                lookahead = lookahead,
                authorOf = { it.author.id },
                groupOf = { it.groupId }
            )
            result.add(remaining.removeAt(pickedIndex))
        }
        return result
    }

    private fun <T> diversifyItems(
        items: List<T>,
        maxConsecutiveAuthor: Int = 2,
        maxConsecutiveGroup: Int = 2,
        lookahead: Int = 12,
        authorOf: (T) -> Int?,
        groupOf: (T) -> Int?
    ): List<T> {
        if (items.size <= 2) return items
        val remaining = items.toMutableList()
        val result = ArrayList<T>(items.size)
        while (remaining.isNotEmpty()) {
            val idx = pickDiverseIndex(
                remaining = remaining,
                history = result,
                maxConsecutiveAuthor = maxConsecutiveAuthor,
                maxConsecutiveGroup = maxConsecutiveGroup,
                lookahead = lookahead,
                authorOf = authorOf,
                groupOf = groupOf
            )
            result.add(remaining.removeAt(idx))
        }
        return result
    }

    private fun <T> pickDiverseIndex(
        remaining: List<T>,
        history: List<T>,
        maxConsecutiveAuthor: Int,
        maxConsecutiveGroup: Int,
        lookahead: Int,
        authorOf: (T) -> Int?,
        groupOf: (T) -> Int?
    ): Int {
        val searchLimit = minOf(remaining.size, lookahead.coerceAtLeast(1))

        fun consecutiveAuthorViolated(candidate: T): Boolean {
            val candidateAuthor = authorOf(candidate) ?: return false
            if (history.size < maxConsecutiveAuthor) return false
            val tail = history.takeLast(maxConsecutiveAuthor)
            return tail.all { authorOf(it) == candidateAuthor }
        }

        fun consecutiveGroupViolated(candidate: T): Boolean {
            val candidateGroup = groupOf(candidate) ?: return false
            if (history.size < maxConsecutiveGroup) return false
            val tail = history.takeLast(maxConsecutiveGroup)
            return tail.all { groupOf(it) == candidateGroup }
        }

        // Pasada 1: respetar autor y grupo
        for (i in 0 until searchLimit) {
            val cand = remaining[i]
            if (!consecutiveAuthorViolated(cand) && !consecutiveGroupViolated(cand)) {
                return i
            }
        }
        // Pasada 2: respetar al menos autor
        for (i in 0 until searchLimit) {
            if (!consecutiveAuthorViolated(remaining[i])) {
                return i
            }
        }
        return 0
    }

    /**
     * 4) Agrupa >= [minBurstCount] publicaciones seguidas del mismo autor y mismo grupo
     * creadas dentro de una ventana de [windowMs] (p.ej. 2 horas) en una tarjeta colapsable BurstClusterEntry.
     */
    fun collapseBurstEntries(
        posts: List<Post>,
        minBurstCount: Int = 3,
        windowMs: Long = 2L * 3600_000L
    ): List<FeedStreamEntry> {
        if (posts.isEmpty()) return emptyList()
        val entries = ArrayList<FeedStreamEntry>(posts.size)
        var i = 0
        while (i < posts.size) {
            val head = posts[i]
            if (head.isPinned || head.isShort) {
                entries.add(FeedStreamEntry.PostEntry(head))
                i++
                continue
            }
            val run = mutableListOf(head)
            var j = i + 1
            while (j < posts.size) {
                val next = posts[j]
                if (!next.isPinned &&
                    !next.isShort &&
                    next.author.id == head.author.id &&
                    next.groupId == head.groupId &&
                    abs(run.last().createdAt - next.createdAt) <= windowMs
                ) {
                    run.add(next)
                    j++
                } else {
                    break
                }
            }
            if (run.size >= minBurstCount) {
                val clusterId = "${head.author.id}_${head.groupId ?: "wall"}_${head.id}"
                entries.add(
                    FeedStreamEntry.BurstClusterEntry(
                        clusterId = clusterId,
                        author = head.author,
                        groupId = head.groupId,
                        groupName = head.groupName,
                        posts = run
                    )
                )
            } else {
                run.forEach { entries.add(FeedStreamEntry.PostEntry(it)) }
            }
            i = j
        }
        return entries
    }

    /**
     * Construye el flujo inteligente del muro aplicando:
     * 1) Prioridad a contenido no visto o con comentarios nuevos + separador "Ya estás al día"
     * 2) Anti-monotonía (máx. 2 seguidos mismo autor / mismo grupo)
     * 3) Bloques variados intercalados: Shorts, Recuerdos del NAS, Conversación activa, Continúa viendo (Series)
     * 4) Agrupación automática de ráfagas (>= 3 seguidos)
     * 5) Rotación ligera determinista con [sessionSeed] al refrescar
     */
    fun buildSmartFeedEntries(
        posts: List<Post>,
        shorts: List<Post>,
        groups: List<FamilyGroup> = emptyList(),
        followedGroupIds: Set<Int> = emptySet(),
        seenCommentsSnapshot: Map<Int, Int> = emptyMap(),
        sessionSeed: Long = 0L,
        interval: Int = 4,
        groupSize: Int = 6
    ): List<FeedStreamEntry> {
        if (posts.isEmpty()) return emptyList()

        val pinnedPosts = posts.filter { it.isPinned }
        val unpinnedPosts = posts.filter { !it.isPinned }

        val hasSeenHistory = seenCommentsSnapshot.isNotEmpty()
        val unseenPosts = if (hasSeenHistory) {
            unpinnedPosts.filter { isPostUnseenOrHasNewActivity(it, seenCommentsSnapshot) }
        } else {
            unpinnedPosts
        }
        val seenPostsRaw = if (hasSeenHistory) {
            unpinnedPosts.filter { !isPostUnseenOrHasNewActivity(it, seenCommentsSnapshot) }
        } else {
            emptyList()
        }

        // 5) Ligera variación de orden en el bloque ya visto cuando el usuario refresca el muro
        val seenPosts = rotateSeenWithSessionSeed(seenPostsRaw, sessionSeed)

        // Detectar conversación familiar activa fuera de los 2 primeros posts para no duplicar arriba
        val activeConvCandidate: Post? = selectActiveConversationCandidate(
            unseenPosts = unseenPosts,
            seenPosts = seenPosts,
            pinnedIds = pinnedPosts.map { it.id }.toSet(),
            sessionSeed = sessionSeed
        )

        val unseenFiltered = if (activeConvCandidate != null) {
            unseenPosts.filter { it.id != activeConvCandidate.id }
        } else unseenPosts
        val seenFiltered = if (activeConvCandidate != null) {
            seenPosts.filter { it.id != activeConvCandidate.id }
        } else seenPosts

        // 4) Agrupar ráfagas + 2) Diversificar dentro de cada bloque
        val unseenBaseEntries = diversifyBaseEntries(collapseBurstEntries(unseenFiltered))
        val seenBaseEntries = diversifyBaseEntries(collapseBurstEntries(seenFiltered))

        // Unificar secuencia de base con separador "Ya estás al día" si corresponde
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

        // Preparar bloques especiales ricos (3a, 3b, 3c) y carrusel de Shorts
        val validShorts = shorts.filter { it.isShort || it.mediaItems.isNotEmpty() }
        val continueSeriesEntry = buildContinueSeriesEntry(
            groups = groups,
            followedGroupIds = followedGroupIds,
            posts = posts,
            seenCommentsSnapshot = seenCommentsSnapshot,
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

        // Intercalado limpio respetando el flujo y evitando bloques especiales consecutivos
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

        // Asegurar aparición de bloques especiales clave si el muro es corto
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

    private fun diversifyBaseEntries(entries: List<FeedStreamEntry>): List<FeedStreamEntry> {
        return diversifyItems(
            items = entries,
            maxConsecutiveAuthor = 2,
            maxConsecutiveGroup = 2,
            lookahead = 12,
            authorOf = { entry ->
                when (entry) {
                    is FeedStreamEntry.PostEntry -> entry.post.author.id
                    is FeedStreamEntry.BurstClusterEntry -> entry.author.id
                    else -> null
                }
            },
            groupOf = { entry ->
                when (entry) {
                    is FeedStreamEntry.PostEntry -> entry.post.groupId
                    is FeedStreamEntry.BurstClusterEntry -> entry.groupId
                    else -> null
                }
            }
        )
    }

    private fun rotateSeenWithSessionSeed(seenPosts: List<Post>, sessionSeed: Long): List<Post> {
        if (seenPosts.size <= 3 || sessionSeed == 0L) return seenPosts
        val rng = Random(sessionSeed)
        val first = seenPosts.first()
        val tail = seenPosts.drop(1)
        // Barajado suave por ventanas de 4 para variar ligeramente sin mandar lo nuevo al fondo absoluto
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
        sessionSeed: Long
    ): FeedStreamEntry.ContinueSeriesEntry? {
        val seriesGroups = groups.filter { g ->
            g.organizationMode == GroupOrganizationMode.SERIES &&
                (followedGroupIds.isEmpty() || followedGroupIds.contains(g.id))
        }
        if (seriesGroups.isEmpty()) return null

        val withPosts = seriesGroups.mapNotNull { group ->
            val episodes = posts.filter { it.groupId == group.id }.sortedBy { it.createdAt }
            if (episodes.isEmpty()) null else group to episodes
        }
        if (withPosts.isEmpty()) return null

        val chosenIdx = (abs(sessionSeed) % withPosts.size.toLong()).toInt()
        val (group, episodes) = withPosts[chosenIdx]
        val nextEpisode = episodes.firstOrNull { !seenCommentsSnapshot.containsKey(it.id) } ?: episodes.first()
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
