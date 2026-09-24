package com.musaraj.forumindex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URL

class TopicUrlPolicyTest {
    @Test fun acceptsOnlySecureInitialTopicUrls() {
        assertEquals("https://forum.example/t/1", validTopicUrl(URL("https://forum.example/t/1"))?.toString())
        assertNull(validTopicUrl(URL("http://forum.example/t/1")))
        assertNull(validTopicUrl(URL("file:/tmp/topic")))
    }

    @Test fun routesHttpsRedirectsInsideAndTappedExternalLinksOutside() {
        val initial = URL("https://forum.example/t/1")
        assertEquals(TopicNavigation.IN_WEBVIEW, routeTopicNavigation(initial, URL("https://forum.example/t/2"), true))
        assertEquals(TopicNavigation.EXTERNAL, routeTopicNavigation(initial, URL("https://other.example/t/2"), true))
        assertEquals(TopicNavigation.IN_WEBVIEW, routeTopicNavigation(initial, URL("https://other.example/t/2"), false))
        assertEquals(TopicNavigation.REJECT, routeTopicNavigation(initial, URL("http://forum.example/t/2"), true))
        assertEquals(TopicNavigation.REJECT, routeTopicNavigation(initial, URL("mailto:test@example.com"), true))
    }
}
