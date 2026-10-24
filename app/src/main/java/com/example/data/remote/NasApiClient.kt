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
import okhttp3.RequestBody.Companion.asRequestBody
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

            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body) else response.message
                return@withContext Result.failure(Exception("HTTP ${response.code}: $detail"))
            }

            val jsonArray = parseJsonArraySafely(body)
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

            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body) else response.message
                return@withContext Result.failure(Exception("HTTP ${response.code}: $detail"))
            }

            val jsonObject = parseJsonObjectSafely(body)
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
                            durationLabel = p.optString("duration_label").takeIf { it.isNotBlank() }
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

                val mediaRequestBody = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
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
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body) else response.message
                return@withContext Result.failure(Exception("HTTP ${response.code}: $detail"))
            }

            val json = parseJsonObjectSafely(body)
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
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body) else response.message
                return@withContext Result.failure(Exception("HTTP ${response.code}: $detail"))
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
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body) else response.message
                return@withContext Result.failure(Exception("HTTP ${response.code}: $detail"))
            }

            val commentId = parseJsonObjectSafely(body).optInt("comment_id", 0)
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
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body) else response.message
                return@withContext Result.failure(Exception("HTTP ${response.code}: $detail"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseJsonObjectSafely(rawBody: String?): JSONObject {
        val body = rawBody?.trim() ?: "{}"
        if (body.isEmpty()) return JSONObject()
        if (body.startsWith("<")) {
            throw Exception(extractHtmlOrPhpError(body))
        }
        return try {
            val json = JSONObject(body)
            if (json.has("error")) {
                throw Exception(json.optString("error"))
            }
            if (json.optString("status").equals("ERROR", ignoreCase = true)) {
                throw Exception(json.optString("message", "Error del servidor Synology"))
            }
            json
        } catch (e: Exception) {
            if (e.message?.startsWith("Acceso") == true || e.message?.startsWith("Conexión") == true ||
                e.message?.startsWith("Error") == true || e.message?.startsWith("La base") == true ||
                e.message?.startsWith("Faltan") == true
            ) {
                throw e
            }
            throw Exception(extractHtmlOrPhpError(body))
        }
    }

    private fun parseJsonArraySafely(rawBody: String?): JSONArray {
        val body = rawBody?.trim() ?: "[]"
        if (body.isEmpty()) return JSONArray()
        if (body.startsWith("<")) {
            throw Exception(extractHtmlOrPhpError(body))
        }
        return try {
            JSONArray(body)
        } catch (e: Exception) {
            try {
                val json = JSONObject(body)
                if (json.has("error")) throw Exception(json.optString("error"))
                if (json.has("message")) throw Exception(json.optString("message"))
            } catch (ex: Exception) {
                if (ex.message?.startsWith("Acceso") == true || ex.message?.startsWith("Conexión") == true) {
                    throw ex
                }
            }
            throw Exception(extractHtmlOrPhpError(body))
        }
    }

    private fun extractHtmlOrPhpError(raw: String): String {
        if (raw.contains("Access denied for user", ignoreCase = true) || raw.contains("1045", ignoreCase = true)) {
            return "Acceso denegado a MariaDB. Verifica el usuario y la contraseña de la base de datos en los ajustes."
        }
        if (raw.contains("Connection refused", ignoreCase = true) || raw.contains("2002", ignoreCase = true)) {
            return "Conexión rechazada por MariaDB. Verifica el puerto de la base de datos (ej. 3307 o 3306) y que esté activo en DSM."
        }
        if (raw.contains("Unknown database", ignoreCase = true) || raw.contains("1049", ignoreCase = true)) {
            return "La base de datos 'family_feed' no existe en MariaDB. Ejecuta el archivo 'schema.sql' en phpMyAdmin."
        }
        if (raw.contains("Table", ignoreCase = true) && raw.contains("doesn't exist", ignoreCase = true)) {
            return "Faltan tablas en MariaDB. Ejecuta el archivo 'schema.sql' en phpMyAdmin."
        }

        val cleaned = raw.replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace(Regex("\\s+"), " ")
            .trim()

        return if (cleaned.isNotBlank()) {
            "Error en Synology: ${cleaned.take(160)}"
        } else {
            "El servidor Synology devolvió una respuesta con formato inválido (HTML en vez de JSON)."
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
