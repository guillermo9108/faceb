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
        assertTrue(SynologyScripts.indexPhp.contains("Synology Web Station + MariaDB 10"))
        assertTrue(SynologyScripts.indexPhp.contains("uploads/originals"))
    }
}
