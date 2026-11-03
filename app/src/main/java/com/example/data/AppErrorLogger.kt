package com.example.data

import android.content.Context
import com.example.model.AppErrorLog
import com.example.model.ErrorCategory
import com.example.model.NasConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppErrorLogger {

    private val _logs = MutableStateFlow<List<AppErrorLog>>(emptyList())
    val logs: StateFlow<List<AppErrorLog>> = _logs.asStateFlow()

    private var appContext: Context? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun initialize(context: Context) {
        appContext = context.applicationContext
        loadPersistedLogs()
    }

    @Synchronized
    fun log(
        category: ErrorCategory,
        title: String,
        message: String,
        endpoint: String? = null,
        httpCode: Int? = null,
        rawServerResponse: String? = null,
        throwable: Throwable? = null,
        suggestedFix: String? = null
    ) {
        val now = System.currentTimeMillis()
        val timeStr = dateFormat.format(Date(now))
        val stackTraceStr = throwable?.let { t ->
            val sw = java.io.StringWriter()
            t.printStackTrace(java.io.PrintWriter(sw))
            sw.toString().take(1200)
        }

        val effectiveFix = suggestedFix ?: determineSuggestedFix(category, httpCode, message, rawServerResponse)

        val entry = AppErrorLog(
            timestamp = now,
            formattedTime = timeStr,
            category = category,
            title = title,
            message = message,
            endpoint = endpoint,
            httpCode = httpCode,
            rawServerResponse = rawServerResponse?.take(2000),
            stackTrace = stackTraceStr,
            suggestedFix = effectiveFix
        )

        val currentList = _logs.value.toMutableList()
        currentList.add(0, entry) // Newest first
        if (currentList.size > 100) {
            _logs.value = currentList.take(100)
        } else {
            _logs.value = currentList
        }

        persistLogs()
    }

    fun logFromHttp(
        endpoint: String,
        statusCode: Int,
        rawBody: String,
        exceptionMsg: String? = null,
        nasConfig: NasConfig? = null
    ) {
        val category = when {
            rawBody.contains("Table", ignoreCase = true) && rawBody.contains("doesn't exist", ignoreCase = true) -> ErrorCategory.MARIADB
            rawBody.contains("Unknown database", ignoreCase = true) -> ErrorCategory.MARIADB
            rawBody.contains("Access denied for user", ignoreCase = true) || rawBody.contains("1045", ignoreCase = true) -> ErrorCategory.MARIADB
            rawBody.contains("Connection refused", ignoreCase = true) || rawBody.contains("2002", ignoreCase = true) -> ErrorCategory.MARIADB
            rawBody.contains("SQLSTATE", ignoreCase = true) -> ErrorCategory.MARIADB
            statusCode == 404 -> ErrorCategory.SYNOLOGY_API
            statusCode == 500 -> ErrorCategory.SYNOLOGY_API
            statusCode <= 0 -> ErrorCategory.NETWORK
            else -> ErrorCategory.SYSTEM
        }

        val title = when (category) {
            ErrorCategory.MARIADB -> "Error en Base de Datos MariaDB 10"
            ErrorCategory.SYNOLOGY_API -> "Error en Web Station / PHP 7.3 (HTTP $statusCode)"
            ErrorCategory.NETWORK -> "Fallo de Conexión LAN con Synology"
            else -> "Error HTTP $statusCode"
        }

        val message = exceptionMsg ?: when {
            rawBody.contains("doesn't exist", ignoreCase = true) -> "Faltan tablas requeridas en la base de datos 'family_feed'."
            rawBody.contains("Access denied", ignoreCase = true) -> "Credenciales inválidas para el usuario de MariaDB."
            statusCode == 404 -> "No se encontró el archivo 'index.php' en la ruta configurada del NAS."
            statusCode == 500 -> "El servidor PHP devolvió error 500."
            else -> "Respuesta no exitosa del servidor ($statusCode)."
        }

        log(
            category = category,
            title = title,
            message = message,
            endpoint = endpoint,
            httpCode = statusCode,
            rawServerResponse = rawBody,
            suggestedFix = determineSuggestedFix(category, statusCode, message, rawBody)
        )
    }

    private fun determineSuggestedFix(
        category: ErrorCategory,
        httpCode: Int?,
        message: String,
        rawBody: String?
    ): String {
        val text = ((rawBody ?: "") + " " + message).lowercase(Locale.ROOT)
        return when {
            text.contains("doesn't exist") || text.contains("faltan tablas") ->
                "Pulsa 'Reparar Tablas' en la app para crearlas automáticamente, o importa el archivo 'schema.sql' en phpMyAdmin de MariaDB 10."
            text.contains("access denied") || text.contains("1045") ->
                "Verifica usuario y contraseña en 'config.php' o en los Ajustes de la App. En Synology MariaDB 10, el usuario por defecto es 'root'."
            text.contains("connection refused") || text.contains("2002") ->
                "MariaDB 10 en Synology utiliza el puerto 3307 por defecto (no el 3306). Comprueba en DSM que el paquete MariaDB 10 esté 'En ejecución'."
            text.contains("unknown database") || text.contains("1049") ->
                "La base de datos 'family_feed' no existe. Pulsa 'Reparar y crear tablas' para que el script la cree automáticamente."
            text.contains("match") && (text.contains("parse error") || text.contains("syntax")) ->
                "PHP 7.3 no soporta la expresión 'match'. Se requiere usar 'switch'. Verifica tener la versión actualizada de los scripts."
            httpCode == 404 ->
                "Verifica que los archivos 'index.php' y 'config.php' estén en '/volume1/web/family_api' de Web Station y que el puerto coincida."
            category == ErrorCategory.NETWORK || httpCode == -1 ->
                "Comprueba que el móvil esté en la misma red Wi-Fi que el Synology NAS y que la IP LAN sea la correcta."
            else ->
                "Revisa que Web Station y PHP 7.3 tengan activadas las extensiones 'pdo_mysql' y 'gd'."
        }
    }

    @Synchronized
    fun clearLogs() {
        _logs.value = emptyList()
        appContext?.let { ctx ->
            try {
                val f = File(ctx.filesDir, "app_error_logs.json")
                if (f.exists()) f.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun exportAsText(nasConfig: NasConfig?): String {
        val sb = StringBuilder()
        sb.append("=====================================================\n")
        sb.append("      REGISTRO DE ERRORES Y DIAGNÓSTICO LAN         \n")
        sb.append("=====================================================\n")
        sb.append("Fecha reporte: ${dateFormat.format(Date())}\n")
        if (nasConfig != null) {
            sb.append("Servidor Synology: ${nasConfig.ip}:${nasConfig.port}\n")
            sb.append("Ruta API: ${nasConfig.path}\n")
            sb.append("MariaDB 10: ${nasConfig.dbHost}:${nasConfig.dbPort} (BD: ${nasConfig.dbName})\n")
            sb.append("Estado conexión actual: ${if (nasConfig.isConnected) "CONECTADO" else "DESCONECTADO"}\n")
            if (!nasConfig.lastError.isNullOrBlank()) {
                sb.append("Último error reportado: ${nasConfig.lastError}\n")
            }
        }
        sb.append("Entorno esperado en NAS: Web Station + PHP 7.3 / MariaDB 10\n")
        sb.append("Total de errores registrados: ${_logs.value.size}\n")
        sb.append("-----------------------------------------------------\n\n")

        if (_logs.value.isEmpty()) {
            sb.append("No hay errores registrados recientemente. El sistema está funcionando correctamente.\n")
        } else {
            _logs.value.forEachIndexed { i, log ->
                sb.append("[#${i + 1}] ${log.formattedTime} - ${log.category.displayName}\n")
                sb.append("Título: ${log.title}\n")
                if (log.httpCode != null) sb.append("Código HTTP: ${log.httpCode}\n")
                if (!log.endpoint.isNullOrBlank()) sb.append("Endpoint: ${log.endpoint}\n")
                sb.append("Mensaje: ${log.message}\n")
                if (!log.suggestedFix.isNullOrBlank()) {
                    sb.append("Solución sugerida: ${log.suggestedFix}\n")
                }
                if (!log.rawServerResponse.isNullOrBlank()) {
                    sb.append("Respuesta del servidor:\n${log.rawServerResponse}\n")
                }
                if (!log.stackTrace.isNullOrBlank()) {
                    sb.append("Trace técnico:\n${log.stackTrace}\n")
                }
                sb.append("-----------------------------------------------------\n\n")
            }
        }
        return sb.toString()
    }

    private fun persistLogs() {
        val ctx = appContext ?: return
        try {
            val arr = JSONArray()
            _logs.value.take(50).forEach { log ->
                val obj = JSONObject()
                obj.put("id", log.id)
                obj.put("timestamp", log.timestamp)
                obj.put("formattedTime", log.formattedTime)
                obj.put("category", log.category.name)
                obj.put("title", log.title)
                obj.put("message", log.message)
                obj.put("endpoint", log.endpoint)
                obj.put("httpCode", log.httpCode ?: -1)
                obj.put("rawServerResponse", log.rawServerResponse)
                obj.put("suggestedFix", log.suggestedFix)
                arr.put(obj)
            }
            val f = File(ctx.filesDir, "app_error_logs.json")
            f.writeText(arr.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadPersistedLogs() {
        val ctx = appContext ?: return
        try {
            val f = File(ctx.filesDir, "app_error_logs.json")
            if (!f.exists()) return
            val content = f.readText()
            if (content.isBlank()) return
            val arr = JSONArray(content)
            val list = mutableListOf<AppErrorLog>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val catName = obj.optString("category", "SYSTEM")
                val cat = try { ErrorCategory.valueOf(catName) } catch (_: Exception) { ErrorCategory.SYSTEM }
                val code = obj.optInt("httpCode", -1).takeIf { it != -1 }
                list.add(
                    AppErrorLog(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        formattedTime = obj.optString("formattedTime", ""),
                        category = cat,
                        title = obj.optString("title", "Error"),
                        message = obj.optString("message", ""),
                        endpoint = obj.optString("endpoint").takeIf { it.isNotBlank() },
                        httpCode = code,
                        rawServerResponse = obj.optString("rawServerResponse").takeIf { it.isNotBlank() },
                        suggestedFix = obj.optString("suggestedFix").takeIf { it.isNotBlank() }
                    )
                )
            }
            _logs.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
