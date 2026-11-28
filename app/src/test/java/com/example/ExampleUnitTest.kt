package com.example

import com.example.model.CheckItem
import com.example.model.DatabaseDiagnosticReport
import com.example.model.FamilyGroup
import com.example.model.FamilyMember
import com.example.model.FamilyStory
import com.example.model.FeedPageResult
import com.example.model.GroupOrganizationMode
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

    @Test
    fun testFamilyGroup_adminAndSeriesDefaults() {
        val defaultGroup = FamilyGroup(
            id = 42,
            name = "Vacaciones 2026",
            folderPath = "/volume1/photo/vacaciones"
        )
        assertEquals(1, defaultGroup.createdByUserId)
        assertTrue(defaultGroup.allowUploads)
        assertEquals(GroupOrganizationMode.NORMAL, defaultGroup.organizationMode)

        val seriesGroup = FamilyGroup(
            id = 43,
            name = "Serie Familiar",
            folderPath = "/volume1/photo/serie",
            createdByUserId = 7,
            allowUploads = false,
            organizationMode = GroupOrganizationMode.SERIES
        )
        assertEquals(7, seriesGroup.createdByUserId)
        assertFalse(seriesGroup.allowUploads)
        assertEquals(GroupOrganizationMode.SERIES, seriesGroup.organizationMode)
    }

    @Test
    fun testUnifiedTimelineHelper_cardToFullscreenHandoffPreservesChunkAndOffset() {
        val durations = listOf(10000L, 12000L, 8000L)
        val offsets = UnifiedTimelineHelper.buildChunkOffsets(durations)
        val cardChunk = 1
        val cardLocalPos = 6400L

        val handoffGlobal = UnifiedTimelineHelper.globalPositionMs(
            chunkIndex = cardChunk,
            localPositionMs = cardLocalPos,
            offsetsMs = offsets,
            durationsMs = durations
        )
        assertEquals(16400L, handoffGlobal)

        val (restoredChunk, restoredLocal) = UnifiedTimelineHelper.seekTargetForGlobal(
            globalPosMs = handoffGlobal,
            offsetsMs = offsets,
            durationsMs = durations
        )
        assertEquals(cardChunk, restoredChunk)
        assertEquals(cardLocalPos, restoredLocal)
    }

    @Test
    fun testOfflineMediaManager_thumbnailProxyUrl() {
        assertEquals(
            "http://nas/family_api/index.php?action=media_proxy&path=foto.jpg&thumb=1",
            OfflineMediaManager.toThumbnailProxyUrl("http://nas/family_api/index.php?action=media_proxy&path=foto.jpg")
        )
        // Idempotente si ya contiene thumb=
        assertEquals(
            "http://nas/family_api/index.php?action=media_proxy&path=foto.jpg&thumb=1",
            OfflineMediaManager.toThumbnailProxyUrl("http://nas/family_api/index.php?action=media_proxy&path=foto.jpg&thumb=1")
        )
        // URLs que no son media_proxy permanecen intactas
        assertEquals(
            "http://nas/family_api/uploads/foto.jpg",
            OfflineMediaManager.toThumbnailProxyUrl("http://nas/family_api/uploads/foto.jpg")
        )
    }

    @Test
    fun testFeedStreamHelper_diversifyPosts_avoidsMonotonyAndConsecutiveVideo() {
        val mama = FamilyMember(id = 2, name = "Mamá", role = "Mamá")
        val papa = FamilyMember(id = 3, name = "Papá", role = "Papá")
        val posts = listOf(
            Post(id = 1, author = mama, contentText = "Video 1", groupId = 10, createdAt = 5000L, mediaItems = listOf(MediaItem(id = "v1", type = MediaType.VIDEO, url = "http://nas/v1.mp4"))),
            Post(id = 2, author = papa, contentText = "Video 2", groupId = 20, createdAt = 4900L, mediaItems = listOf(MediaItem(id = "v2", type = MediaType.VIDEO, url = "http://nas/v2.mp4"))),
            Post(id = 3, author = papa, contentText = "Foto P1", groupId = 20, createdAt = 4800L, mediaItems = listOf(MediaItem(id = "i1", type = MediaType.IMAGE, url = "http://nas/i1.jpg"))),
            Post(id = 4, author = mama, contentText = "Foto M1", groupId = 10, createdAt = 4700L, mediaItems = listOf(MediaItem(id = "i2", type = MediaType.IMAGE, url = "http://nas/i2.jpg")))
        )

        val diversified = FeedStreamHelper.diversifyPosts(posts, maxConsecutiveAuthor = 1, maxConsecutiveGroup = 1)
        assertEquals(4, diversified.size)
        // Después de Video 1 no debe ir inmediatamente Video 2 si Foto M1/P1 rompen autor/grupo y formato
        assertFalse(
            diversified[0].mediaItems.first().type == MediaType.VIDEO &&
                diversified[1].mediaItems.first().type == MediaType.VIDEO
        )
    }

    @Test
    fun testFeedStreamHelper_buildSmartFeedEntries_unseenPrioritySeriesOrderAndOfflineBoost() {
        val self = FamilyMember(id = 1, name = "Yo", role = "Familiar")
        val mama = FamilyMember(id = 2, name = "Mamá", role = "Familiar")
        val papa = FamilyMember(id = 3, name = "Papá", role = "Familiar")

        val seriesGroup = FamilyGroup(
            id = 50,
            name = "Serie Vacaciones",
            folderPath = "/volume1/photo/serie",
            organizationMode = GroupOrganizationMode.SERIES
        )

        val seenOld1 = Post(id = 1, author = mama, contentText = "Visto 1", createdAt = 100L)
        val seenOld2 = Post(id = 2, author = papa, contentText = "Visto 2", createdAt = 200L)
        val seenWithNewComment = Post(
            id = 3,
            author = mama,
            contentText = "Con comentario nuevo",
            createdAt = 300L,
            comments = listOf(
                com.example.model.Comment(id = 91, postId = 3, author = papa, text = "¡Qué bueno!"),
                com.example.model.Comment(id = 92, postId = 3, author = self, text = "Genial")
            )
        )
        val unseenFresh = Post(id = 4, author = papa, contentText = "Nuevo sin ver", createdAt = 400L)
        val seriesEp1 = Post(id = 5, author = self, groupId = 50, groupName = "Serie Vacaciones", contentText = "Ep 1", createdAt = 450L)
        val seriesEp2 = Post(id = 6, author = self, groupId = 50, groupName = "Serie Vacaciones", contentText = "Ep 2", createdAt = 550L)

        // Usuario vio 1, 2 y 3 (cuando 3 tenía 0 comentarios), pero aún no vio 4, 5 ni 6
        val seenSnapshot = mapOf(1 to 0, 2 to 0, 3 to 0)
        val entries = FeedStreamHelper.buildSmartFeedEntries(
            posts = listOf(seriesEp2, seriesEp1, unseenFresh, seenWithNewComment, seenOld2, seenOld1),
            shorts = listOf(Post(id = 900, author = mama, isShort = true, contentText = "Short")),
            groups = listOf(seriesGroup),
            followedGroupIds = setOf(50),
            seenCommentsSnapshot = seenSnapshot,
            sessionSeed = 12345L,
            interval = 3,
            groupSize = 4
        )

        val dividerIdx = entries.indexOfFirst { it is FeedStreamEntry.CaughtUpDividerEntry }
        assertTrue("Debe aparecer el separador Ya estás al día", dividerIdx > 0)

        val beforeDividerIds = entries.take(dividerIdx).mapNotNull {
            when (it) {
                is FeedStreamEntry.PostEntry -> it.post.id
                is FeedStreamEntry.ActiveConversationEntry -> it.post.id
                else -> null
            }
        }
        assertTrue("El post con nuevos comentarios (3) debe priorizarse antes del separador", beforeDividerIds.contains(3) || entries.any { it is FeedStreamEntry.ActiveConversationEntry && it.post.id == 3 })
        assertFalse("Los vistos sin actividad nueva no deben estar antes del separador", beforeDividerIds.contains(1) || beforeDividerIds.contains(2))
        assertTrue("Ep 1 de la serie debe ir antes que Ep 2 en el muro", beforeDividerIds.indexOf(5) < beforeDividerIds.indexOf(6))
        assertTrue("Debe incluir tarjeta Continúa viendo para grupo Series", entries.any { it is FeedStreamEntry.ContinueSeriesEntry })

        // En modo offline, las publicaciones guardadas localmente reciben prioridad frente a otras sin caché
        val cachedScore = FeedStreamHelper.computeRelevanceScore(
            post = seenOld1.copy(isSavedOffline = true),
            seenCommentsSnapshot = seenSnapshot,
            isOfflineOrSlow = true,
            referenceTimeMs = 1000L
        )
        val uncachedScore = FeedStreamHelper.computeRelevanceScore(
            post = seenOld2.copy(isSavedOffline = false),
            seenCommentsSnapshot = seenSnapshot,
            isOfflineOrSlow = true,
            referenceTimeMs = 1000L
        )
        assertTrue("Modo offline debe priorizar publicaciones cacheadas", cachedScore > uncachedScore)
    }

    @Test
    fun testFeedStreamHelper_buildAppendedFeedEntries_neverJumpsAboveAnchoredPosts() {
        val mama = FamilyMember(id = 2, name = "Mamá", role = "Familiar")
        val papa = FamilyMember(id = 3, name = "Papá", role = "Familiar")

        val page0Posts = listOf(
            Post(id = 1, author = mama, contentText = "Inicial 1 (ya visto)", createdAt = 2000L),
            Post(id = 2, author = papa, contentText = "Inicial 2 (ya visto)", createdAt = 1900L)
        )
        // Aunque página 1 venga sin visto y con muchas reacciones, nunca debe saltar arriba de página 0
        val page1NewPosts = listOf(
            Post(
                id = 11,
                author = mama,
                contentText = "Paginado 11",
                createdAt = 1000L,
                reactions = mapOf(ReactionType.LOVE to 25)
            ),
            Post(id = 12, author = papa, contentText = "Paginado 12", createdAt = 900L)
        )
        val shorts = listOf(Post(id = 901, author = mama, isShort = true, contentText = "Short 1"))
        val seenSnapshot = mapOf(1 to 0, 2 to 0)

        val initialAnchored = FeedStreamHelper.buildSmartFeedEntries(
            posts = page0Posts,
            shorts = shorts,
            seenCommentsSnapshot = seenSnapshot,
            interval = 2,
            groupSize = 4
        )
        val existingShortsCount = initialAnchored.count { it is FeedStreamEntry.ShortsGroupEntry }
        val appendedTail = FeedStreamHelper.buildAppendedFeedEntries(
            newPosts = page1NewPosts,
            shorts = shorts,
            seenCommentsSnapshot = seenSnapshot,
            previousTailPosts = page0Posts,
            existingShortsGroupCount = existingShortsCount,
            interval = 2,
            groupSize = 4
        )

        val fullTimeline = initialAnchored + appendedTail
        val postIdsInOrder = fullTimeline.filterIsInstance<FeedStreamEntry.PostEntry>().map { it.post.id }

        // Las publicaciones de página 0 conservan sus posiciones iniciales antes que página 1
        assertEquals(listOf(1, 2, 11, 12), postIdsInOrder)

        // Las claves de bloques Shorts no colisionan entre lotes
        val allKeys = fullTimeline.map { it.stableKey }
        assertEquals(allKeys.distinct().size, allKeys.size)
    }

    @Test
    fun testOfflineDownloadQueueManager_fifoSequentialPromotion() {
        com.example.util.OfflineDownloadQueueManager.clearPendingQueue()
        val author = FamilyMember(id = 1, name = "Papá", role = "Admin")
        val p1 = Post(
            id = 501,
            author = author,
            contentText = "Vídeo vacaciones",
            mediaItems = listOf(MediaItem(id = "v1", type = MediaType.VIDEO, url = "http://nas/uploads/clip1.mp4"))
        )
        val p2 = Post(
            id = 502,
            author = author,
            contentText = "Foto parque",
            mediaItems = listOf(MediaItem(id = "i1", type = MediaType.IMAGE, url = "http://nas/uploads/foto1.jpg"))
        )

        val q1 = com.example.util.OfflineDownloadQueueManager.enqueueForQueueOnly("http://nas/family_api", p1)
        val q2 = com.example.util.OfflineDownloadQueueManager.enqueueForQueueOnly("http://nas/family_api", p2)

        assertEquals(com.example.util.DownloadItemStatus.DOWNLOADING, q1.status)
        assertEquals(com.example.util.DownloadItemStatus.QUEUED, q2.status)
        assertEquals(1, com.example.util.OfflineDownloadQueueManager.getQueuedRemainingCount(p1.id))

        val updated1 = com.example.util.OfflineDownloadQueueManager.updateActiveProgress(
            postId = p1.id,
            progressPercent = 47,
            currentFileName = "clip1.mp4",
            currentFileIndex = 0,
            totalFiles = 1
        )
        assertEquals(47, updated1?.progressPercent)

        val (finished, nextItem) = com.example.util.OfflineDownloadQueueManager.completeAndAdvance(p1.id, true)
        assertEquals(p1.id, finished?.id)
        assertEquals(p2.id, nextItem?.postId)
        assertEquals(com.example.util.DownloadItemStatus.DOWNLOADING, nextItem?.status)

        com.example.util.OfflineDownloadQueueManager.clearPendingQueue()
    }

    @Test
    fun testSeriesEpisodeHelper_heterogeneousChaptersAndSeasonsOrder() {
        val author = FamilyMember(id = 1, name = "Admin", role = "Admin")
        val rawTitles = listOf(
            "7",
            "0006",
            "Cap 01 (Temporada 2)",
            "t1x05",
            "3",
            "Cap 12 Parte 2",
            "cap 01",
            "004",
            "Cap 08 (Temporada 1)",
            "Cap 12 Parte 1",
            "02",
            "Novela 2024 - 10 [1080p x264]",
            "t1x09 [720p]"
        )

        val posts = rawTitles.mapIndexed { idx, title ->
            Post(
                id = 100 + idx,
                author = author,
                contentText = title,
                createdAt = 10_000_000L - (idx * 1000L), // Desordenado cronológicamente a propósito
                groupId = 9
            )
        }

        val sorted = posts.sortedWith(com.example.util.SeriesEpisodeHelper.seriesComparator())
        val sortedTitles = sorted.map { it.contentText }

        assertEquals(
            listOf(
                "cap 01",
                "02",
                "3",
                "004",
                "t1x05",
                "0006",
                "7",
                "Cap 08 (Temporada 1)",
                "t1x09 [720p]",
                "Novela 2024 - 10 [1080p x264]",
                "Cap 12 Parte 1",
                "Cap 12 Parte 2",
                "Cap 01 (Temporada 2)"
            ),
            sortedTitles
        )

        assertEquals("Cap. 1", com.example.util.SeriesEpisodeHelper.formatEpisodeBadge(sorted[0], 0))
        assertEquals("Temp. 1 · Ep. 5", com.example.util.SeriesEpisodeHelper.formatEpisodeBadge(sorted[4], 4))
        assertEquals("Cap. 12 · Pt. 2", com.example.util.SeriesEpisodeHelper.formatEpisodeBadge(sorted[11], 11))
        assertEquals("Temp. 2 · Ep. 1", com.example.util.SeriesEpisodeHelper.formatEpisodeBadge(sorted[12], 12))
    }

    @Test
    fun testSearchHelper_tokenizedOutOfOrderAccentsAndNumericEquivalence() {
        val mama = FamilyMember(id = 2, name = "Mamá", role = "Mamá")
        val papa = FamilyMember(id = 3, name = "Papá", role = "Papá")

        val pBeach = Post(
            id = 1,
            author = mama,
            contentText = "Vacaciones en Familia por la Playa",
            createdAt = 3000L
        )
        val pEpisode = Post(
            id = 2,
            author = papa,
            contentText = "Novela Familiar - cap 01 [1080p]",
            groupId = 7,
            groupName = "Novelas",
            createdAt = 2000L
        )
        val pOther = Post(
            id = 3,
            author = papa,
            contentText = "Receta de cocina rápida",
            createdAt = 1000L
        )

        // Búsqueda desordenada y sin tildes
        val outOfOrder = com.example.util.SearchHelper.filterAndRankPosts(
            posts = listOf(pOther, pEpisode, pBeach),
            query = "playa familia"
        )
        assertEquals(listOf(1), outOfOrder.map { it.id })

        // Equivalencia numérica ("cap 1" debe encontrar "cap 01") + tildes ("mamá" -> "mama")
        val numericMatch = com.example.util.SearchHelper.filterAndRankPosts(
            posts = listOf(pOther, pEpisode, pBeach),
            query = "cap 1"
        )
        assertEquals(listOf(2), numericMatch.map { it.id })

        val accentMemberMatch = com.example.util.SearchHelper.filterAndRankPosts(
            posts = listOf(pOther, pEpisode, pBeach),
            query = "playa mama"
        )
        assertEquals(listOf(1), accentMemberMatch.map { it.id })

        // Resaltado de términos tolerante a tildes y mayúsculas
        val highlighted = com.example.util.SearchHelper.buildHighlightedText(
            text = "Capítulo 1 con Mamá en la playa",
            query = "capitulo mama",
            highlightColor = androidx.compose.ui.graphics.Color.Blue,
            highlightBackground = androidx.compose.ui.graphics.Color.Yellow
        )
        assertEquals(2, highlighted.spanStyles.size)
    }

    @Test
    fun testFeedStreamHelper_continueSeriesSkipsWatchedEpisodes() {
        val self = FamilyMember(id = 1, name = "Yo", role = "Admin")
        val seriesGroup = FamilyGroup(
            id = 88,
            name = "Telenovela",
            folderPath = "/volume1/video/novela",
            organizationMode = GroupOrganizationMode.SERIES
        )
        val ep1 = Post(
            id = 301,
            author = self,
            groupId = 88,
            groupName = "Telenovela",
            contentText = "Cap 01",
            createdAt = 100L,
            mediaItems = listOf(MediaItem(id = "v1", type = MediaType.VIDEO, url = "http://nas/ep1.mp4"))
        )
        val ep2 = Post(
            id = 302,
            author = self,
            groupId = 88,
            groupName = "Telenovela",
            contentText = "Cap 02",
            createdAt = 200L,
            mediaItems = listOf(MediaItem(id = "v2", type = MediaType.VIDEO, url = "http://nas/ep2.mp4"))
        )

        val entries = FeedStreamHelper.buildSmartFeedEntries(
            posts = listOf(ep2, ep1),
            shorts = emptyList(),
            groups = listOf(seriesGroup),
            followedGroupIds = setOf(88),
            watchedPostIds = setOf(301),
            sessionSeed = 42L
        )

        val continueEntry = entries.filterIsInstance<FeedStreamEntry.ContinueSeriesEntry>().firstOrNull()
        assertEquals(302, continueEntry?.nextPost?.id)
    }

    @Test
    fun testOfflineMediaManager_formatBytesFormatting() {
        assertEquals("0 KB", OfflineMediaManager.formatBytes(0L))
        assertTrue(OfflineMediaManager.formatBytes(2048L).endsWith("KB"))
        assertTrue(OfflineMediaManager.formatBytes(5L * 1024L * 1024L).endsWith("MB"))
        assertTrue(OfflineMediaManager.formatBytes(2L * 1024L * 1024L * 1024L).endsWith("GB"))
    }
}


