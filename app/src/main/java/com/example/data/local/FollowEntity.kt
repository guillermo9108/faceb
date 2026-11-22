package com.example.data.local

import androidx.room.Entity

@Entity(
    tableName = "cached_follows",
    primaryKeys = ["followerId", "targetType", "targetId"]
)
data class FollowEntity(
    val followerId: Int,
    val targetType: String, // "USER" or "GROUP"
    val targetId: Int
)
