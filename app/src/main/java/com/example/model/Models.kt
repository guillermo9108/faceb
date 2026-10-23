package com.example.model

enum class MediaType {
    IMAGE,
    VIDEO
}

enum class ReactionType(val emoji: String, val label: String) {
    LIKE("👍", "Me gusta"),
    LOVE("❤️", "Me encanta"),
    LAUGH("😂", "Me divierte"),
    CLAP("👏", "Bravo"),
    HUG("🤗", "Abrazo")
}

data class FamilyMember(
    val id: Int,
    val name: String,
    val role: String, // "Papá", "Mamá", "Hija mayor", "Hijo", "Abuela"
    val avatarEmoji: String = "👤",
    val avatarColorHex: Long = 0xFF1E40AF,
    val pin: String = "1234",
    val bio: String = "",
    val postsCount: Int = 0,
    val isOnlineInLan: Boolean = true
)

data class MediaItem(
    val id: String,
    val type: MediaType = MediaType.IMAGE,
    val url: String,
    val thumbnailUrl: String = url,
    val caption: String = "",
    val durationLabel: String? = null
)

data class Comment(
    val id: Int,
    val postId: Int,
    val author: FamilyMember,
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class Post(
    val id: Int,
    val author: FamilyMember,
    val contentText: String,
    val createdAt: Long = System.currentTimeMillis(),
    val mediaItems: List<MediaItem> = emptyList(),
    val reactions: Map<ReactionType, Int> = emptyMap(),
    val userReaction: ReactionType? = null,
    val comments: List<Comment> = emptyList(),
    val feeling: String? = null,
    val isPinned: Boolean = false,
    val location: String? = null
)

data class FamilyStory(
    val id: String,
    val author: FamilyMember,
    val title: String,
    val imageUrl: String,
    val isViewed: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class NasConfig(
    val ip: String = "192.168.1.100",
    val port: Int = 8085,
    val path: String = "/family_api",
    val isConnected: Boolean = false,
    val lastPingMs: Long = -1L,
    val lastError: String? = null,
    val serverTech: String = "Synology Web Station (Nginx + PHP 8.2)",
    val databaseTech: String = "MariaDB 10 (Puerto 3307)",
    val uploadMaxLimit: String = "1024 MB"
)
