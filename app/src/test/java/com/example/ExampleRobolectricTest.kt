package com.example

import com.example.model.NasConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleRobolectricTest {

    @Test
    fun testNasConfiguration_defaultValues() {
        val config = NasConfig()
        assertEquals("192.168.43.101", config.ip)
        assertEquals(80, config.port)
        assertEquals("/family_api", config.path)
        assertEquals(3307, config.dbPort)
    }
}
