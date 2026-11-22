package com.example

import com.example.model.CheckItem
import com.example.model.DatabaseDiagnosticReport
import com.example.model.FamilyMember
import com.example.model.FamilyStory
import com.example.model.FeedPageResult
import com.example.model.MediaItem
import com.example.model.MediaType
import com.example.model.NasConfig
import com.example.model.Post
import com.example.model.ReactionType
import com.example.model.ScannedFolder
import com.example.ui.components.UnifiedTimelineHelper
import com.example.util.FeedStreamEntry
import com.example.util.FeedStreamHelper
import com.example.util.OfflineMediaManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
            "users", "posts", "media", "comments", "reactions", "stories", "family_groups", "groups", "user_follows"
        )
        val report = DatabaseDiagnosticReport(
            status = "OK",
            timestamp = "2026-11-10 12:00:00",
            phpVersion = "7.3.16",
            dbHost = "127.0.0.1",
            dbPort = 3307,
            dbName = "family_feed",
            tablesCount = tables.size,
            expectedTablesCount = 9,
            tables = tables,
            missingTables = emptyList(),
            databaseExists = true,
            pdoMysqlExtension = CheckItem(success = true, message = "PDO MySQL Activo"),
            tcpConnection = CheckItem(success = true, message = "Puerto TCP 3307 OK")
        )

        assertEquals("OK", report.status)
        assertEquals(9, report.tablesCount)
        assertEquals(9, report.expectedTablesCount)
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

    @Test
    fun testUnifiedTimelineHelper_multiChunkOffsetsAndSeek() {
        val durations = UnifiedTimelineHelper.buildChunkDurations(
            chunkCount = 3,
            explicitDurationsMs = listOf(8000L, 7500L, 4500L)
        )
        assertEquals(listOf(8000L, 7500L, 4500L), durations)

        val offsets = UnifiedTimelineHelper.buildChunkOffsets(durations)
        assertEquals(listOf(0L, 8000L, 15500L), offsets)
        assertEquals(20000L, UnifiedTimelineHelper.totalDurationMs(durations))

        // Posición en el segundo 3.2 del fragmento #1 -> 8000 + 3200 = 11200ms
        assertEquals(11200L, UnifiedTimelineHelper.globalPositionMs(1, 3200L, offsets, durations))

        // Seek global a 17000ms -> cae en el fragmento #2 con offset local de 1500ms
        val (chunkIdx, localMs) = UnifiedTimelineHelper.seekTargetForGlobal(17000L, offsets, durations)
        assertEquals(2, chunkIdx)
        assertEquals(1500L, localMs)

        // Dos fronteras internas entre 3 fragmentos
        val boundaries = UnifiedTimelineHelper.boundaryFractions(offsets, 20000L)
        assertEquals(2, boundaries.size)
        assertEquals(0.4f, boundaries[0], 0.01f)
        assertEquals(0.775f, boundaries[1], 0.01f)
    }

    @Test
    fun testUnifiedTimelineHelper_fallbackEstimationAndLabelParsing() {
        assertEquals(95000L, UnifiedTimelineHelper.parseDurationLabelMs("01:35"))
        assertEquals(3665000L, UnifiedTimelineHelper.parseDurationLabelMs("01:01:05"))

        val estimated = UnifiedTimelineHelper.buildChunkDurations(
            chunkCount = 3,
            explicitDurationsMs = emptyList(),
            exoDurationsMs = emptyList(),
            fallbackTotalMs = 24000L
        )
        assertEquals(listOf(8000L, 8000L, 8000L), estimated)
    }

    @Test
    fun testFeedPageResult_paginationFields() {
        val page = FeedPageResult(
            posts = emptyList(),
            hasMore = true,
            totalCount = 34,
            nextOffset = 10
        )
        assertTrue(page.hasMore)
        assertEquals(34, page.totalCount)
        assertEquals(10, page.nextOffset)
    }

    @Test
    fun testOfflineMediaManager_thumbnailFallbackWithoutVideoDownload() {
        assertTrue(OfflineMediaManager.isVideoExtension("http://nas/family_api/index.php?route=media&path=videos/clip.mp4"))
        assertFalse(OfflineMediaManager.isVideoExtension("http://nas/family_api/index.php?route=media&path=photos/cover.jpg"))

        // Vídeo sin miniatura explícita pero con imagen de carpeta -> usa la de carpeta
        val withFolderCover = OfflineMediaManager.resolveThumbnailCandidate(
            isVideo = true,
            thumbnailUrl = "http://nas/uploads/clip.mp4",
            mediaUrl = "http://nas/uploads/clip.mp4",
            fallbackFolderImageUrl = "http://nas/uploads/folder_cover.jpg"
        )
        assertEquals("http://nas/uploads/folder_cover.jpg", withFolderCover)

        // Vídeo con miniatura explícita generada -> prioriza la explícita sobre la de carpeta
        val withExplicitThumb = OfflineMediaManager.resolveThumbnailCandidate(
            isVideo = true,
            thumbnailUrl = "http://nas/uploads/thumbs/vthumb_1.jpg",
            mediaUrl = "http://nas/uploads/clip.mp4",
            fallbackFolderImageUrl = "http://nas/uploads/folder_cover.jpg"
        )
        assertEquals("http://nas/uploads/thumbs/vthumb_1.jpg", withExplicitThumb)

        // Vídeo sin miniatura ni imagen en carpeta -> null (nunca devuelve el .mp4 para evitar descargas)
        val withoutAnyImage = OfflineMediaManager.resolveThumbnailCandidate(
            isVideo = true,
            thumbnailUrl = null,
            mediaUrl = "http://nas/uploads/clip.mp4",
            fallbackFolderImageUrl = null
        )
        assertNull(withoutAnyImage)
    }

    @Test
    fun testFeedStreamHelper_curatedFollowsAndOrderedShortsInterleaving() {
        val self = FamilyMember(id = 1, name = "Yo", role = "Familiar")
        val followedUser = FamilyMember(id = 2, name = "Mamá", role = "Familiar")
        val unfollowedUser = FamilyMember(id = 3, name = "Tío", role = "Familiar")

        val p1 = Post(id = 10, author = self, contentText = "Post propio", createdAt = 1000L)
        val p2 = Post(id = 11, author = unfollowedUser, contentText = "Post no seguido", createdAt = 900L)
        val p3 = Post(id = 12, author = followedUser, contentText = "Post de usuario seguido", createdAt = 800L)
        val p4 = Post(id = 13, author = unfollowedUser, groupId = 5, groupName = "Viajes", contentText = "Post de grupo seguido", createdAt = 700L)
        val p5 = Post(id = 14, author = unfollowedUser, groupId = 9, groupName = "Otro", contentText = "Post de grupo no seguido", createdAt = 600L)

        val curated = FeedStreamHelper.filterFeedByFollows(
            posts = listOf(p1, p2, p3, p4, p5),
            currentUserId = 1,
            followedUserIds = setOf(2),
            followedGroupIds = setOf(5)
        )

        assertEquals(listOf(10, 12, 13), curated.map { it.id })

        val shorts = listOf(
            Post(id = 201, author = followedUser, isShort = true, contentText = "Short 1"),
            Post(id = 202, author = self, isShort = true, contentText = "Short 2")
        )
        val entries = FeedStreamHelper.buildFeedEntries(
            posts = curated,
            shorts = shorts,
            interval = 2,
            groupSize = 4
        )

        // Orden relativo de publicaciones conservado + carrusel de Shorts intercalado tras el 2do post
        assertEquals(4, entries.size)
        assertTrue(entries[0] is FeedStreamEntry.PostEntry)
        assertEquals(10, (entries[0] as FeedStreamEntry.PostEntry).post.id)
        assertTrue(entries[1] is FeedStreamEntry.PostEntry)
        assertEquals(12, (entries[1] as FeedStreamEntry.PostEntry).post.id)
        assertTrue(entries[2] is FeedStreamEntry.ShortsGroupEntry)
        assertEquals(listOf(201, 202), (entries[2] as FeedStreamEntry.ShortsGroupEntry).shorts.map { it.id })
        assertTrue(entries[3] is FeedStreamEntry.PostEntry)
        assertEquals(13, (entries[3] as FeedStreamEntry.PostEntry).post.id)
    }
}
