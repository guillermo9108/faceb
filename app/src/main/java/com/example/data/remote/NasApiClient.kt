package com.example.data.remote

import android.content.Context
import android.net.Uri
import com.example.data.AppErrorLogger
import com.example.model.AppErrorLog
import com.example.model.CheckItem
import com.example.model.Comment
import com.example.model.DatabaseDiagnosticReport
import com.example.model.ErrorCategory
import com.example.model.FamilyGroup
import com.example.model.FamilyMember
import com.example.model.MediaItem
import com.example.model.MediaType
import com.example.model.Post
import com.example.model.ReactionType
import com.example.model.ScannedFolder
import com.example.model.NasStorageVolume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
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
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    // Cliente especializado sin límites de tiempo de espera (timeout) para subida de vídeos pesados en LAN
    private val uploadClient: OkHttpClient = client.newBuilder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    var onPathAutoDetected: ((String) -> Unit)? = null

    private fun buildBaseUrl(ip: String, port: Int, path: String): String {
        val cleanPath = path.trim().trimEnd('/')
        return if (port == 80) "http://$ip$cleanPath" else "http://$ip:$port$cleanPath"
    }

    private fun buildEndpointUrl(ip: String, port: Int, path: String, action: String, extraParams: String? = null): String {
        val base = buildBaseUrl(ip, port, path)
        val query = if (extraParams.isNullOrBlank()) "action=$action" else "action=$action&$extraParams"
        return "$base/index.php?$query"
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
     * Prueba en red LAN para verificar si el Synology NAS y los scripts PHP están en la IP y puerto dados.
     * Retorna la ruta válida (ej: "/family_api" o "") si index.php responde positivamente.
     */
    suspend fun probeNasServer(ip: String, port: Int = 80, path: String = "/family_api"): Result<String> = withContext(Dispatchers.IO) {
        val cleanPath = path.trim().trimEnd('/')
        val testPaths = listOf(cleanPath, "/family_api", "", "/family_feed", "/api").distinct()
        val fastClient = client.newBuilder()
            .connectTimeout(1000, TimeUnit.MILLISECONDS)
            .readTimeout(1500, TimeUnit.MILLISECONDS)
            .build()

        for (p in testPaths) {
            // 1. Probar con parámetro ?action=ping (compatible 100% con Synology Nginx y Apache)
            val urlParam = if (port == 80) "http://$ip$p/index.php?action=ping" else "http://$ip:$port$p/index.php?action=ping"
            try {
                val req = Request.Builder().url(urlParam).get().build()
                val resp = fastClient.newCall(req).execute()
                val body = resp.body?.string() ?: ""
                if (resp.isSuccessful || body.contains("ONLINE") || body.contains("Synology") || body.contains("server") || body.contains("family_feed")) {
                    if (p != cleanPath) {
                        onPathAutoDetected?.invoke(p)
                    }
                    return@withContext Result.success(p)
                }
            } catch (_: Exception) {}

            // 2. Probar si responde index.php directo
            val urlDirect = if (port == 80) "http://$ip$p/index.php" else "http://$ip:$port$p/index.php"
            try {
                val req = Request.Builder().url(urlDirect).get().build()
                val resp = fastClient.newCall(req).execute()
                val body = resp.body?.string() ?: ""
                if (resp.isSuccessful || body.contains("ONLINE") || body.contains("Synology") || body.contains("server") || body.contains("Endpoint no encontrado") || body.contains("family_feed")) {
                    if (p != cleanPath) {
                        onPathAutoDetected?.invoke(p)
                    }
                    return@withContext Result.success(p)
                }
            } catch (_: Exception) {}
        }
        Result.failure(Exception("No responde Synology NAS en $ip:$port"))
    }

    /**
     * Prueba simultánea en puertos 80 y 8080 para localizar automáticamente el servidor Synology / Web Station.
     */
    suspend fun probeNasServerOnPorts(ip: String, ports: List<Int> = listOf(80, 8080), path: String = "/family_api"): Pair<Int, String>? {
        for (port in ports) {
            val res = probeNasServer(ip, port, path)
            if (res.isSuccess) {
                return Pair(port, res.getOrNull() ?: path)
            }
        }
        return null
    }

    /**
     * Solicita la verificación y reparación/creación automática de todas las tablas en MariaDB (family_feed)
     * ejecutando los DDL y migraciones pendientes en el Synology NAS.
     */
    suspend fun repairDatabase(ip: String, port: Int, path: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            var currentPath = path
            var url = buildEndpointUrl(ip, port, currentPath, "repair_db")
            var response = client.newCall(Request.Builder().url(url).get().build()).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "repair_db")
                    response = client.newCall(Request.Builder().url(url).get().build()).execute()
                    body = response.body?.string() ?: ""
                }
            }

            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body, response.code, currentPath) else response.message
                return@withContext Result.failure(Exception("HTTP ${response.code}: $detail"))
            }

            val json = parseJsonObjectSafely(body)
            val msg = json.optString("message", "Tablas y base de datos verificadas y reparadas correctamente.")
            Result.success(msg)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Ejecuta un diagnóstico exhaustivo en vivo en el servidor Synology NAS comprobando extensiones PHP,
     * conectividad TCP en puerto 3307, sockets UNIX de MariaDB 10, autenticación y tablas.
     */
    suspend fun getDatabaseDiagnostics(ip: String, port: Int, path: String): Result<DatabaseDiagnosticReport> = withContext(Dispatchers.IO) {
        try {
            var currentPath = path
            var url = buildEndpointUrl(ip, port, currentPath, "db_diagnostics")
            var response = client.newCall(Request.Builder().url(url).get().build()).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "db_diagnostics")
                    response = client.newCall(Request.Builder().url(url).get().build()).execute()
                    body = response.body?.string() ?: ""
                }
            }

            if (!response.isSuccessful && body.isBlank()) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            val json = parseJsonObjectSafely(body)
            val status = json.optString("status", if (response.isSuccessful) "OK" else "ERROR")
            val timestamp = json.optString("timestamp", "")
            val phpVer = json.optString("php_version", "")
            val recommendation = json.optString("recommendation", "")

            val cfg = json.optJSONObject("config")
            val dbHost = cfg?.optString("db_host", "127.0.0.1") ?: "127.0.0.1"
            val dbPort = cfg?.optInt("db_port", 3307) ?: 3307
            val dbName = cfg?.optString("db_name", "family_feed") ?: "family_feed"
            val dbUser = cfg?.optString("db_user", "root") ?: "root"
            val dbPassIsDefault = cfg?.optBoolean("db_pass_is_default", false) ?: false

            val checks = json.optJSONObject("checks")

            val extObj = checks?.optJSONObject("php_extensions")
            val extItem = CheckItem(
                success = extObj?.optBoolean("success", false) ?: false,
                message = extObj?.optString("message", "Verificación de extensiones PHP") ?: "Verificación de extensiones PHP",
                detail = "PDO: ${extObj?.optBoolean("pdo", false)}, pdo_mysql: ${extObj?.optBoolean("pdo_mysql", false)}"
            )

            val tcpObj = checks?.optJSONObject("tcp_connection")
            val tcpItem = CheckItem(
                success = tcpObj?.optBoolean("success", false) ?: false,
                message = tcpObj?.optString("message", "Conexión TCP a MariaDB") ?: "Conexión TCP a MariaDB",
                detail = if (tcpObj != null && tcpObj.has("error")) tcpObj.optString("error") else null
            )

            val sockObj = checks?.optJSONObject("unix_sockets")
            val sockItem = CheckItem(
                success = sockObj?.optBoolean("success", false) ?: false,
                message = sockObj?.optString("message", "Verificación de sockets UNIX") ?: "Verificación de sockets UNIX",
                detail = null
            )

            val authObj = checks?.optJSONObject("mariadb_authentication")
            val authItem = CheckItem(
                success = authObj?.optBoolean("success", false) ?: false,
                message = authObj?.optString("message", "Autenticación MariaDB 10") ?: "Autenticación MariaDB 10",
                detail = if (authObj != null && authObj.has("error")) authObj.optString("error") else null
            )

            val dbExists = authObj?.optBoolean("database_exists", false) ?: false
            val tablesCount = authObj?.optInt("tables_count", 0) ?: 0
            val expectedTablesCount = authObj?.optInt("expected_tables_count", 8) ?: 8

            val tablesList = mutableListOf<String>()
            val tablesArray = authObj?.optJSONArray("tables")
            if (tablesArray != null) {
                for (ti in 0 until tablesArray.length()) {
                    tablesList.add(tablesArray.optString(ti))
                }
            }

            val missingList = mutableListOf<String>()
            val missingArray = authObj?.optJSONArray("missing_tables")
            if (missingArray != null) {
                for (mi in 0 until missingArray.length()) {
                    missingList.add(missingArray.optString(mi))
                }
            }

            val report = DatabaseDiagnosticReport(
                status = status,
                timestamp = timestamp,
                phpVersion = phpVer,
                dbHost = dbHost,
                dbPort = dbPort,
                dbName = dbName,
                dbUser = dbUser,
                dbPassIsDefault = dbPassIsDefault,
                pdoMysqlExtension = extItem,
                tcpConnection = tcpItem,
                unixSockets = sockItem,
                mariadbAuthentication = authItem,
                databaseExists = dbExists,
                tablesCount = tablesCount,
                expectedTablesCount = expectedTablesCount,
                tables = tablesList,
                missingTables = missingList,
                recommendation = recommendation,
                rawJson = body
            )

            Result.success(report)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Obtiene los usuarios reales configurados en MariaDB 10.
     */
    suspend fun fetchUsers(ip: String, port: Int, path: String): Result<List<FamilyMember>> = withContext(Dispatchers.IO) {
        try {
            var currentPath = path
            var url = buildEndpointUrl(ip, port, currentPath, "users")
            var response = client.newCall(Request.Builder().url(url).get().build()).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "users")
                    response = client.newCall(Request.Builder().url(url).get().build()).execute()
                    body = response.body?.string() ?: ""
                }
            }

            if (!response.isSuccessful) {
                if (body.contains("doesn't exist", ignoreCase = true) || body.contains("Base table", ignoreCase = true) || body.contains("Faltan tablas", ignoreCase = true)) {
                    val repairResult = repairDatabase(ip, port, currentPath)
                    if (repairResult.isSuccess) {
                        url = buildEndpointUrl(ip, port, currentPath, "users")
                        response = client.newCall(Request.Builder().url(url).get().build()).execute()
                        body = response.body?.string() ?: ""
                    }
                }
            }

            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body, response.code, currentPath) else response.message
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
                        bio = obj.optString("bio", ""),
                        username = obj.optString("username", "")
                    )
                )
            }
            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Registro de nuevo usuario familiar en MariaDB 10.
     */
    suspend fun register(
        ip: String,
        port: Int,
        path: String,
        username: String,
        pass: String,
        displayName: String,
        role: String,
        avatarEmoji: String,
        avatarColorHex: Long
    ): Result<FamilyMember> = withContext(Dispatchers.IO) {
        try {
            var currentPath = path
            var url = buildEndpointUrl(ip, port, currentPath, "auth/register")
            val formBody = FormBody.Builder()
                .add("username", username)
                .add("password", pass)
                .add("display_name", displayName)
                .add("role", role)
                .add("avatar_emoji", avatarEmoji)
                .add("avatar_color_hex", avatarColorHex.toString())
                .build()

            var response = client.newCall(Request.Builder().url(url).post(formBody).build()).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "auth/register")
                    response = client.newCall(Request.Builder().url(url).post(formBody).build()).execute()
                    body = response.body?.string() ?: ""
                }
            }

            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body, response.code, currentPath) else response.message
                return@withContext Result.failure(Exception(detail))
            }

            val json = parseJsonObjectSafely(body)
            val uObj = json.getJSONObject("user")
            val member = FamilyMember(
                id = uObj.getInt("id"),
                name = uObj.getString("display_name"),
                role = uObj.optString("role", "Familiar"),
                avatarEmoji = uObj.optString("avatar_emoji", "👤"),
                avatarColorHex = uObj.optLong("avatar_color_hex", 0xFF1E40AF),
                pin = "",
                bio = uObj.optString("bio", ""),
                username = uObj.optString("username", username)
            )
            Result.success(member)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Inicio de sesión de usuario familiar en MariaDB 10.
     */
    suspend fun login(
        ip: String,
        port: Int,
        path: String,
        username: String,
        pass: String
    ): Result<FamilyMember> = withContext(Dispatchers.IO) {
        try {
            var currentPath = path
            var url = buildEndpointUrl(ip, port, currentPath, "auth/login")
            val formBody = FormBody.Builder()
                .add("username", username)
                .add("password", pass)
                .build()

            var response = client.newCall(Request.Builder().url(url).post(formBody).build()).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "auth/login")
                    response = client.newCall(Request.Builder().url(url).post(formBody).build()).execute()
                    body = response.body?.string() ?: ""
                }
            }

            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body, response.code, currentPath) else response.message
                return@withContext Result.failure(Exception(detail))
            }

            val json = parseJsonObjectSafely(body)
            val uObj = json.getJSONObject("user")
            val member = FamilyMember(
                id = uObj.getInt("id"),
                name = uObj.getString("display_name"),
                role = uObj.optString("role", "Familiar"),
                avatarEmoji = uObj.optString("avatar_emoji", "👤"),
                avatarColorHex = uObj.optLong("avatar_color_hex", 0xFF1E40AF),
                pin = "",
                bio = uObj.optString("bio", ""),
                username = uObj.optString("username", username)
            )
            Result.success(member)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Actualiza nick, avatar, bio y/o contraseña en MariaDB 10.
     */
    suspend fun updateProfile(
        ip: String,
        port: Int,
        path: String,
        userId: Int,
        displayName: String,
        avatarEmoji: String,
        avatarColorHex: Long,
        bio: String,
        newPassword: String?
    ): Result<FamilyMember> = withContext(Dispatchers.IO) {
        try {
            var currentPath = path
            var url = buildEndpointUrl(ip, port, currentPath, "profile/update")
            val formBuilder = FormBody.Builder()
                .add("user_id", userId.toString())
                .add("display_name", displayName)
                .add("avatar_emoji", avatarEmoji)
                .add("avatar_color_hex", avatarColorHex.toString())
                .add("bio", bio)

            if (!newPassword.isNullOrBlank()) {
                formBuilder.add("new_password", newPassword)
            }

            var response = client.newCall(Request.Builder().url(url).post(formBuilder.build()).build()).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "profile/update")
                    response = client.newCall(Request.Builder().url(url).post(formBuilder.build()).build()).execute()
                    body = response.body?.string() ?: ""
                }
            }

            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body, response.code, currentPath) else response.message
                return@withContext Result.failure(Exception(detail))
            }

            val json = parseJsonObjectSafely(body)
            val uObj = json.getJSONObject("user")
            val member = FamilyMember(
                id = uObj.getInt("id"),
                name = uObj.getString("display_name"),
                role = uObj.optString("role", "Familiar"),
                avatarEmoji = uObj.optString("avatar_emoji", "👤"),
                avatarColorHex = uObj.optLong("avatar_color_hex", 0xFF1E40AF),
                pin = "",
                bio = uObj.optString("bio", ""),
                username = uObj.optString("username", "")
            )
            Result.success(member)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Obtiene el feed real de publicaciones desde el backend PHP en Synology Web Station.
     */
    suspend fun fetchFeed(ip: String, port: Int, path: String): Result<List<Post>> = withContext(Dispatchers.IO) {
        try {
            var currentPath = path
            var url = buildEndpointUrl(ip, port, currentPath, "feed")
            var response = client.newCall(Request.Builder().url(url).get().build()).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "feed")
                    response = client.newCall(Request.Builder().url(url).get().build()).execute()
                    body = response.body?.string() ?: ""
                }
            }

            if (!response.isSuccessful) {
                if (body.contains("doesn't exist", ignoreCase = true) || body.contains("Base table", ignoreCase = true) || body.contains("Faltan tablas", ignoreCase = true)) {
                    val repairResult = repairDatabase(ip, port, currentPath)
                    if (repairResult.isSuccess) {
                        url = buildEndpointUrl(ip, port, currentPath, "feed")
                        response = client.newCall(Request.Builder().url(url).get().build()).execute()
                        body = response.body?.string() ?: ""
                    }
                }
            }

            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body, response.code, currentPath) else response.message
                return@withContext Result.failure(Exception("HTTP ${response.code}: $detail"))
            }

            val jsonObject = parseJsonObjectSafely(body)
            val postsArray = jsonObject.optJSONArray("posts") ?: JSONArray()
            val postsList = mutableListOf<Post>()
            val baseUrl = buildBaseUrl(ip, port, currentPath)

            for (i in 0 until postsArray.length()) {
                val p = postsArray.getJSONObject(i)
                postsList.add(parsePostFromJson(p, baseUrl))
            }

            Result.success(postsList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Obtiene los Shorts (vídeos verticales y publicaciones marcadas como Short).
     */
    suspend fun fetchShorts(ip: String, port: Int, path: String): Result<List<Post>> = withContext(Dispatchers.IO) {
        try {
            var currentPath = path
            var url = buildEndpointUrl(ip, port, currentPath, "shorts")
            var response = client.newCall(Request.Builder().url(url).get().build()).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "shorts")
                    response = client.newCall(Request.Builder().url(url).get().build()).execute()
                    body = response.body?.string() ?: ""
                }
            }

            if (!response.isSuccessful) {
                if (body.contains("doesn't exist", ignoreCase = true) || body.contains("Base table", ignoreCase = true) || body.contains("Faltan tablas", ignoreCase = true)) {
                    val repairResult = repairDatabase(ip, port, currentPath)
                    if (repairResult.isSuccess) {
                        url = buildEndpointUrl(ip, port, currentPath, "shorts")
                        response = client.newCall(Request.Builder().url(url).get().build()).execute()
                        body = response.body?.string() ?: ""
                    }
                }
            }

            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body, response.code, currentPath) else response.message
                return@withContext Result.failure(Exception("HTTP ${response.code}: $detail"))
            }

            val jsonObject = parseJsonObjectSafely(body)
            val postsArray = jsonObject.optJSONArray("shorts") ?: JSONArray()
            val postsList = mutableListOf<Post>()
            val baseUrl = buildBaseUrl(ip, port, currentPath)

            for (i in 0 until postsArray.length()) {
                val p = postsArray.getJSONObject(i)
                postsList.add(parsePostFromJson(p, baseUrl))
            }

            Result.success(postsList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parsePostFromJson(p: JSONObject, baseUrl: String): Post {
        val postId = p.optInt("id", (1..99999).random())
        val author = FamilyMember(
            id = p.optInt("user_id", 1),
            name = p.optString("display_name", "Familiar"),
            role = p.optString("role", "Familiar"),
            avatarEmoji = p.optString("avatar_emoji", "👤"),
            avatarColorHex = p.optLong("avatar_color_hex", 0xFF1E40AF),
            username = p.optString("username", "")
        )

        val mediaItems = mutableListOf<MediaItem>()
        val filePath = p.optString("file_path", "")
        if (filePath.isNotBlank()) {
            val fullMediaUrl = when {
                filePath.startsWith("http://") || filePath.startsWith("https://") -> filePath
                filePath.startsWith("index.php") -> "$baseUrl/$filePath"
                filePath.startsWith("/") -> "$baseUrl$filePath"
                else -> "$baseUrl/$filePath"
            }
            val thumbPath = p.optString("thumb_path", filePath)
            val fullThumbUrl = when {
                thumbPath.startsWith("http://") || thumbPath.startsWith("https://") -> thumbPath
                thumbPath.startsWith("index.php") -> "$baseUrl/$thumbPath"
                thumbPath.startsWith("/") -> "$baseUrl$thumbPath"
                else -> "$baseUrl/$thumbPath"
            }
            val mediaTypeStr = p.optString("media_type", "IMAGE")
            val isChunked = p.optInt("is_chunked", 0) == 1
            val rawChunks = p.optString("chunks_paths", "")
            val chunksList = if (rawChunks.isNotBlank()) {
                rawChunks.split("|||").map { relPath ->
                    when {
                        relPath.startsWith("http://") || relPath.startsWith("https://") -> relPath
                        relPath.startsWith("index.php") -> "$baseUrl/$relPath"
                        relPath.startsWith("/") -> "$baseUrl$relPath"
                        else -> "$baseUrl/$relPath"
                    }
                }
            } else emptyList()

            mediaItems.add(
                MediaItem(
                    id = "m_$postId",
                    type = if (mediaTypeStr.equals("VIDEO", ignoreCase = true)) MediaType.VIDEO else MediaType.IMAGE,
                    url = fullMediaUrl,
                    thumbnailUrl = fullThumbUrl,
                    durationLabel = p.optString("duration_label").takeIf { it.isNotBlank() },
                    isChunked = isChunked,
                    chunks = chunksList
                )
            )
        }

        val likesCount = p.optInt("likes_count", 0)
        val reactionsMap = if (likesCount > 0) mapOf(ReactionType.LIKE to likesCount) else emptyMap()

        val commentsList = mutableListOf<Comment>()
        val commentsArray = p.optJSONArray("comments")
        if (commentsArray != null) {
            for (cIdx in 0 until commentsArray.length()) {
                val cObj = commentsArray.getJSONObject(cIdx)
                commentsList.add(
                    Comment(
                        id = cObj.optInt("id", (1..99999).random()),
                        postId = postId,
                        author = FamilyMember(
                            id = cObj.optInt("user_id", 1),
                            name = cObj.optString("display_name", "Familiar"),
                            role = cObj.optString("role", "Familiar"),
                            avatarEmoji = cObj.optString("avatar_emoji", "👤"),
                            avatarColorHex = cObj.optLong("avatar_color_hex", 0xFF1E40AF)
                        ),
                        text = cObj.optString("comment_text", ""),
                        createdAt = parseTimestamp(cObj.optString("created_at"))
                    )
                )
            }
        }

        val groupId = if (p.has("group_id") && !p.isNull("group_id")) p.optInt("group_id") else null
        val groupName = p.optString("group_name").takeIf { it.isNotBlank() }

        return Post(
            id = postId,
            author = author,
            contentText = p.optString("content_text", ""),
            createdAt = parseTimestamp(p.optString("created_at")),
            mediaItems = mediaItems,
            reactions = reactionsMap,
            comments = commentsList,
            feeling = p.optString("feeling").takeIf { it.isNotBlank() },
            location = p.optString("location").takeIf { it.isNotBlank() },
            isPinned = p.optInt("is_pinned", 0) == 1,
            isShort = p.optInt("is_short", 0) == 1,
            groupId = groupId,
            groupName = groupName
        )
    }

    /**
     * Obtiene la lista de grupos familiares desde MariaDB en el NAS.
     */
    suspend fun fetchGroups(ip: String, port: Int, path: String): Result<List<FamilyGroup>> = withContext(Dispatchers.IO) {
        try {
            var currentPath = path
            var url = buildEndpointUrl(ip, port, currentPath, "groups")
            var response = client.newCall(Request.Builder().url(url).get().build()).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "groups")
                    response = client.newCall(Request.Builder().url(url).get().build()).execute()
                    body = response.body?.string() ?: ""
                }
            }

            if (!response.isSuccessful) {
                if (body.contains("doesn't exist", ignoreCase = true) || body.contains("Base table", ignoreCase = true) || body.contains("Faltan tablas", ignoreCase = true)) {
                    val repairResult = repairDatabase(ip, port, currentPath)
                    if (repairResult.isSuccess) {
                        url = buildEndpointUrl(ip, port, currentPath, "groups")
                        response = client.newCall(Request.Builder().url(url).get().build()).execute()
                        body = response.body?.string() ?: ""
                    }
                }
            }

            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body, response.code, currentPath) else response.message
                return@withContext Result.failure(Exception("HTTP ${response.code}: $detail"))
            }

            val jsonObject = parseJsonObjectSafely(body)
            val groupsArray = jsonObject.optJSONArray("groups") ?: JSONArray()
            val groupsList = mutableListOf<FamilyGroup>()
            val baseUrl = buildBaseUrl(ip, port, currentPath)

            for (i in 0 until groupsArray.length()) {
                val g = groupsArray.getJSONObject(i)
                val rawCover = g.optString("cover_photo_url", "")
                val fullCover = if (rawCover.isNotBlank()) {
                    if (rawCover.startsWith("http://") || rawCover.startsWith("https://")) rawCover
                    else "$baseUrl/$rawCover"
                } else null

                groupsList.add(
                    FamilyGroup(
                        id = g.getInt("id"),
                        name = g.optString("name", "Grupo"),
                        folderPath = g.optString("folder_path", ""),
                        description = g.optString("description", ""),
                        coverPhotoUrl = fullCover,
                        postsCount = g.optInt("posts_count", 0),
                        createdAt = parseTimestamp(g.optString("created_at"))
                    )
                )
            }
            Result.success(groupsList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Obtiene las publicaciones asociadas a un grupo familiar específico.
     */
    suspend fun fetchGroupPosts(ip: String, port: Int, path: String, groupId: Int): Result<List<Post>> = withContext(Dispatchers.IO) {
        try {
            var currentPath = path
            var url = buildEndpointUrl(ip, port, currentPath, "group/posts", "group_id=$groupId")
            var response = client.newCall(Request.Builder().url(url).get().build()).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "group/posts", "group_id=$groupId")
                    response = client.newCall(Request.Builder().url(url).get().build()).execute()
                    body = response.body?.string() ?: ""
                }
            }

            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body, response.code, currentPath) else response.message
                return@withContext Result.failure(Exception("HTTP ${response.code}: $detail"))
            }

            val jsonObject = parseJsonObjectSafely(body)
            val postsArray = jsonObject.optJSONArray("posts") ?: JSONArray()
            val postsList = mutableListOf<Post>()
            val baseUrl = buildBaseUrl(ip, port, currentPath)

            for (i in 0 until postsArray.length()) {
                val p = postsArray.getJSONObject(i)
                postsList.add(parsePostFromJson(p, baseUrl))
            }
            Result.success(postsList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Detecta discos y volúmenes de almacenamiento en el servidor Synology NAS (/volume1, /volume2, /volume3...).
     */
    suspend fun detectStorageVolumes(ip: String, port: Int, path: String): Result<List<NasStorageVolume>> = withContext(Dispatchers.IO) {
        try {
            var currentPath = path
            var url = buildEndpointUrl(ip, port, currentPath, "admin/detect_storage")
            var response = client.newCall(Request.Builder().url(url).get().build()).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "admin/detect_storage")
                    response = client.newCall(Request.Builder().url(url).get().build()).execute()
                    body = response.body?.string() ?: ""
                }
            }

            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body, response.code, currentPath) else response.message
                return@withContext Result.failure(Exception("HTTP ${response.code}: $detail"))
            }

            val json = parseJsonObjectSafely(body)
            val volArray = json.optJSONArray("volumes") ?: JSONArray()
            val list = mutableListOf<NasStorageVolume>()
            for (i in 0 until volArray.length()) {
                val vo = volArray.optJSONObject(i) ?: continue
                val mediaArr = vo.optJSONArray("media_folders") ?: JSONArray()
                val mediaList = mutableListOf<String>()
                for (j in 0 until mediaArr.length()) {
                    mediaList.add(mediaArr.optString(j))
                }
                list.add(
                    NasStorageVolume(
                        volumeName = vo.optString("volume", "volume3"),
                        path = vo.optString("path", "/volume3"),
                        totalGb = vo.optDouble("total_gb", 0.0),
                        freeGb = vo.optDouble("free_gb", 0.0),
                        isCurrentServerVolume = vo.optBoolean("is_current_server_volume", false),
                        mediaFolders = mediaList
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Escanea carpetas con contenido multimedia en el almacenamiento del NAS (solo Administrador).
     */
    suspend fun scanNasFolders(ip: String, port: Int, path: String, basePath: String): Result<List<ScannedFolder>> = withContext(Dispatchers.IO) {
        try {
            var currentPath = path
            val encodedBase = java.net.URLEncoder.encode(basePath, "UTF-8")
            var url = buildEndpointUrl(ip, port, currentPath, "admin/scan_folders", "base_path=$encodedBase")
            var response = client.newCall(Request.Builder().url(url).get().build()).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "admin/scan_folders", "base_path=$encodedBase")
                    response = client.newCall(Request.Builder().url(url).get().build()).execute()
                    body = response.body?.string() ?: ""
                }
            }

            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body, response.code, currentPath) else response.message
                return@withContext Result.failure(Exception("HTTP ${response.code}: $detail"))
            }

            val jsonObject = parseJsonObjectSafely(body)
            val foldersArray = jsonObject.optJSONArray("folders") ?: JSONArray()
            val foldersList = mutableListOf<ScannedFolder>()

            for (i in 0 until foldersArray.length()) {
                val f = foldersArray.getJSONObject(i)
                foldersList.add(
                    ScannedFolder(
                        folderName = f.optString("name", "Carpeta"),
                        folderPath = f.optString("path", ""),
                        mediaCount = f.optInt("media_count", 0),
                        photosCount = f.optInt("photos_count", 0),
                        videosCount = f.optInt("videos_count", 0),
                        isAlreadyImported = f.optBoolean("already_imported", false),
                        isSelected = false
                    )
                )
            }
            Result.success(foldersList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    data class NasBatchImportResponse(
        val importedGroups: Int,
        val importedPosts: Int,
        val batchOffset: Int,
        val batchImported: Int,
        val totalFiles: Int,
        val hasMore: Boolean,
        val nextOffset: Int
    )

    suspend fun importNasGroupsBatch(
        ip: String,
        port: Int,
        path: String,
        folderPaths: List<String>,
        adminUserId: Int,
        batchOffset: Int,
        batchLimit: Int = 50
    ): Result<NasBatchImportResponse> = withContext(Dispatchers.IO) {
        try {
            var currentPath = path
            var url = buildEndpointUrl(ip, port, currentPath, "admin/import_groups")
            val jsonBody = JSONObject().apply {
                put("admin_user_id", adminUserId)
                put("folder_paths", JSONArray(folderPaths))
                put("batch_offset", batchOffset)
                put("batch_limit", batchLimit)
                put("is_batch", 1)
            }

            val reqBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            var response = client.newCall(Request.Builder().url(url).post(reqBody).build()).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "admin/import_groups")
                    response = client.newCall(Request.Builder().url(url).post(reqBody).build()).execute()
                    body = response.body?.string() ?: ""
                }
            }

            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body, response.code, currentPath) else response.message
                return@withContext Result.failure(Exception("HTTP ${response.code}: $detail"))
            }

            val jsonObject = parseJsonObjectSafely(body)
            val res = NasBatchImportResponse(
                importedGroups = jsonObject.optInt("imported_groups", folderPaths.size),
                importedPosts = jsonObject.optInt("imported_posts", 0),
                batchOffset = jsonObject.optInt("batch_offset", batchOffset),
                batchImported = jsonObject.optInt("batch_imported", 0),
                totalFiles = jsonObject.optInt("total_files", 0),
                hasMore = jsonObject.optBoolean("has_more", false),
                nextOffset = jsonObject.optInt("next_offset", batchOffset)
            )
            Result.success(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Importa las carpetas seleccionadas del NAS como grupos y publica progresivamente sus fotos/vídeos en segundo plano.
     * Soporta miles de archivos en carpetas y subcarpetas sin caídas ni timeouts por HTTP.
     */
    suspend fun importNasGroups(
        ip: String,
        port: Int,
        path: String,
        folderPaths: List<String>,
        adminUserId: Int,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        var currentOffset = 0
        var totalProcessed = 0
        var totalFiles = 0
        var totalGroups = folderPaths.size
        var keepGoing = true
        var batchCount = 0

        while (keepGoing && batchCount < 200) { // Límite de seguridad de 200 lotes (10,000 archivos)
            batchCount++
            val batchRes = importNasGroupsBatch(ip, port, path, folderPaths, adminUserId, currentOffset, 50)
            if (batchRes.isFailure) {
                return@withContext if (totalProcessed > 0) {
                    Result.success(totalGroups)
                } else {
                    Result.failure(batchRes.exceptionOrNull() ?: Exception("Error en lote $batchCount"))
                }
            }
            val data = batchRes.getOrThrow()
            totalGroups = data.importedGroups
            totalProcessed += data.batchImported
            totalFiles = data.totalFiles
            onProgress?.invoke(totalProcessed, totalFiles)

            if (!data.hasMore || data.nextOffset <= currentOffset || data.batchImported == 0) {
                keepGoing = false
            } else {
                currentOffset = data.nextOffset
            }
        }
        Result.success(totalGroups)
    }

    /**
     * Obtiene los comentarios en tiempo real para una publicación específica de MariaDB 10.
     */
    suspend fun fetchComments(ip: String, port: Int, path: String, postId: Int): Result<List<Comment>> = withContext(Dispatchers.IO) {
        try {
            var currentPath = path
            var url = buildEndpointUrl(ip, port, currentPath, "comments", "post_id=$postId")
            var response = client.newCall(Request.Builder().url(url).get().build()).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "comments", "post_id=$postId")
                    response = client.newCall(Request.Builder().url(url).get().build()).execute()
                    body = response.body?.string() ?: ""
                }
            }

            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body, response.code, currentPath) else response.message
                return@withContext Result.failure(Exception("HTTP ${response.code}: $detail"))
            }

            val jsonObject = parseJsonObjectSafely(body)
            val commentsArray = jsonObject.optJSONArray("comments") ?: JSONArray()
            val commentsList = mutableListOf<Comment>()

            for (i in 0 until commentsArray.length()) {
                val c = commentsArray.getJSONObject(i)
                commentsList.add(
                    Comment(
                        id = c.getInt("id"),
                        postId = postId,
                        author = FamilyMember(
                            id = c.getInt("user_id"),
                            name = c.optString("display_name", "Familiar"),
                            role = c.optString("role", "Familiar"),
                            avatarEmoji = c.optString("avatar_emoji", "👤"),
                            avatarColorHex = c.optLong("avatar_color_hex", 0xFF1E40AF)
                        ),
                        text = c.optString("comment_text", ""),
                        createdAt = parseTimestamp(c.optString("created_at"))
                    )
                )
            }
            Result.success(commentsList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Obtiene las historias familiares independientes desde MariaDB 10 en el NAS.
     */
    suspend fun fetchStories(ip: String, port: Int, path: String): Result<List<com.example.model.FamilyStory>> = withContext(Dispatchers.IO) {
        try {
            var currentPath = path
            var url = buildEndpointUrl(ip, port, currentPath, "stories")
            var response = client.newCall(Request.Builder().url(url).get().build()).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "stories")
                    response = client.newCall(Request.Builder().url(url).get().build()).execute()
                    body = response.body?.string() ?: ""
                }
            }

            if (!response.isSuccessful) {
                if (body.contains("doesn't exist", ignoreCase = true) || body.contains("Base table", ignoreCase = true) || body.contains("Faltan tablas", ignoreCase = true)) {
                    val repairResult = repairDatabase(ip, port, currentPath)
                    if (repairResult.isSuccess) {
                        url = buildEndpointUrl(ip, port, currentPath, "stories")
                        response = client.newCall(Request.Builder().url(url).get().build()).execute()
                        body = response.body?.string() ?: ""
                    }
                }
            }

            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body, response.code, currentPath) else response.message
                return@withContext Result.failure(Exception("HTTP ${response.code}: $detail"))
            }

            val jsonObject = parseJsonObjectSafely(body)
            val storiesArray = jsonObject.optJSONArray("stories") ?: JSONArray()
            val storiesList = mutableListOf<com.example.model.FamilyStory>()
            val baseUrl = buildBaseUrl(ip, port, currentPath)

            for (i in 0 until storiesArray.length()) {
                val s = storiesArray.getJSONObject(i)
                val author = FamilyMember(
                    id = s.getInt("user_id"),
                    name = s.optString("display_name", "Familiar"),
                    role = s.optString("role", "Familiar"),
                    avatarEmoji = s.optString("avatar_emoji", "👤"),
                    avatarColorHex = s.optLong("avatar_color_hex", 0xFF1E40AF)
                )

                val filePath = s.optString("file_path", "")
                val fullUrl = if (filePath.isNotBlank()) "$baseUrl/$filePath" else ""
                val caption = s.optString("caption", "")
                val storyTypeStr = s.optString("story_type", "IMAGE")
                val storyType = when (storyTypeStr.uppercase()) {
                    "TEXT" -> com.example.model.StoryType.TEXT
                    "VIDEO" -> com.example.model.StoryType.VIDEO
                    else -> com.example.model.StoryType.IMAGE
                }

                storiesList.add(
                    com.example.model.FamilyStory(
                        id = "story_${s.getInt("id")}",
                        author = author,
                        title = caption,
                        imageUrl = fullUrl,
                        mediaUrl = fullUrl,
                        storyType = storyType,
                        caption = caption,
                        backgroundColorHex = s.optLong("bg_color_hex", 0xFF1E3A8A),
                        timestamp = parseTimestamp(s.optString("created_at"))
                    )
                )
            }
            Result.success(storiesList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sube una publicación real con archivo multimedia, miniatura y duración al Synology NAS.
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
        mediaUri: Uri?,
        thumbnailFile: File? = null,
        durationLabel: String? = null,
        isShort: Boolean = false
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var currentPath = path
            var url = buildEndpointUrl(ip, port, currentPath, "post")
            val multipartBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("user_id", userId.toString())
                .addFormDataPart("content_text", text)
                .addFormDataPart("is_short", if (isShort) "1" else "0")

            feeling?.let { multipartBuilder.addFormDataPart("feeling", it) }
            location?.let { multipartBuilder.addFormDataPart("location", it) }
            durationLabel?.let { multipartBuilder.addFormDataPart("duration_label", it) }

            // Si hay miniatura extraída explícita para vídeos, enviarla
            if (thumbnailFile != null && thumbnailFile.exists()) {
                val thumbBody = thumbnailFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                multipartBuilder.addFormDataPart("thumbnail", thumbnailFile.name, thumbBody)
            }

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

            var response = uploadClient.newCall(request).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "post")
                    val retryReq = Request.Builder().url(url).post(multipartBuilder.build()).build()
                    response = uploadClient.newCall(retryReq).execute()
                    body = response.body?.string() ?: ""
                }
            }

            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body, response.code, currentPath) else response.message
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
     * Sube un fragmento de vídeo individual a la sesión especificada en el Synology NAS.
     */
    suspend fun uploadVideoChunk(
        ip: String,
        port: Int,
        path: String,
        sessionId: String,
        chunkIndex: Int,
        totalChunks: Int,
        chunkFile: File
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            var currentPath = path
            var url = buildEndpointUrl(ip, port, currentPath, "video/upload_chunk")

            val fileBody = chunkFile.asRequestBody("video/mp4".toMediaTypeOrNull())
            val multipartBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("session_id", sessionId)
                .addFormDataPart("chunk_index", chunkIndex.toString())
                .addFormDataPart("total_chunks", totalChunks.toString())
                .addFormDataPart("chunk", chunkFile.name, fileBody)

            val request = Request.Builder()
                .url(url)
                .post(multipartBuilder.build())
                .build()

            var response = uploadClient.newCall(request).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "video/upload_chunk")
                    val retryReq = Request.Builder().url(url).post(multipartBuilder.build()).build()
                    response = uploadClient.newCall(retryReq).execute()
                    body = response.body?.string() ?: ""
                }
            }

            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body, response.code, currentPath) else response.message
                return@withContext Result.failure(Exception("HTTP ${response.code}: $detail"))
            }

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Finaliza la subida de todos los fragmentos y crea la publicación o Short en MariaDB 10.
     */
    suspend fun finalizeChunkedVideo(
        ip: String,
        port: Int,
        path: String,
        sessionId: String,
        userId: Int,
        text: String,
        feeling: String?,
        location: String?,
        durationLabel: String?,
        isShort: Boolean,
        groupId: Int?,
        totalChunks: Int,
        thumbnailFile: File?
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var currentPath = path
            var url = buildEndpointUrl(ip, port, currentPath, "video/finalize_chunks")

            val multipartBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("session_id", sessionId)
                .addFormDataPart("user_id", userId.toString())
                .addFormDataPart("content_text", text)
                .addFormDataPart("is_short", if (isShort) "1" else "0")
                .addFormDataPart("total_chunks", totalChunks.toString())

            feeling?.let { multipartBuilder.addFormDataPart("feeling", it) }
            location?.let { multipartBuilder.addFormDataPart("location", it) }
            durationLabel?.let { multipartBuilder.addFormDataPart("duration_label", it) }
            groupId?.let { multipartBuilder.addFormDataPart("group_id", it.toString()) }

            if (thumbnailFile != null && thumbnailFile.exists()) {
                val thumbBody = thumbnailFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                multipartBuilder.addFormDataPart("thumbnail", thumbnailFile.name, thumbBody)
            }

            val request = Request.Builder()
                .url(url)
                .post(multipartBuilder.build())
                .build()

            var response = uploadClient.newCall(request).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "video/finalize_chunks")
                    val retryReq = Request.Builder().url(url).post(multipartBuilder.build()).build()
                    response = uploadClient.newCall(retryReq).execute()
                    body = response.body?.string() ?: ""
                }
            }

            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body, response.code, currentPath) else response.message
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
     * Sube una historia familiar independiente a MariaDB 10 y al almacenamiento del NAS.
     */
    suspend fun createStory(
        ip: String,
        port: Int,
        path: String,
        context: Context,
        userId: Int,
        caption: String,
        storyType: String,
        bgColorHex: Long,
        mediaUri: Uri?
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var currentPath = path
            var url = buildEndpointUrl(ip, port, currentPath, "story")
            val multipartBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("user_id", userId.toString())
                .addFormDataPart("caption", caption)
                .addFormDataPart("story_type", storyType)
                .addFormDataPart("bg_color_hex", bgColorHex.toString())

            if (mediaUri != null) {
                val inputStream = context.contentResolver.openInputStream(mediaUri)
                val mimeType = context.contentResolver.getType(mediaUri) ?: "image/jpeg"
                val isVideo = mimeType.startsWith("video")
                val extension = if (isVideo) "mp4" else "jpg"

                val tempFile = File(context.cacheDir, "story_${System.currentTimeMillis()}.$extension")
                inputStream?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val mediaRequestBody = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
                multipartBuilder.addFormDataPart("photo", tempFile.name, mediaRequestBody)
            }

            val request = Request.Builder()
                .url(url)
                .post(multipartBuilder.build())
                .build()

            var response = uploadClient.newCall(request).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "story")
                    val retryReq = Request.Builder().url(url).post(multipartBuilder.build()).build()
                    response = uploadClient.newCall(retryReq).execute()
                    body = response.body?.string() ?: ""
                }
            }

            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body, response.code, currentPath) else response.message
                return@withContext Result.failure(Exception("HTTP ${response.code}: $detail"))
            }

            val json = parseJsonObjectSafely(body)
            val storyId = json.optInt("story_id", (1..99999).random())
            Result.success(storyId)
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
            var currentPath = path
            var url = buildEndpointUrl(ip, port, currentPath, "reaction")
            val json = JSONObject().apply {
                put("post_id", postId)
                put("user_id", userId)
                put("reaction_type", reactionType)
            }

            val request = Request.Builder()
                .url(url)
                .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            var response = client.newCall(request).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "reaction")
                    val retryReq = Request.Builder().url(url).post(json.toString().toRequestBody("application/json".toMediaTypeOrNull())).build()
                    response = client.newCall(retryReq).execute()
                    body = response.body?.string() ?: ""
                }
            }

            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body, response.code, currentPath) else response.message
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
            var currentPath = path
            var url = buildEndpointUrl(ip, port, currentPath, "comment")
            val json = JSONObject().apply {
                put("post_id", postId)
                put("user_id", userId)
                put("comment_text", text)
            }

            val request = Request.Builder()
                .url(url)
                .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            var response = client.newCall(request).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "comment")
                    val retryReq = Request.Builder().url(url).post(json.toString().toRequestBody("application/json".toMediaTypeOrNull())).build()
                    response = client.newCall(retryReq).execute()
                    body = response.body?.string() ?: ""
                }
            }

            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body, response.code, currentPath) else response.message
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
            var currentPath = path
            val formBody = FormBody.Builder()
                .add("action", "delete_post")
                .add("post_id", postId.toString())
                .build()
            var url = buildEndpointUrl(ip, port, currentPath, "delete_post", "post_id=$postId")
            var response = client.newCall(Request.Builder().url(url).post(formBody).build()).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404 || response.code == 405) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "delete_post", "post_id=$postId")
                    response = client.newCall(Request.Builder().url(url).post(formBody).build()).execute()
                    body = response.body?.string() ?: ""
                }
            }

            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body, response.code, currentPath) else response.message
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

    private fun extractHtmlOrPhpError(raw: String, statusCode: Int = 0, path: String = ""): String {
        if (statusCode == 404 || raw.contains("404 Not Found", ignoreCase = true) || raw.contains("File Not Found", ignoreCase = true)) {
            val pDesc = if (path.isNotBlank()) " en '$path'" else ""
            return "Error 404: No se encontró 'index.php'$pDesc en el Synology NAS. Comprueba que los archivos PHP estén en '/volume1/web' o '/volume1/web/family_api' de Web Station o pulsa 'Probar' para autodetectar la ruta."
        }
        if (raw.contains("Access denied for user", ignoreCase = true) || raw.contains("1045", ignoreCase = true)) {
            return "Acceso denegado a MariaDB. Verifica el usuario y la contraseña de la base de datos en los ajustes."
        }
        if (raw.contains("Connection refused", ignoreCase = true) || raw.contains("2002", ignoreCase = true)) {
            return "Conexión rechazada por MariaDB. Verifica el puerto de la base de datos (ej. 3307 o 3306) y que esté activo en DSM."
        }
        if (raw.contains("Unknown database", ignoreCase = true) || raw.contains("1049", ignoreCase = true)) {
            return "La base de datos 'family_feed' no existe en MariaDB. Se creará automáticamente al conectar o importa 'schema.sql'."
        }
        if (raw.contains("Table", ignoreCase = true) && raw.contains("doesn't exist", ignoreCase = true)) {
            return "Faltan tablas en MariaDB. Pulsa 'Reparar Tablas' para crearlas automáticamente o importa 'schema.sql'."
        }

        if (raw.contains("min-height:600px") || raw.contains("synoman") || raw.contains("Web Station")) {
            return if (statusCode == 404) {
                "Error 404: Web Station no encontró 'index.php'. Verifica que los archivos estén en '/volume1/web' o pulsa 'Probar'."
            } else {
                "Synology Web Station devolvió un error HTTP $statusCode. Comprueba que Web Station y PHP estén iniciados."
            }
        }

        val cleaned = raw.replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace(Regex("\\s+"), " ")
            .trim()

        val finalMsg = when {
            raw.contains("Access denied for user", ignoreCase = true) || raw.contains("1045", ignoreCase = true) ->
                "Acceso denegado a MariaDB. Verifica el usuario y la contraseña de la base de datos en los ajustes."
            raw.contains("Connection refused", ignoreCase = true) || raw.contains("2002", ignoreCase = true) ->
                "Conexión rechazada por MariaDB. Verifica el puerto de la base de datos (puerto 3307 por defecto en Synology MariaDB 10) y que esté activo en DSM."
            raw.contains("Unknown database", ignoreCase = true) || raw.contains("1049", ignoreCase = true) ->
                "La base de datos 'family_feed' no existe en MariaDB. Se creará automáticamente al conectar o importa 'schema.sql'."
            raw.contains("Table", ignoreCase = true) && raw.contains("doesn't exist", ignoreCase = true) ->
                "Faltan tablas en MariaDB. Pulsa 'Reparar Tablas' para crearlas automáticamente o importa 'schema.sql'."
            cleaned.isNotBlank() ->
                "Error en Synology: ${cleaned.take(160)}"
            else ->
                "El servidor Synology devolvió una respuesta con formato inválido (HTML en vez de JSON)."
        }

        AppErrorLogger.logFromHttp(
            endpoint = if (path.isNotBlank()) path else "Synology API",
            statusCode = statusCode,
            rawBody = raw,
            exceptionMsg = finalMsg
        )

        return finalMsg
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
