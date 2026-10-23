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
}

