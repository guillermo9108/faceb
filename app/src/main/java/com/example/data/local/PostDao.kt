package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {

    @Query("SELECT * FROM cached_posts ORDER BY isPinned DESC, createdAt DESC")
    fun getAllPosts(): Flow<List<PostEntity>>

    @Query("SELECT * FROM cached_posts WHERE isShort = 0 ORDER BY isPinned DESC, createdAt DESC")
    fun getFeedPosts(): Flow<List<PostEntity>>

    @Query("SELECT * FROM cached_posts WHERE isShort = 1 ORDER BY createdAt DESC")
    fun getShortsPosts(): Flow<List<PostEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(posts: List<PostEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: PostEntity)

    @Query("DELETE FROM cached_posts WHERE id = :postId")
    suspend fun deletePost(postId: Int)

    @Query("DELETE FROM cached_posts")
    suspend fun clearAll()
}
