package com.example.model

enum class ErrorCategory(val displayName: String, val badge: String) {
    MARIADB("MariaDB 10", "🗄️"),
    SYNOLOGY_API("PHP 7.3 / Web Station", "🌐"),
    NETWORK("Red LAN", "📶"),
    VIDEO_CHUNKING("Fragmentación / Subida", "✂️"),
    AUTH("Autenticación", "👤"),
    SYSTEM("Sistema", "⚙️")
}

data class AppErrorLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val formattedTime: String,
    val category: ErrorCategory,
    val title: String,
    val message: String,
    val endpoint: String? = null,
    val httpCode: Int? = null,
    val rawServerResponse: String? = null,
    val stackTrace: String? = null,
    val suggestedFix: String? = null
)
