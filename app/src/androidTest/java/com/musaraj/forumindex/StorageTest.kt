package com.musaraj.forumindex

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var preferences: ForumIndexPreferences
    private lateinit var tokens: SecureTokenStore

    @Before fun setUp() {
        context.getSharedPreferences("forum_index", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("forum_index_secure", Context.MODE_PRIVATE).edit().clear().commit()
        preferences = ForumIndexPreferences(context)
        tokens = SecureTokenStore(context)
    }

    @After fun tearDown() {
        context.getSharedPreferences("forum_index", Context.MODE_PRIVATE).edit().clear().commit()
        tokens.delete()
    }

    @Test fun appearanceAndContributionChoicesSurviveNewStore() {
        assertEquals(AppAppearance.AUTO, preferences.appearance)
        preferences.appearance = AppAppearance.DARK
        preferences.contributionsOptedOut = true
        preferences.contributorDisplayName = "Reader-12345678"
        val restored = ForumIndexPreferences(context)
        assertEquals(AppAppearance.DARK, restored.appearance)
        assertTrue(restored.contributionsOptedOut)
        assertEquals("Reader-12345678", restored.contributorDisplayName)
    }

    @Test fun preferencesRoundTripUserOwnedCollectionsAndDeviceName() {
        val stars = listOf(
            StarredTopic("Title \"one\"", "https://forum.test/t/1", "Forum", 11, forumId = 3),
            StarredTopic("Two", "https://other.test/t/2", "Other", 22),
            StarredTopic("Linked reply", "https://forum.test/t/other/99/7#reply", "Forum"),
        )
        preferences.visibleSubjectOrder = listOf("main-feed", "parent:ai")
        preferences.stars = stars
        preferences.openedTopicIds = setOf("3:11", "4:22")
        preferences.deviceNameOverride = "Pixel Test"

        val restored = ForumIndexPreferences(context)
        assertEquals(listOf("main-feed", "parent:ai"), restored.visibleSubjectOrder)
        assertEquals(stars, restored.stars)
        assertEquals(setOf("3:11", "4:22"), restored.openedTopicIds)
        assertEquals("Pixel Test", restored.deviceNameOverride)
        val stored = context.getSharedPreferences("forum_index", Context.MODE_PRIVATE).all
        assertTrue(stored.values.all { it is String })
        assertTrue((stored["stars"] as String).startsWith("["))
    }

    @Test fun openedCompositeHelperUsesForumAndTopicIds() {
        preferences.markOpened(7, 41)
        assertTrue(preferences.isOpened(7, 41))
        assertFalse(preferences.isOpened(8, 41))
        assertEquals(setOf("7:41"), preferences.openedTopicIds)
    }

    @Test fun tokenIsEncryptedAndEnrollmentRoundTrips() {
        val enrollment = enrollment("plain-secret-token")
        tokens.save(enrollment)

        assertEquals(enrollment, SecureTokenStore(context).load())
        val raw = context.getSharedPreferences("forum_index_secure", Context.MODE_PRIVATE).all
        val blob = raw["token"] as String
        assertFalse(blob.contains(enrollment.token))
        assertFalse(raw.toString().contains(enrollment.token))
        assertTrue(android.util.Base64.decode(blob, android.util.Base64.NO_WRAP).size > 12)
    }

    @Test fun deleteRemovesTokenAndEnrollmentState() {
        tokens.save(enrollment("delete-me"))
        tokens.delete()
        assertNull(tokens.load())
        assertTrue(context.getSharedPreferences("forum_index_secure", Context.MODE_PRIVATE).all.isEmpty())
    }

    @Test fun corruptOrMissingCiphertextSignsOutAndDeletesBadState() {
        val raw = context.getSharedPreferences("forum_index_secure", Context.MODE_PRIVATE)
        raw.edit().putString("token", "not-base64").putString("installation", "{\"public_id\":\"A1\"}").commit()
        assertNull(tokens.load())
        assertTrue(raw.all.isEmpty())

        raw.edit().putString("installation", "orphaned metadata").commit()
        assertNull(tokens.load())
        assertTrue(raw.all.isEmpty())
    }

    @Test fun wrongPreferenceTypesAreClearedInsteadOfCrashing() {
        val regular = context.getSharedPreferences("forum_index", Context.MODE_PRIVATE)
        regular.edit().putInt("subjects", 1).putInt("stars", 2).putInt("opened", 3).putInt("device_name", 4).commit()
        val restored = ForumIndexPreferences(context)
        assertTrue(restored.visibleSubjectOrder.isEmpty())
        assertTrue(restored.stars.isEmpty())
        assertTrue(restored.openedTopicIds.isEmpty())
        assertNull(restored.deviceNameOverride)
        assertTrue(regular.all.isEmpty())

        val secure = context.getSharedPreferences("forum_index_secure", Context.MODE_PRIVATE)
        secure.edit().putInt("token", 1).putInt("installation", 2).commit()
        assertNull(tokens.load())
        assertTrue(secure.all.isEmpty())
    }

    private fun enrollment(token: String) = Enrollment(
        token,
        Installation("A1", "Display", "Device", deviceVerified = false, trusted = true),
    )
}
