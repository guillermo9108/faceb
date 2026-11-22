package com.example.util

import com.example.model.Post

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
}

object FeedStreamHelper {

    /**
     * Filtra las publicaciones del muro para mostrar únicamente:
     * 1) Publicaciones del propio usuario
     * 2) Publicaciones de otros usuarios que sigue
     * 3) Publicaciones pertenecientes a grupos que sigue
     * Mantiene estrictamente el orden original de las publicaciones.
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

        // Si hay Shorts publicados y no alcanzó a intercalarse ningún carrusel (p. ej. muro con < interval posts),
        // añadir un bloque de Shorts al final respetando el orden de los posts previos.
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
