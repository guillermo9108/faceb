package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_posts")
data class PostEntity(
    @PrimaryKey val id: Int,
    val authorId: Int,
    val authorName: String,
    val authorRole: String,
    val authorEmoji: String,
    val authorColorHex: Long,
    val contentText: String,
    val createdAt: Long,
    val mediaUrl: String?,
    val mediaType: String?,
    val mediaCaption: String?,
    val mediaDuration: String?,
    val feeling: String?,
    val location: String?,
    val isPinned: Boolean,
    val likesCount: Int,
    val commentsCount: Int,
    val userReaction: String?,
    val isShort: Boolean = false,
    val thumbUrl: String? = null
)
