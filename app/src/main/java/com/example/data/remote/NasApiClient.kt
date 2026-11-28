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
import com.example.model.FeedPageResult
import com.example.model.GroupOrganizationMode
import com.example.model.MediaItem
import com.example.model.MediaType
import com.example.model.Post
import com.example.model.ReactionType
import com.example.model.NasScanResult
import com.example.model.NasStorageVolume
import com.example.model.NasSyncGroupResult
import com.example.model.ScannedFolder
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

    // Cliente ágil para paginación del feed y evitar bloqueos largos en scroll
    private val feedClient: OkHttpClient = client.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

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

            if (response.code == 404 || response.code == 405) {
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

            if (response.code == 404 || response.code == 405) {
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
            val expectedTablesCount = authObj?.optInt("expected_tables_count", 9) ?: 9

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

            if (response.code == 404 || response.code == 405) {
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

            if (response.code == 404 || response.code == 405) {
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

            if (response.code == 404 || response.code == 405) {
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

            if (response.code == 404 || response.code == 405) {
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
     * Obtiene una página del feed (10 publicaciones por defecto) desde Synology Web Station.
     */
    suspend fun fetchFeedPage(
        ip: String,
        port: Int,
        path: String,
        limit: Int = 10,
        offset: Int = 0,
        userId: Int? = null,
        onlyFollowed: Boolean = false
    ): Result<FeedPageResult> = withContext(Dispatchers.IO) {
        try {
            var currentPath = path
            val pageParams = buildString {
                append("limit=$limit&offset=$offset")
                if (userId != null && userId > 0) {
                    append("&user_id=$userId&only_followed=${if (onlyFollowed) 1 else 0}")
                }
            }
            var url = buildEndpointUrl(ip, port, currentPath, "feed", pageParams)
            var response = feedClient.newCall(Request.Builder().url(url).get().build()).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404 || response.code == 405) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "feed", pageParams)
                    response = feedClient.newCall(Request.Builder().url(url).get().build()).execute()
                    body = response.body?.string() ?: ""
                }
            }

            if (!response.isSuccessful) {
                if (body.contains("doesn't exist", ignoreCase = true) || body.contains("Base table", ignoreCase = true) || body.contains("Faltan tablas", ignoreCase = true)) {
                    val repairResult = repairDatabase(ip, port, currentPath)
                    if (repairResult.isSuccess) {
                        url = buildEndpointUrl(ip, port, currentPath, "feed", pageParams)
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

            val totalCount = jsonObject.optInt("total_count", offset + postsList.size)
            val nextOffset = jsonObject.optInt("next_offset", offset + postsList.size)
            val hasMore = if (jsonObject.has("has_more")) {
                jsonObject.optBoolean("has_more", false)
            } else {
                postsList.size >= limit && (offset + postsList.size) < totalCount
            }

            Result.success(
                FeedPageResult(
                    posts = postsList,
                    hasMore = hasMore,
                    totalCount = totalCount,
                    nextOffset = nextOffset
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Obtiene las primeras publicaciones del feed desde el backend PHP en Synology Web Station.
     */
    suspend fun fetchFeed(ip: String, port: Int, path: String): Result<List<Post>> {
        return fetchFeedPage(ip, port, path, limit = 10, offset = 0).map { it.posts }
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

            if (response.code == 404 || response.code == 405) {
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

    private fun resolveMediaUrl(rawPath: String, baseUrl: String): String {
        val trimmed = rawPath.trim()
        if (trimmed.isBlank()) return ""
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("index.php") -> "$baseUrl/$trimmed"
            trimmed.startsWith("/") -> "$baseUrl$trimmed"
            else -> "$baseUrl/$trimmed"
        }
    }

    private fun parseChunksAndDurations(
        obj: JSONObject,
        baseUrl: String
    ): Triple<List<String>, List<Long>, Long> {
        val metaArray = obj.optJSONArray("chunks_meta")
        val parsedChunks = mutableListOf<String>()
        val parsedDurations = mutableListOf<Long>()

        if (metaArray != null && metaArray.length() > 0) {
            val indexed = mutableListOf<Triple<Int, String, Long>>()
            for (idx in 0 until metaArray.length()) {
                val item = metaArray.optJSONObject(idx) ?: continue
                val cPath = item.optString("file_path", "")
                if (cPath.isNotBlank()) {
                    indexed.add(
                        Triple(
                            item.optInt("chunk_index", idx),
                            resolveMediaUrl(cPath, baseUrl),
                            item.optLong("duration_ms", 0L).coerceAtLeast(0L)
                        )
                    )
                }
            }
            indexed.sortedBy { it.first }.forEach { (_, url, dur) ->
                parsedChunks.add(url)
                parsedDurations.add(dur)
            }
        }

        if (parsedChunks.isEmpty()) {
            val rawChunks = obj.optString("chunks_paths", "")
            if (rawChunks.isNotBlank()) {
                rawChunks.split("|||").filter { it.isNotBlank() }.forEach { relPath ->
                    parsedChunks.add(resolveMediaUrl(relPath, baseUrl))
                }
            } else {
                val chunksJsonArr = obj.optJSONArray("chunks")
                if (chunksJsonArr != null) {
                    for (idx in 0 until chunksJsonArr.length()) {
                        val relPath = chunksJsonArr.optString(idx, "")
                        if (relPath.isNotBlank()) {
                            parsedChunks.add(resolveMediaUrl(relPath, baseUrl))
                        }
                    }
                }
            }
        }

        val totalMs = obj.optLong("total_duration_ms", 0L).takeIf { it > 0L }
            ?: parsedDurations.sum().takeIf { it > 0L }
            ?: 0L

        return Triple(parsedChunks, parsedDurations, totalMs)
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
        val mediaItemsArray = p.optJSONArray("media_items")
        if (mediaItemsArray != null && mediaItemsArray.length() > 0) {
            for (mIdx in 0 until mediaItemsArray.length()) {
                val mObj = mediaItemsArray.optJSONObject(mIdx) ?: continue
                val filePath = mObj.optString("file_path", "")
                if (filePath.isBlank()) continue
                val fullMediaUrl = resolveMediaUrl(filePath, baseUrl)
                val mediaTypeStr = mObj.optString("media_type", "IMAGE")
                val isVideoItem = mediaTypeStr.equals("VIDEO", ignoreCase = true)
                val rawThumb = mObj.optString("thumb_path", "")
                val fullThumbUrl = if (isVideoItem) {
                    if (rawThumb.isBlank()) "" else resolveMediaUrl(rawThumb, baseUrl)
                } else {
                    resolveMediaUrl(rawThumb.ifBlank { filePath }, baseUrl)
                }
                val (chunksList, chunkDurations, totalDurationMs) = parseChunksAndDurations(mObj, baseUrl)
                val isChunked = mObj.optInt("is_chunked", 0) == 1 || chunksList.isNotEmpty()

                mediaItems.add(
                    MediaItem(
                        id = "m_${postId}_${mObj.optInt("id", mIdx)}",
                        type = if (isVideoItem) MediaType.VIDEO else MediaType.IMAGE,
                        url = fullMediaUrl,
                        thumbnailUrl = fullThumbUrl,
                        durationLabel = mObj.optString("duration_label").takeIf { it.isNotBlank() },
                        isChunked = isChunked,
                        chunks = chunksList,
                        chunkDurationsMs = chunkDurations,
                        totalDurationMs = totalDurationMs
                    )
                )
            }
        }

        if (mediaItems.isEmpty()) {
            val filePath = p.optString("file_path", "")
            if (filePath.isNotBlank()) {
                val fullMediaUrl = resolveMediaUrl(filePath, baseUrl)
                val mediaTypeStr = p.optString("media_type", "IMAGE")
                val isVideoItem = mediaTypeStr.equals("VIDEO", ignoreCase = true)
                val rawThumb = p.optString("thumb_path", "")
                val fullThumbUrl = if (isVideoItem) {
                    if (rawThumb.isBlank()) "" else resolveMediaUrl(rawThumb, baseUrl)
                } else {
                    resolveMediaUrl(rawThumb.ifBlank { filePath }, baseUrl)
                }
                val (chunksList, chunkDurations, totalDurationMs) = parseChunksAndDurations(p, baseUrl)
                val isChunked = p.optInt("is_chunked", 0) == 1 || chunksList.isNotEmpty()

                mediaItems.add(
                    MediaItem(
                        id = "m_$postId",
                        type = if (isVideoItem) MediaType.VIDEO else MediaType.IMAGE,
                        url = fullMediaUrl,
                        thumbnailUrl = fullThumbUrl,
                        durationLabel = p.optString("duration_label").takeIf { it.isNotBlank() },
                        isChunked = isChunked,
                        chunks = chunksList,
                        chunkDurationsMs = chunkDurations,
                        totalDurationMs = totalDurationMs
                    )
                )
            }
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
                            avatarColorHex = cObj.optLong("avatar_color_hex", 0xFF1E40AF),
                            username = cObj.optString("username", "")
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

            if (response.code == 404 || response.code == 405) {
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
                groupsList.add(parseGroupFromJson(g, baseUrl))
            }
            Result.success(groupsList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseGroupFromJson(g: JSONObject, baseUrl: String): FamilyGroup {
        val rawCover = g.optString("cover_photo_url", "")
        val fullCover = if (rawCover.isNotBlank()) {
            resolveMediaUrl(rawCover, baseUrl)
        } else null
        val orgStr = g.optString("organization_mode", "NORMAL")
        val orgMode = if (orgStr.equals("SERIES", ignoreCase = true)) {
            GroupOrganizationMode.SERIES
        } else {
            GroupOrganizationMode.NORMAL
        }
        val allowVal = g.optInt("allow_member_uploads", 1) == 1
        return FamilyGroup(
            id = g.getInt("id"),
            name = g.optString("name", "Grupo"),
            folderPath = g.optString("folder_path", ""),
            description = g.optString("description", ""),
            coverPhotoUrl = fullCover,
            postsCount = g.optInt("posts_count", 0),
            createdAt = parseTimestamp(g.optString("created_at")),
            createdByUserId = g.optInt("created_by_user_id", 1),
            allowUploads = allowVal,
            organizationMode = orgMode
        )
    }

    /**
     * Crea un nuevo grupo familiar administrado por el usuario actual.
     */
    suspend fun createGroup(
        ip: String,
        port: Int,
        path: String,
        context: Context,
        userId: Int,
        name: String,
        description: String,
        allowUploads: Boolean,
        organizationMode: GroupOrganizationMode,
        coverUri: Uri? = null
    ): Result<FamilyGroup> = withContext(Dispatchers.IO) {
        try {
            var currentPath = path
            var url = buildEndpointUrl(ip, port, currentPath, "group/create")
            val multipartBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("user_id", userId.toString())
                .addFormDataPart("name", name.trim())
                .addFormDataPart("description", description.trim())
                .addFormDataPart("allow_member_uploads", if (allowUploads) "1" else "0")
                .addFormDataPart("organization_mode", organizationMode.value)

            var tempCoverFile: File? = null
            if (coverUri != null) {
                try {
                    val mimeType = context.contentResolver.getType(coverUri) ?: "image/jpeg"
                    tempCoverFile = File(context.cacheDir, "group_cover_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(coverUri)?.use { input ->
                        FileOutputStream(tempCoverFile).use { output -> input.copyTo(output) }
                    }
                    if (tempCoverFile.exists() && tempCoverFile.length() > 0L) {
                        multipartBuilder.addFormDataPart(
                            "cover",
                            tempCoverFile.name,
                            tempCoverFile.asRequestBody(mimeType.toMediaTypeOrNull())
                        )
                    }
                } catch (_: Exception) {}
            }

            val bodyPayload = multipartBuilder.build()
            var response = uploadClient.newCall(Request.Builder().url(url).post(bodyPayload).build()).execute()
            var respBody = response.body?.string() ?: ""

            if (response.code == 404 || response.code == 405) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "group/create")
                    response = uploadClient.newCall(Request.Builder().url(url).post(bodyPayload).build()).execute()
                    respBody = response.body?.string() ?: ""
                }
            }
            tempCoverFile?.delete()

            if (!response.isSuccessful) {
                val detail = if (respBody.isNotBlank()) extractHtmlOrPhpError(respBody, response.code, currentPath) else response.message
                return@withContext Result.failure(Exception("HTTP ${response.code}: $detail"))
            }

            val json = parseJsonObjectSafely(respBody)
            val gObj = json.getJSONObject("group")
            val baseUrl = buildBaseUrl(ip, port, currentPath)
            Result.success(parseGroupFromJson(gObj, baseUrl))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Actualiza configuración de grupo: nombre, descripción, portada, permiso de subida y organización (Normal/Series).
     */
    suspend fun updateGroup(
        ip: String,
        port: Int,
        path: String,
        context: Context,
        groupId: Int,
        userId: Int,
        name: String? = null,
        description: String? = null,
        allowUploads: Boolean? = null,
        organizationMode: GroupOrganizationMode? = null,
        coverUri: Uri? = null,
        coverPhotoUrl: String? = null
    ): Result<FamilyGroup> = withContext(Dispatchers.IO) {
        try {
            var currentPath = path
            var url = buildEndpointUrl(ip, port, currentPath, "group/update")
            val multipartBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("group_id", groupId.toString())
                .addFormDataPart("user_id", userId.toString())

            if (!name.isNullOrBlank()) multipartBuilder.addFormDataPart("name", name.trim())
            if (description != null) multipartBuilder.addFormDataPart("description", description.trim())
            if (allowUploads != null) multipartBuilder.addFormDataPart("allow_member_uploads", if (allowUploads) "1" else "0")
            if (organizationMode != null) multipartBuilder.addFormDataPart("organization_mode", organizationMode.value)
            if (!coverPhotoUrl.isNullOrBlank() && coverUri == null) {
                multipartBuilder.addFormDataPart("cover_photo_url", coverPhotoUrl.trim())
            }

            var tempCoverFile: File? = null
            if (coverUri != null) {
                try {
                    val mimeType = context.contentResolver.getType(coverUri) ?: "image/jpeg"
                    tempCoverFile = File(context.cacheDir, "group_cover_upd_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(coverUri)?.use { input ->
                        FileOutputStream(tempCoverFile).use { output -> input.copyTo(output) }
                    }
                    if (tempCoverFile.exists() && tempCoverFile.length() > 0L) {
                        multipartBuilder.addFormDataPart(
                            "cover",
                            tempCoverFile.name,
                            tempCoverFile.asRequestBody(mimeType.toMediaTypeOrNull())
                        )
                    }
                } catch (_: Exception) {}
            }

            val bodyPayload = multipartBuilder.build()
            var response = uploadClient.newCall(Request.Builder().url(url).post(bodyPayload).build()).execute()
            var respBody = response.body?.string() ?: ""

            if (response.code == 404 || response.code == 405) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "group/update")
                    response = uploadClient.newCall(Request.Builder().url(url).post(bodyPayload).build()).execute()
                    respBody = response.body?.string() ?: ""
                }
            }
            tempCoverFile?.delete()

            if (!response.isSuccessful) {
                val detail = if (respBody.isNotBlank()) extractHtmlOrPhpError(respBody, response.code, currentPath) else response.message
                return@withContext Result.failure(Exception("HTTP ${response.code}: $detail"))
            }

            val json = parseJsonObjectSafely(respBody)
            val gObj = json.getJSONObject("group")
            val baseUrl = buildBaseUrl(ip, port, currentPath)
            Result.success(parseGroupFromJson(gObj, baseUrl))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Elimina un grupo familiar en el servidor NAS.
     */
    suspend fun deleteGroup(
        ip: String,
        port: Int,
        path: String,
        groupId: Int,
        userId: Int
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            var currentPath = path
            var url = buildEndpointUrl(ip, port, currentPath, "group/delete")
            val form = FormBody.Builder()
                .add("group_id", groupId.toString())
                .add("user_id", userId.toString())
                .build()
            var response = client.newCall(Request.Builder().url(url).post(form).build()).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404 || response.code == 405) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "group/delete")
                    response = client.newCall(Request.Builder().url(url).post(form).build()).execute()
                    body = response.body?.string() ?: ""
                }
            }

            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body, response.code, currentPath) else response.message
                return@withContext Result.failure(Exception("HTTP ${response.code}: $detail"))
            }
            parseJsonObjectSafely(body)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Obtiene una página de publicaciones asociadas a un grupo familiar específico (10 en 10 por defecto).
     */
    suspend fun fetchGroupPostsPage(
        ip: String,
        port: Int,
        path: String,
        groupId: Int,
        limit: Int = 10,
        offset: Int = 0
    ): Result<FeedPageResult> = withContext(Dispatchers.IO) {
        try {
            var currentPath = path
            val params = "group_id=$groupId&limit=$limit&offset=$offset"
            var url = buildEndpointUrl(ip, port, currentPath, "group/posts", params)
            var response = client.newCall(Request.Builder().url(url).get().build()).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404 || response.code == 405) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "group/posts", params)
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

            val totalCount = jsonObject.optInt("total_count", offset + postsList.size)
            val nextOffset = jsonObject.optInt("next_offset", offset + postsList.size)
            val hasMore = if (jsonObject.has("has_more")) {
                jsonObject.optBoolean("has_more", false)
            } else {
                postsList.size >= limit && (offset + postsList.size) < totalCount
            }

            Result.success(
                FeedPageResult(
                    posts = postsList,
                    hasMore = hasMore,
                    totalCount = totalCount,
                    nextOffset = nextOffset
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchGroupPosts(ip: String, port: Int, path: String, groupId: Int): Result<List<Post>> {
        return fetchGroupPostsPage(ip, port, path, groupId, limit = 10, offset = 0).map { it.posts }
    }

    /**
     * Búsqueda ligera en toda la base de datos MariaDB 10 (global o acotada a un grupo).
     */
    suspend fun searchPosts(
        ip: String,
        port: Int,
        path: String,
        query: String,
        groupId: Int? = null
    ): Result<List<Post>> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext Result.success(emptyList())
        try {
            var currentPath = path
            val encodedQ = java.net.URLEncoder.encode(cleanQuery, "UTF-8")
            val params = buildString {
                append("q=$encodedQ")
                if (groupId != null && groupId > 0) {
                    append("&group_id=$groupId")
                }
            }
            var url = buildEndpointUrl(ip, port, currentPath, "search", params)
            var response = client.newCall(Request.Builder().url(url).get().build()).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404 || response.code == 405) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "search", params)
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
     * Obtiene los IDs de usuarios y grupos seguidos por el usuario en MariaDB 10.
     */
    suspend fun fetchFollows(
        ip: String,
        port: Int,
        path: String,
        userId: Int
    ): Result<Pair<Set<Int>, Set<Int>>> = withContext(Dispatchers.IO) {
        try {
            var currentPath = path
            var url = buildEndpointUrl(ip, port, currentPath, "follows", "user_id=$userId")
            var response = client.newCall(Request.Builder().url(url).get().build()).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404 || response.code == 405) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "follows", "user_id=$userId")
                    response = client.newCall(Request.Builder().url(url).get().build()).execute()
                    body = response.body?.string() ?: ""
                }
            }

            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body, response.code, currentPath) else response.message
                return@withContext Result.failure(Exception("HTTP ${response.code}: $detail"))
            }

            val json = parseJsonObjectSafely(body)
            val uSet = mutableSetOf<Int>()
            val uArr = json.optJSONArray("followed_user_ids")
            if (uArr != null) {
                for (i in 0 until uArr.length()) uSet.add(uArr.optInt(i))
            }
            val gSet = mutableSetOf<Int>()
            val gArr = json.optJSONArray("followed_group_ids")
            if (gArr != null) {
                for (i in 0 until gArr.length()) gSet.add(gArr.optInt(i))
            }
            Result.success(Pair(uSet, gSet))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Alterna seguir/dejar de seguir a un usuario o grupo en MariaDB 10.
     */
    suspend fun toggleFollow(
        ip: String,
        port: Int,
        path: String,
        userId: Int,
        targetType: String,
        targetId: Int
    ): Result<Triple<Boolean, Set<Int>, Set<Int>>> = withContext(Dispatchers.IO) {
        try {
            var currentPath = path
            var url = buildEndpointUrl(ip, port, currentPath, "follow/toggle")
            val jsonBody = JSONObject().apply {
                put("user_id", userId)
                put("target_type", targetType)
                put("target_id", targetId)
            }
            val reqBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            var response = client.newCall(Request.Builder().url(url).post(reqBody).build()).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404 || response.code == 405) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "follow/toggle")
                    response = client.newCall(Request.Builder().url(url).post(reqBody).build()).execute()
                    body = response.body?.string() ?: ""
                }
            }

            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body, response.code, currentPath) else response.message
                return@withContext Result.failure(Exception("HTTP ${response.code}: $detail"))
            }

            val json = parseJsonObjectSafely(body)
            val following = json.optBoolean("following", false)
            val uSet = mutableSetOf<Int>()
            val uArr = json.optJSONArray("followed_user_ids")
            if (uArr != null) {
                for (i in 0 until uArr.length()) uSet.add(uArr.optInt(i))
            }
            val gSet = mutableSetOf<Int>()
            val gArr = json.optJSONArray("followed_group_ids")
            if (gArr != null) {
                for (i in 0 until gArr.length()) gSet.add(gArr.optInt(i))
            }
            Result.success(Triple(following, uSet, gSet))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sube al NAS la miniatura generada localmente cuando un usuario reproduce un vídeo.
     */
    suspend fun uploadVideoThumbnail(
        ip: String,
        port: Int,
        path: String,
        postId: Int,
        thumbFile: File
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!thumbFile.exists() || thumbFile.length() <= 0L) {
            return@withContext Result.failure(Exception("Archivo de miniatura vacío"))
        }
        try {
            var currentPath = path
            var url = buildEndpointUrl(ip, port, currentPath, "video/upload_thumb")
            val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("post_id", postId.toString())
                .addFormDataPart("thumbnail", thumbFile.name, thumbFile.asRequestBody("image/jpeg".toMediaTypeOrNull()))
            val req = Request.Builder().url(url).post(bodyBuilder.build()).build()
            var response = client.newCall(req).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404 || response.code == 405) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "video/upload_thumb")
                    response = client.newCall(Request.Builder().url(url).post(bodyBuilder.build()).build()).execute()
                    body = response.body?.string() ?: ""
                }
            }
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val json = parseJsonObjectSafely(body)
            Result.success(json.optString("thumb_path", ""))
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

            if (response.code == 404 || response.code == 405) {
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
    suspend fun scanNasFolders(ip: String, port: Int, path: String, basePath: String): Result<NasScanResult> = withContext(Dispatchers.IO) {
        try {
            var currentPath = path
            val encodedBase = java.net.URLEncoder.encode(basePath, "UTF-8")
            var url = buildEndpointUrl(ip, port, currentPath, "admin/scan_folders", "base_path=$encodedBase")
            var response = client.newCall(Request.Builder().url(url).get().build()).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404 || response.code == 405) {
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
            val resolvedBase = jsonObject.optString("base_path", basePath).trimEnd('/').ifBlank { "/" }
            val rawParent = jsonObject.optString("parent_path", "").trim()
            val computedParent = if (rawParent.isNotBlank()) {
                rawParent
            } else {
                val trimmed = resolvedBase.trimEnd('/')
                val slashIdx = trimmed.lastIndexOf('/')
                if (slashIdx > 0) trimmed.substring(0, slashIdx) else null
            }

            val foldersArray = jsonObject.optJSONArray("folders") ?: JSONArray()
            val foldersList = mutableListOf<ScannedFolder>()

            for (i in 0 until foldersArray.length()) {
                val f = foldersArray.getJSONObject(i)
                val subCount = f.optInt("subfolders_count", if (f.optBoolean("has_subfolders", true)) 1 else 0)
                foldersList.add(
                    ScannedFolder(
                        folderName = f.optString("name", "Carpeta"),
                        folderPath = f.optString("path", ""),
                        mediaCount = f.optInt("media_count", 0),
                        photosCount = f.optInt("photos_count", 0),
                        videosCount = f.optInt("videos_count", 0),
                        subfoldersCount = subCount,
                        hasSubfolders = f.optBoolean("has_subfolders", subCount > 0),
                        isAlreadyImported = f.optBoolean("already_imported", false),
                        isSelected = false
                    )
                )
            }
            Result.success(
                NasScanResult(
                    basePath = resolvedBase,
                    parentPath = computedParent,
                    folders = foldersList
                )
            )
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
        batchLimit: Int = 50,
        allowUploads: Boolean = true,
        organizationMode: GroupOrganizationMode = GroupOrganizationMode.NORMAL
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
                put("allow_member_uploads", if (allowUploads) 1 else 0)
                put("organization_mode", organizationMode.value)
            }

            val reqBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            var response = client.newCall(Request.Builder().url(url).post(reqBody).build()).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404 || response.code == 405) {
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
        allowUploads: Boolean = true,
        organizationMode: GroupOrganizationMode = GroupOrganizationMode.NORMAL,
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
            val batchRes = importNasGroupsBatch(
                ip = ip,
                port = port,
                path = path,
                folderPaths = folderPaths,
                adminUserId = adminUserId,
                batchOffset = currentOffset,
                batchLimit = 50,
                allowUploads = allowUploads,
                organizationMode = organizationMode
            )
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

            if (response.code == 404 || response.code == 405) {
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

            if (response.code == 404 || response.code == 405) {
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
        mediaUri: Uri? = null,
        mediaUris: List<Uri> = emptyList(),
        thumbnailFile: File? = null,
        durationLabel: String? = null,
        isShort: Boolean = false,
        groupId: Int? = null
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
            groupId?.let { multipartBuilder.addFormDataPart("group_id", it.toString()) }

            // Si hay miniatura extraída explícita para vídeos, enviarla
            if (thumbnailFile != null && thumbnailFile.exists()) {
                val thumbBody = thumbnailFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                multipartBuilder.addFormDataPart("thumbnail", thumbnailFile.name, thumbBody)
            }

            // Procesar todos los archivos multimedia seleccionados (uno o múltiples)
            val allUris = if (mediaUris.isNotEmpty()) mediaUris else listOfNotNull(mediaUri)
            for ((idx, uri) in allUris.withIndex()) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val isVideo = mimeType.startsWith("video")
                    val extension = if (isVideo) "mp4" else "jpg"

                    val tempFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}_$idx.$extension")
                    inputStream?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    val mediaRequestBody = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
                    val partKey = if (allUris.size == 1 && idx == 0) "photo" else "photo_$idx"
                    multipartBuilder.addFormDataPart(
                        partKey,
                        tempFile.name,
                        mediaRequestBody
                    )
                } catch (eFile: Exception) {
                    eFile.printStackTrace()
                }
            }

            val request = Request.Builder()
                .url(url)
                .post(multipartBuilder.build())
                .build()

            var response = uploadClient.newCall(request).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404 || response.code == 405) {
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
            if (!chunkFile.exists() || chunkFile.length() <= 0L) {
                return@withContext Result.failure(Exception("El fragmento #$chunkIndex está vacío o no existe"))
            }

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

            if (response.code == 404 || response.code == 405) {
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

            val json = parseJsonObjectSafely(body)
            if (json.has("error")) {
                return@withContext Result.failure(Exception(json.optString("error", "Error subiendo fragmento")))
            }
            if (json.has("received") && !json.optBoolean("received", true)) {
                return@withContext Result.failure(Exception("El NAS no confirmó la recepción del fragmento #$chunkIndex"))
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
        thumbnailFile: File?,
        chunkDurationsMs: List<Long> = emptyList(),
        totalDurationMs: Long = 0L
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

            if (chunkDurationsMs.isNotEmpty()) {
                val arr = JSONArray()
                chunkDurationsMs.forEach { arr.put(it) }
                multipartBuilder.addFormDataPart("chunk_durations", arr.toString())
            }
            if (totalDurationMs > 0L) {
                multipartBuilder.addFormDataPart("total_duration_ms", totalDurationMs.toString())
            }

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

            if (response.code == 404 || response.code == 405) {
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
            if (json.has("error")) {
                return@withContext Result.failure(Exception(json.optString("error", "Error finalizando vídeo en NAS")))
            }
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

            if (response.code == 404 || response.code == 405) {
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

            if (response.code == 404 || response.code == 405) {
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

            if (response.code == 404 || response.code == 405) {
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

    /**
     * Edita el texto, sentimiento, ubicación o grupo de una publicación existente en MariaDB 10.
     */
    suspend fun updatePost(
        ip: String,
        port: Int,
        path: String,
        postId: Int,
        userId: Int,
        contentText: String,
        feeling: String?,
        location: String?,
        groupId: Int?
    ): Result<Post> = withContext(Dispatchers.IO) {
        try {
            var currentPath = path
            var url = buildEndpointUrl(ip, port, currentPath, "posts/update")
            val jsonBody = JSONObject().apply {
                put("post_id", postId)
                put("user_id", userId)
                put("content_text", contentText.trim())
                put("feeling", feeling?.trim() ?: "")
                put("location", location?.trim() ?: "")
                if (groupId != null && groupId > 0) {
                    put("group_id", groupId)
                } else {
                    put("group_id", JSONObject.NULL)
                }
            }
            val reqBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            var response = client.newCall(Request.Builder().url(url).post(reqBody).build()).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404 || response.code == 405) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "posts/update")
                    response = client.newCall(Request.Builder().url(url).post(reqBody).build()).execute()
                    body = response.body?.string() ?: ""
                }
            }

            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body, response.code, currentPath) else response.message
                return@withContext Result.failure(Exception("HTTP ${response.code}: $detail"))
            }

            val jsonObject = parseJsonObjectSafely(body)
            val postObj = jsonObject.optJSONObject("post")
                ?: return@withContext Result.failure(Exception("Respuesta sin datos actualizados"))
            val baseUrl = buildBaseUrl(ip, port, currentPath)
            Result.success(parsePostFromJson(postObj, baseUrl))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Re-escanea incrementalmente la carpeta de un grupo importado en el NAS, añadiendo archivos nuevos
     * y opcionalmente limpiando referencias huérfanas.
     */
    suspend fun syncNasGroup(
        ip: String,
        port: Int,
        path: String,
        groupId: Int,
        userId: Int,
        removeMissing: Boolean = false
    ): Result<NasSyncGroupResult> = withContext(Dispatchers.IO) {
        try {
            var currentPath = path
            var url = buildEndpointUrl(ip, port, currentPath, "admin/sync_group")
            val jsonBody = JSONObject().apply {
                put("group_id", groupId)
                put("user_id", userId)
                put("remove_missing", if (removeMissing) 1 else 0)
            }
            val reqBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            var response = uploadClient.newCall(Request.Builder().url(url).post(reqBody).build()).execute()
            var body = response.body?.string() ?: ""

            if (response.code == 404 || response.code == 405) {
                val detected = probeNasServer(ip, port, currentPath).getOrNull()
                if (detected != null && detected != currentPath) {
                    currentPath = detected
                    url = buildEndpointUrl(ip, port, currentPath, "admin/sync_group")
                    response = uploadClient.newCall(Request.Builder().url(url).post(reqBody).build()).execute()
                    body = response.body?.string() ?: ""
                }
            }

            if (!response.isSuccessful) {
                val detail = if (body.isNotBlank()) extractHtmlOrPhpError(body, response.code, currentPath) else response.message
                return@withContext Result.failure(Exception("HTTP ${response.code}: $detail"))
            }

            val json = parseJsonObjectSafely(body)
            Result.success(
                NasSyncGroupResult(
                    groupId = json.optInt("group_id", groupId),
                    addedPosts = json.optInt("added_posts", 0),
                    addedFiles = json.optInt("added_files", 0),
                    totalFiles = json.optInt("total_files", 0),
                    removedMissing = json.optInt("removed_missing", 0)
                )
            )
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
        if (statusCode == 405 || raw.contains("405 Not Allowed", ignoreCase = true)) {
            val pDesc = if (path.isNotBlank()) " en '$path'" else ""
            return "Error HTTP 405 en Nginx$pDesc: Web Station rechazó la petición en esa ruta. Pulsa 'Probar' para autodetectar la ruta raíz de tu portal web o verifica que index.php esté en la raíz de Web Station."
        }
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
