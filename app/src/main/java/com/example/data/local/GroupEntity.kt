package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.FamilyGroup
import com.example.model.GroupOrganizationMode

@Entity(tableName = "cached_groups")
data class GroupEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val folderPath: String,
    val description: String = "",
    val coverPhotoUrl: String? = null,
    val postsCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val createdByUserId: Int = 1,
    val allowUploads: Boolean = true,
    val organizationMode: String = GroupOrganizationMode.NORMAL.value
) {
    fun toDomainGroup(): FamilyGroup {
        val mode = if (organizationMode.equals("SERIES", ignoreCase = true)) {
            GroupOrganizationMode.SERIES
        } else {
            GroupOrganizationMode.NORMAL
        }
        return FamilyGroup(
            id = id,
            name = name,
            folderPath = folderPath,
            description = description,
            coverPhotoUrl = coverPhotoUrl,
            postsCount = postsCount,
            createdAt = createdAt,
            createdByUserId = createdByUserId,
            allowUploads = allowUploads,
            organizationMode = mode
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
        createdAt = createdAt,
        createdByUserId = createdByUserId,
        allowUploads = allowUploads,
        organizationMode = organizationMode.value
    )
}
