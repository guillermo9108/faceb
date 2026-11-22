package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FollowDao {

    @Query("SELECT * FROM cached_follows WHERE followerId = :followerId")
    fun observeFollowsForUser(followerId: Int): Flow<List<FollowEntity>>

    @Query("SELECT targetId FROM cached_follows WHERE followerId = :followerId AND targetType = 'USER'")
    suspend fun getFollowedUserIds(followerId: Int): List<Int>

    @Query("SELECT targetId FROM cached_follows WHERE followerId = :followerId AND targetType = 'GROUP'")
    suspend fun getFollowedGroupIds(followerId: Int): List<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFollow(follow: FollowEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFollows(follows: List<FollowEntity>)

    @Query("DELETE FROM cached_follows WHERE followerId = :followerId AND targetType = :targetType AND targetId = :targetId")
    suspend fun deleteFollow(followerId: Int, targetType: String, targetId: Int)

    @Query("DELETE FROM cached_follows WHERE followerId = :followerId")
    suspend fun clearFollowsForUser(followerId: Int)

    @Transaction
    suspend fun replaceFollowsForUser(followerId: Int, userIds: Set<Int>, groupIds: Set<Int>) {
        clearFollowsForUser(followerId)
        val entries = buildList {
            userIds.forEach { add(FollowEntity(followerId, "USER", it)) }
            groupIds.forEach { add(FollowEntity(followerId, "GROUP", it)) }
        }
        if (entries.isNotEmpty()) {
            insertFollows(entries)
        }
    }
}
