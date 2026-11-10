package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.NasConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ExampleRobolectricTest {

    @Test
    fun testApplicationContext_available() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNotNull(context)
        assertEquals("com.example", context.packageName)
    }

    @Test
    fun testNasConfiguration_defaultValues() {
        val config = NasConfig()
        assertEquals("192.168.43.101", config.ip)
        assertEquals(80, config.port)
        assertEquals("/family_api", config.path)
        assertEquals(3307, config.dbPort)
    }
}
