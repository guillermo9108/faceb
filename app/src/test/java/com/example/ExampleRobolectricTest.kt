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
}
