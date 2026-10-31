package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {

    @Query("SELECT * FROM cached_groups ORDER BY createdAt DESC")
    fun getAllGroups(): Flow<List<GroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<GroupEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)

    @Query("SELECT * FROM cached_groups WHERE id = :groupId LIMIT 1")
    suspend fun getGroupById(groupId: Int): GroupEntity?

    @Query("DELETE FROM cached_groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: Int)

    @Query("DELETE FROM cached_groups")
    suspend fun clearAll()
}
