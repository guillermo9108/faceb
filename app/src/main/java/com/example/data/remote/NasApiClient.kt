package com.example.data.remote

import android.content.Context
import android.net.Uri
import com.example.model.Comment
import com.example.model.FamilyMember
import com.example.model.MediaItem
import com.example.model.MediaType
import com.example.model.Post
import com.example.model.ReactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

class NasApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    private fun buildBaseUrl(ip: String, port: Int, path: String): String {
        val cleanPath = path.trim().trimEnd('/')
        return "http://$ip:$port$cleanPath"
    }

    /**
     * Medición real de latencia LAN al Synology NAS abriendo un Socket TCP directo.
     */
    suspend fun pingNas(ip: String, port: Int): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), 2500)
            }
            val elapsed = System.currentTimeMillis() - startTime
            Result.success(elapsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Obtiene los usuarios reales configurados en MariaDB 10.
     */
    suspend fun fetchUsers(ip: String, port: Int, path: String): Result<List<FamilyMember>> = withContext(Dispatchers.IO) {
        try {
            val url = "${buildBaseUrl(ip, port, path)}/index.php/api/users"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            val body = response.body?.string() ?: "[]"
            val jsonArray = JSONArray(body)
            val users = mutableListOf<FamilyMember>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                users.add(
                    FamilyMember(
                        id = obj.getInt("id"),
                        name = obj.getString("display_name"),
                        role = obj.optString("role", "Familiar"),
                        avatarEmoji = obj.optString("avatar_emoji", "👤"),
                        avatarColorHex = obj.optLong("avatar_color_hex", 0xFF1E40AF),
                        pin = obj.optString("pin_hash", "1234"),
                        bio = obj.optString("bio", "")
                    )
                )
            }
            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Obtiene el feed real de publicaciones desde el backend PHP en Synology Web Station.
     */
    suspend fun fetchFeed(ip: String, port: Int, path: String): Result<List<Post>> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = buildBaseUrl(ip, port, path)
            val url = "$baseUrl/index.php/api/feed"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            val body = response.body?.string() ?: "{}"
            val jsonObject = JSONObject(body)
            val postsArray = jsonObject.optJSONArray("posts") ?: JSONArray()
            val postsList = mutableListOf<Post>()

            for (i in 0 until postsArray.length()) {
                val p = postsArray.getJSONObject(i)
                val postId = p.getInt("id")
                val author = FamilyMember(
                    id = p.getInt("user_id"),
                    name = p.optString("display_name", "Familiar"),
                    role = p.optString("role", "Familiar"),
                    avatarEmoji = p.optString("avatar_emoji", "👤"),
                    avatarColorHex = p.optLong("avatar_color_hex", 0xFF1E40AF)
                )

                val mediaItems = mutableListOf<MediaItem>()
                val filePath = p.optString("file_path", "")
                if (filePath.isNotBlank()) {
                    val fullMediaUrl = "$baseUrl/$filePath"
                    val thumbPath = p.optString("thumb_path", filePath)
                    val fullThumbUrl = "$baseUrl/$thumbPath"
                    val mediaTypeStr = p.optString("media_type", "IMAGE")

                    mediaItems.add(
                        MediaItem(
                            id = "m_$postId",
                            type = if (mediaTypeStr.equals("VIDEO", ignoreCase = true)) MediaType.VIDEO else MediaType.IMAGE,
                            url = fullMediaUrl,
                            thumbnailUrl = fullThumbUrl,
                            durationLabel = p.optString("duration_label", null)
                        )
                    )
                }

                val likesCount = p.optInt("likes_count", 0)
                val reactionsMap = if (likesCount > 0) mapOf(ReactionType.LIKE to likesCount) else emptyMap()

                postsList.add(
                    Post(
                        id = postId,
                        author = author,
                        contentText = p.optString("content_text", ""),
                        createdAt = parseTimestamp(p.optString("created_at")),
                        mediaItems = mediaItems,
                        reactions = reactionsMap,
                        feeling = p.optString("feeling").takeIf { it.isNotBlank() },
                        location = p.optString("location").takeIf { it.isNotBlank() },
                        isPinned = p.optInt("is_pinned", 0) == 1
                    )
                )
            }

            Result.success(postsList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sube una publicación real con archivo multimedia real al Synology NAS.
     */
    suspend fun createPost(
        ip: String,
        port: Int,
        path: String,
        context: Context,
        userId: Int,
        text: String,
        feeling: String?,
        location: String?,
        mediaUri: Uri?
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val url = "${buildBaseUrl(ip, port, path)}/index.php/api/post"
            val multipartBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("user_id", userId.toString())
                .addFormDataPart("content_text", text)

            feeling?.let { multipartBuilder.addFormDataPart("feeling", it) }
            location?.let { multipartBuilder.addFormDataPart("location", it) }

            // Si hay un archivo real seleccionado desde el dispositivo, leer los bytes y enviarlos
            if (mediaUri != null) {
                val inputStream = context.contentResolver.openInputStream(mediaUri)
                val mimeType = context.contentResolver.getType(mediaUri) ?: "image/jpeg"
                val isVideo = mimeType.startsWith("video")
                val extension = if (isVideo) "mp4" else "jpg"

                val tempFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}.$extension")
                inputStream?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val mediaRequestBody = RequestBody.create(
                    mimeType.toMediaTypeOrNull(),
                    tempFile
                )
                multipartBuilder.addFormDataPart(
                    "photo",
                    tempFile.name,
                    mediaRequestBody
                )
            }

            val request = Request.Builder()
                .url(url)
                .post(multipartBuilder.build())
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            val body = response.body?.string() ?: "{}"
            val json = JSONObject(body)
            val newPostId = json.optInt("post_id", (1..99999).random())
            Result.success(newPostId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Envía una reacción real a MariaDB 10.
     */
    suspend fun sendReaction(
        ip: String,
        port: Int,
        path: String,
        postId: Int,
        userId: Int,
        reactionType: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "${buildBaseUrl(ip, port, path)}/index.php/api/reaction"
            val json = JSONObject().apply {
                put("post_id", postId)
                put("user_id", userId)
                put("reaction_type", reactionType)
            }

            val request = Request.Builder()
                .url(url)
                .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Envía un comentario real a MariaDB 10.
     */
    suspend fun sendComment(
        ip: String,
        port: Int,
        path: String,
        postId: Int,
        userId: Int,
        text: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val url = "${buildBaseUrl(ip, port, path)}/index.php/api/comment"
            val json = JSONObject().apply {
                put("post_id", postId)
                put("user_id", userId)
                put("comment_text", text)
            }

            val request = Request.Builder()
                .url(url)
                .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val body = response.body?.string() ?: "{}"
            val commentId = JSONObject(body).optInt("comment_id", 0)
            Result.success(commentId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Elimina una publicación de MariaDB 10 y del almacenamiento en el NAS.
     */
    suspend fun deletePost(ip: String, port: Int, path: String, postId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "${buildBaseUrl(ip, port, path)}/index.php/api/post/$postId"
            val request = Request.Builder()
                .url(url)
                .delete()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseTimestamp(rawDate: String?): Long {
        if (rawDate.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            format.parse(rawDate)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
