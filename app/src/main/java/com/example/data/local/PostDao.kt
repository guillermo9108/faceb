package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {

    @Query("SELECT * FROM cached_posts WHERE isShort = 0 ORDER BY isPinned DESC, createdAt DESC")
    fun getFeedPosts(): Flow<List<PostEntity>>

    @Query("SELECT * FROM cached_posts WHERE isShort = 1 ORDER BY createdAt DESC")
    fun getShortsPosts(): Flow<List<PostEntity>>

    @Query("SELECT * FROM cached_posts WHERE groupId = :groupId ORDER BY isPinned DESC, createdAt DESC")
    fun getPostsByGroupId(groupId: Int): Flow<List<PostEntity>>

    @Query("SELECT * FROM cached_posts WHERE groupId = :groupId ORDER BY isPinned DESC, createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPostsByGroupIdPaged(groupId: Int, limit: Int, offset: Int): List<PostEntity>

    @Query("SELECT * FROM cached_posts WHERE groupId = :groupId")
    suspend fun getPostsByGroupIdSync(groupId: Int): List<PostEntity>

    @Query("SELECT * FROM cached_posts WHERE isShort = 0 ORDER BY isPinned DESC, createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getFeedPostsPaged(limit: Int, offset: Int): List<PostEntity>

    @Query("SELECT * FROM cached_posts WHERE isShort = 0 ORDER BY isPinned DESC, createdAt DESC")
    suspend fun getFeedPostsSync(): List<PostEntity>

    @Query("""
        SELECT * FROM cached_posts
        WHERE (
            contentText LIKE :pattern
            OR authorName LIKE :pattern
            OR IFNULL(feeling, '') LIKE :pattern
            OR IFNULL(location, '') LIKE :pattern
            OR IFNULL(groupName, '') LIKE :pattern
        )
        ORDER BY isPinned DESC, createdAt DESC
        LIMIT 200
    """)
    suspend fun searchPostsGlobal(pattern: String): List<PostEntity>

    @Query("""
        SELECT * FROM cached_posts
        WHERE groupId = :groupId
          AND (
            contentText LIKE :pattern
            OR authorName LIKE :pattern
            OR IFNULL(feeling, '') LIKE :pattern
            OR IFNULL(location, '') LIKE :pattern
            OR IFNULL(groupName, '') LIKE :pattern
          )
        ORDER BY isPinned DESC, createdAt DESC
        LIMIT 200
    """)
    suspend fun searchPostsInGroup(groupId: Int, pattern: String): List<PostEntity>

    @Query("SELECT * FROM cached_posts ORDER BY isPinned DESC, createdAt DESC")
    suspend fun getAllPostsSync(): List<PostEntity>

    @Query("SELECT * FROM cached_posts WHERE isSavedOffline = 1")
    suspend fun getSavedOfflinePosts(): List<PostEntity>

    @Query("SELECT * FROM cached_posts WHERE id = :postId LIMIT 1")
    suspend fun getPostById(postId: Int): PostEntity?

    @Query("UPDATE cached_posts SET isSavedOffline = :saved WHERE id = :postId")
    suspend fun updateSavedOffline(postId: Int, saved: Boolean)

    @Query("UPDATE cached_posts SET commentsJson = :commentsJson, commentsCount = :commentsCount WHERE id = :postId")
    suspend fun updateCommentsAndCount(postId: Int, commentsJson: String, commentsCount: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(posts: List<PostEntity>)

    @Query("DELETE FROM cached_posts WHERE id = :postId")
    suspend fun deletePost(postId: Int)

    @Query("DELETE FROM cached_posts")
    suspend fun clearAll()
}
