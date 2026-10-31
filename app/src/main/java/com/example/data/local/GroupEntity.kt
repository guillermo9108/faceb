package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.FamilyGroup

@Entity(tableName = "cached_groups")
data class GroupEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val folderPath: String,
    val description: String = "",
    val coverPhotoUrl: String? = null,
    val postsCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomainGroup(): FamilyGroup {
        return FamilyGroup(
            id = id,
            name = name,
            folderPath = folderPath,
            description = description,
            coverPhotoUrl = coverPhotoUrl,
            postsCount = postsCount,
            createdAt = createdAt
        )
    }
}

fun FamilyGroup.toEntity(): GroupEntity {
    return GroupEntity(
        id = id,
        name = name,
        folderPath = folderPath,
        description = description,
        coverPhotoUrl = coverPhotoUrl,
        postsCount = postsCount,
        createdAt = createdAt
    )
}
