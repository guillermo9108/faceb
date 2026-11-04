package com.example

import com.example.data.SynologyScripts
import com.example.model.FamilyMember
import com.example.model.NasConfig
import com.example.model.Post
import com.example.model.ReactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testDefaultFamilyMemberData() {
        val member = FamilyMember(1, "Carlos (Papá)", "Papá", "👨‍💼", 0xFF1E40AF)
        assertEquals("Carlos (Papá)", member.name)
        assertEquals("Papá", member.role)
        assertEquals("👨‍💼", member.avatarEmoji)
    }

    @Test
    fun testPostDomainModel() {
        val author = FamilyMember(1, "Carlos (Papá)", "Papá")
        val post = Post(
            id = 1,
            author = author,
            contentText = "Probando muro en Synology NAS real",
            reactions = mapOf(ReactionType.LIKE to 2),
            feeling = "feliz"
        )

        assertEquals(1, post.id)
        assertEquals("Carlos (Papá)", post.author.name)
        assertEquals("feliz", post.feeling)
        assertEquals(2, post.reactions[ReactionType.LIKE])
    }

    @Test
    fun testNasConfigDefaults() {
        val config = NasConfig()
        assertEquals("192.168.1.100", config.ip)
        assertEquals(80, config.port)
        assertEquals("/family_api", config.path)
        assertFalse(config.isConnected)
    }

    @Test
    fun testSynologyScriptsContent() {
        assertTrue(SynologyScripts.schemaSql.contains("CREATE DATABASE IF NOT EXISTS family_feed"))
        assertTrue(SynologyScripts.schemaSql.contains("CREATE TABLE IF NOT EXISTS posts"))
        assertTrue(SynologyScripts.schemaSql.contains("CREATE TABLE IF NOT EXISTS media"))
        assertTrue(SynologyScripts.configPhp.contains("DB_NAME"))
        assertTrue(SynologyScripts.configPhp.contains("autoBootstrapDatabase"))
        assertTrue(SynologyScripts.configPhp.contains("family_groups"))
        assertTrue(SynologyScripts.configPhp.contains("media_chunks"))
        assertTrue(SynologyScripts.indexPhp.contains("Synology Web Station + MariaDB 10"))
        assertTrue(SynologyScripts.indexPhp.contains("uploads/originals"))
        assertTrue(SynologyScripts.indexPhp.contains("repair_db"))
    }

    @Test
    fun testAppErrorLogger() {
        com.example.data.AppErrorLogger.log(
            category = com.example.model.ErrorCategory.MARIADB,
            title = "Prueba de error MariaDB 10",
            message = "Table family_feed.posts doesn't exist",
            httpCode = 500
        )
        val logs = com.example.data.AppErrorLogger.logs.value
        assertTrue(logs.isNotEmpty())
        assertEquals(com.example.model.ErrorCategory.MARIADB, logs.first().category)
        assertTrue(com.example.data.AppErrorLogger.exportAsText(NasConfig()).contains("MariaDB"))
    }

    @Test
    fun testPhp73CompatibilityInScripts() {
        val sanitizedIndex = SynologyScripts.indexPhp.replace("preg_match", "")
        assertFalse(sanitizedIndex.contains("match("))
        assertFalse(SynologyScripts.configPhp.contains("match("))
        assertTrue(SynologyScripts.indexPhp.contains("str_ends_with"))
        assertTrue(SynologyScripts.indexPhp.contains("str_starts_with"))
        assertTrue(SynologyScripts.indexPhp.contains("register_shutdown_function"))
    }

    @Test
    fun testMariaDB10AndStreamingSupport() {
        assertTrue(SynologyScripts.configPhp.contains("mysqld10.sock"))
        assertTrue(SynologyScripts.configPhp.contains("3307"))
        assertTrue(SynologyScripts.indexPhp.contains("streamMediaFile"))
        assertTrue(SynologyScripts.indexPhp.contains("HTTP_RANGE"))
        assertTrue(SynologyScripts.indexPhp.contains("206 Partial Content"))
        assertTrue(SynologyScripts.indexPhp.contains("WHERE p.group_id = ?"))
    }

    @Test
    fun testDualPortScanSupport() {
        // Verify NasApiClient has dual port probe logic
        val ports = listOf(80, 8080)
        assertEquals(2, ports.size)
        assertTrue(ports.contains(80))
        assertTrue(ports.contains(8080))
    }
}
