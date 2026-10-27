package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.FamilyRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Muro Familiar LAN", appName)
    }

    @Test
    fun `repository initializes with real Room database and no mock posts`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FamilyRepository(context)

        assertNotNull(repo.currentMember.value)
        assertEquals("Carlos (Papá)", repo.currentMember.value.name)
        assertEquals(5, repo.familyMembers.value.size)
        // Starts with 0 posts until real sync from Synology NAS or user publication
        assertEquals(0, repo.posts.value.size)
    }

    @Test
    fun `zip exporter bundles all config files and supports custom database port`() {
        val customConfig = com.example.model.NasConfig(
            ip = "192.168.1.150",
            port = 8085,
            dbPort = 3306 // Puerto MySQL alternativo
        )

        val zipBytes = com.example.util.SynologyZipExporter.createZipBytes(customConfig)
        assertTrue("ZIP bytes should not be empty", zipBytes.isNotEmpty())

        val entries = mutableMapOf<String, String>()
        val zis = java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(zipBytes))
        var entry = zis.nextEntry
        while (entry != null) {
            val content = zis.readBytes().toString(Charsets.UTF_8)
            entries[entry.name] = content
            entry = zis.nextEntry
        }
        zis.close()

        assertTrue(entries.containsKey("schema.sql"))
        assertTrue(entries.containsKey("config.php"))
        assertTrue(entries.containsKey("index.php"))
        assertTrue(entries.containsKey(".htaccess"))
        assertTrue(entries.containsKey("LEEME_INSTRUCCIONES.txt"))

        val configPhp = entries["config.php"] ?: ""
        assertTrue("config.php must use the custom DB port 3306", configPhp.contains("define('DB_PORT', '3306');"))
    }

    @Test
    fun `nas config persists across repository re-initialization`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo1 = FamilyRepository(context)

        val newConfig = com.example.model.NasConfig(
            ip = "192.168.1.222",
            port = 9090,
            path = "/family_nas",
            dbPort = 3308,
            dbHost = "192.168.1.200",
            dbName = "family_custom_db",
            dbUser = "familia_admin",
            dbPass = "SuperClave123"
        )

        repo1.updateNasConfig(newConfig)

        // Verify repo1 has the updated config
        assertEquals("192.168.1.222", repo1.nasConfig.value.ip)
        assertEquals(9090, repo1.nasConfig.value.port)
        assertEquals("familia_admin", repo1.nasConfig.value.dbUser)
        assertEquals("SuperClave123", repo1.nasConfig.value.dbPass)

        // Re-initialize repository simulating app restart
        val repo2 = FamilyRepository(context)
        assertEquals("192.168.1.222", repo2.nasConfig.value.ip)
        assertEquals(9090, repo2.nasConfig.value.port)
        assertEquals("/family_nas", repo2.nasConfig.value.path)
        assertEquals(3308, repo2.nasConfig.value.dbPort)
        assertEquals("192.168.1.200", repo2.nasConfig.value.dbHost)
        assertEquals("family_custom_db", repo2.nasConfig.value.dbName)
        assertEquals("familia_admin", repo2.nasConfig.value.dbUser)
        assertEquals("SuperClave123", repo2.nasConfig.value.dbPass)
    }

    @Test
    fun `zip exporter includes custom dbUser and dbPass in config php`() {
        val customConfig = com.example.model.NasConfig(
            ip = "192.168.1.150",
            port = 8085,
            dbPort = 3306,
            dbUser = "usuario_synology",
            dbPass = "MiClaveSecreta99"
        )

        val zipBytes = com.example.util.SynologyZipExporter.createZipBytes(customConfig)
        val entries = mutableMapOf<String, String>()
        val zis = java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(zipBytes))
        var entry = zis.nextEntry
        while (entry != null) {
            val content = zis.readBytes().toString(Charsets.UTF_8)
            entries[entry.name] = content
            entry = zis.nextEntry
        }
        zis.close()

        val configPhp = entries["config.php"] ?: ""
        assertTrue("config.php must contain DB_USER", configPhp.contains("define('DB_USER', 'usuario_synology');"))
        assertTrue("config.php must contain DB_PASS", configPhp.contains("define('DB_PASS', 'MiClaveSecreta99');"))

        val schemaSql = entries["schema.sql"] ?: ""
        assertTrue("schema.sql must contain stories table", schemaSql.contains("CREATE TABLE IF NOT EXISTS stories"))

        val indexPhp = entries["index.php"] ?: ""
        assertTrue("index.php must contain comments endpoint", indexPhp.contains("/comments"))
        assertTrue("index.php must contain stories endpoint", indexPhp.contains("/stories"))
        assertTrue("index.php must contain thumbnail handling", indexPhp.contains("thumbnail"))
    }

    @Test
    fun `video helper formats duration accurately`() {
        assertEquals("00:00", com.example.util.VideoHelper.formatDuration(0L))
        assertEquals("00:45", com.example.util.VideoHelper.formatDuration(45_000L))
        assertEquals("01:24", com.example.util.VideoHelper.formatDuration(84_000L))
        assertEquals("10:05", com.example.util.VideoHelper.formatDuration(605_000L))
    }

    @Test
    fun `session persistence remembers logged in user and survives restart`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo1 = FamilyRepository(context)

        // Login as Mateo with rememberSession = true
        val result = repo1.login("mateo", "1234", rememberSession = true)
        assertTrue("Login should succeed", result.isSuccess)
        assertTrue(repo1.isUserLoggedIn.value)
        assertEquals("Mateo", repo1.currentMember.value.name)

        // Re-create repository simulating app kill & restart
        val repo2 = FamilyRepository(context)
        assertTrue("Session should be restored upon app restart", repo2.isUserLoggedIn.value)
        assertEquals("Mateo", repo2.currentMember.value.name)

        // Now logout
        repo2.logout()
        org.junit.Assert.assertFalse("User should be logged out", repo2.isUserLoggedIn.value)

        // Re-create again, session should stay logged out
        val repo3 = FamilyRepository(context)
        org.junit.Assert.assertFalse("Session should stay logged out", repo3.isUserLoggedIn.value)
    }

    @Test
    fun `profile update updates nick and avatar emoji`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FamilyRepository(context)

        repo.login("carlos", "1234", rememberSession = false)
        val updateRes = repo.updateProfile("Papá Genial 🚀", "🚀", 0xFF1877F2L, "Bio genial", null)
        assertTrue(updateRes.isSuccess)
        assertEquals("Papá Genial 🚀", repo.currentMember.value.name)
        assertEquals("🚀", repo.currentMember.value.avatarEmoji)
    }

    @Test
    fun `synology backend scripts include auth, shorts, and profile update endpoints`() {
        val config = com.example.model.NasConfig()
        val zipBytes = com.example.util.SynologyZipExporter.createZipBytes(config)
        val entries = mutableMapOf<String, String>()
        val zis = java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(zipBytes))
        var entry = zis.nextEntry
        while (entry != null) {
            val content = zis.readBytes().toString(Charsets.UTF_8)
            entries[entry.name] = content
            entry = zis.nextEntry
        }
        zis.close()

        val indexPhp = entries["index.php"] ?: ""
        assertTrue("index.php must contain auth login", indexPhp.contains("/auth/login"))
        assertTrue("index.php must contain auth register", indexPhp.contains("/auth/register"))
        assertTrue("index.php must contain profile update", indexPhp.contains("/profile/update"))
        assertTrue("index.php must contain shorts endpoint", indexPhp.contains("/shorts"))

        val schemaSql = entries["schema.sql"] ?: ""
        assertTrue("schema.sql must contain is_short column", schemaSql.contains("is_short"))
    }
}

