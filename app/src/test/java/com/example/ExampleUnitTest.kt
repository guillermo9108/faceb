package com.example

import com.example.model.CheckItem
import com.example.model.DatabaseDiagnosticReport
import com.example.model.FamilyMember
import com.example.model.FamilyStory
import com.example.model.MediaItem
import com.example.model.MediaType
import com.example.model.NasConfig
import com.example.model.Post
import com.example.model.ReactionType
import com.example.model.ScannedFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias para modelos de datos y lógica de configuración del NAS Synology y MariaDB.
 */
class ExampleUnitTest {

    @Test
    fun testNasConfig_baseUrlGeneration() {
        val defaultConfig = NasConfig(ip = "192.168.1.50", port = 80, path = "/family_api")
        assertEquals("http://192.168.1.50/family_api", defaultConfig.baseUrl)

        val customPortConfig = NasConfig(ip = "192.168.1.50", port = 8080, path = "/family_api")
        assertEquals("http://192.168.1.50:8080/family_api", customPortConfig.baseUrl)
    }

    @Test
    fun testFamilyMember_initialization() {
        val member = FamilyMember(
            id = 1,
            name = "Papá",
            role = "Administrador",
            avatarEmoji = "👨",
            avatarColorHex = 0xFF1877F2L,
            pin = "1234"
        )
        assertEquals(1, member.id)
        assertEquals("Papá", member.name)
        assertEquals("👨", member.avatarEmoji)
        assertTrue(member.isOnlineInLan)
    }

    @Test
    fun testPost_reactionsAndMedia() {
        val author = FamilyMember(id = 2, name = "Mamá", role = "Mamá")
        val media = listOf(
            MediaItem(id = "m1", type = MediaType.IMAGE, url = "http://nas/photo.jpg"),
            MediaItem(id = "m2", type = MediaType.VIDEO, url = "http://nas/video.mp4")
        )
        val post = Post(
            id = 101,
            author = author,
            contentText = "Día en el parque familiar",
            mediaItems = media,
            reactions = mapOf(ReactionType.LOVE to 3, ReactionType.LIKE to 5)
        )

        assertEquals(101, post.id)
        assertEquals(2, post.mediaItems.size)
        assertEquals(3, post.reactions[ReactionType.LOVE])
        assertEquals(5, post.reactions[ReactionType.LIKE])
        assertFalse(post.isShort)
    }

    @Test
    fun testDatabaseDiagnosticReport_tableCountCheck() {
        val tables = listOf(
            "users", "posts", "media", "comments", "reactions", "stories", "family_groups", "groups"
        )
        val report = DatabaseDiagnosticReport(
            status = "OK",
            timestamp = "2026-11-10 12:00:00",
            phpVersion = "7.3.16",
            dbHost = "127.0.0.1",
            dbPort = 3307,
            dbName = "family_feed",
            tablesCount = tables.size,
            expectedTablesCount = 8,
            tables = tables,
            missingTables = emptyList(),
            databaseExists = true,
            pdoMysqlExtension = CheckItem(success = true, message = "PDO MySQL Activo"),
            tcpConnection = CheckItem(success = true, message = "Puerto TCP 3307 OK")
        )

        assertEquals("OK", report.status)
        assertEquals(8, report.tablesCount)
        assertEquals(8, report.expectedTablesCount)
        assertTrue(report.missingTables.isEmpty())
        assertTrue(report.databaseExists)
        assertTrue(report.pdoMysqlExtension.success)
        assertTrue(report.tcpConnection.success)
    }

    @Test
    fun testScannedFolder_subfolderAttributes() {
        val folder = ScannedFolder(
            folderName = "Vacaciones 2026",
            folderPath = "/volume1/photo/Vacaciones 2026",
            mediaCount = 150,
            photosCount = 120,
            videosCount = 30,
            isAlreadyImported = false,
            isSelected = true
        )

        assertEquals("Vacaciones 2026", folder.folderName)
        assertEquals(150, folder.mediaCount)
        assertTrue(folder.isSelected)
        assertFalse(folder.isAlreadyImported)
    }

    @Test
    fun testFamilyStory_defaults() {
        val author = FamilyMember(id = 3, name = "Hija", role = "Hija")
        val story = FamilyStory(
            id = "s-1",
            author = author,
            imageUrl = "http://nas/story1.jpg"
        )

        assertEquals("s-1", story.id)
        assertEquals("http://nas/story1.jpg", story.mediaUrl)
        assertFalse(story.isViewed)
    }
}
