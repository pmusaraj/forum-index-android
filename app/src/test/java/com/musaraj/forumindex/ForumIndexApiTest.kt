package com.musaraj.forumindex

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

class ForumIndexApiTest {
    @Test fun destinationsNeverCollideWithMagicMainSlug() {
        val subject = Subject(1, "Main", "main", "", 0, false, 5)
        assertEquals("main-feed", Destination.Main.id)
        assertEquals("parent:main", Destination.Subject(subject).id)
        assertNotEquals(Destination.Main.id, Destination.Subject(subject).id)
    }

    @Test fun buildsExactProductionReadUrls() {
        val api = HttpForumIndexApi(connectionFactory = { FakeConnection(it) })
        assertEquals("https://do3.musaraj.com/api/v2/subjects?window=5d&language=en", api.taxonomyUrl().toString())
        assertEquals("https://do3.musaraj.com/api/v2/topics?feed=for_you&limit=30&page=2&language=en", api.personalizedTopicsUrl(2).toString())
        assertEquals("https://do3.musaraj.com/api/v2/subjects/caf%C3%A9%20news/topics?feed=trending&limit=30&page=3&language=en", api.subjectTopicsUrl("café news", 3).toString())
    }

    @Test fun rejectsUnsafeBasesPagesAndSlugs() {
        listOf("http://example.com", "https://u:p@example.com", "https://example.com?q=1", "https://example.com#x", "https:///x")
            .forEach { base -> assertThrows(IllegalArgumentException::class.java) { HttpForumIndexApi(base) } }
        val api = HttpForumIndexApi(connectionFactory = { FakeConnection(it) })
        assertThrows(IllegalArgumentException::class.java) { api.mainTopicsUrl(0) }
        listOf("", " ", ".", "..", "a/b", "a\\b", "a%b", "a?b", "a#b", "a:b", "a&b")
            .forEach { slug -> assertThrows(IllegalArgumentException::class.java) { api.subjectTopicsUrl(slug, 1) } }
    }

    @Test fun decodesTaxonomyDefaults() {
        val value = ForumIndexJson.navigation("""{"version":"v2","window":"5d","subjects":[{"id":7,"name":"AI","slug":"ai","topic_count":9}]}""")
        assertEquals(NavigationEnvelope("v2", "5d", listOf(Subject(7, "AI", "ai", "", 0, false, 9))), value)
    }

    @Test fun decodesAndSanitizesTopics() {
        val json = """{"count":2,"results":[
          {"id":1,"title":"One","url":"https://forum.test/t/1","excerpt":"   ","published_at":"2026-09-08T10:11:12.345Z","engagement":{"replies":-4},"forum":{"id":3,"name":"Forum","slug":"forum","base_url":"https://forum.test","icon_url":"http://forum.test/icon.png"}},
          {"id":2,"title":"Two","url":"http://forum.test/t/2","excerpt":" kept ","published_at":"2026-09-08T10:11:12Z","forum":{"id":4,"name":"Other","slug":"other","base_url":"javascript:bad","icon_url":"https://cdn.test/i.png"}}
        ]}"""
        val feed = ForumIndexJson.feed(json)
        assertEquals(2, feed.count)
        assertNull(feed.results[0].excerpt)
        assertEquals(0, feed.results[0].replyCount)
        assertEquals(Instant.parse("2026-09-08T10:11:12.345Z"), feed.results[0].publishedAt)
        assertNull(feed.results[0].forum.iconUrl)
        assertNull(feed.results[1].url)
        assertEquals(" kept ", feed.results[1].excerpt)
        assertEquals(Instant.parse("2026-09-08T10:11:12Z"), feed.results[1].publishedAt)
        assertNull(feed.results[1].forum.baseUrl)
        assertEquals("https://cdn.test/i.png", feed.results[1].forum.iconUrl.toString())
    }

    @Test fun personalizedFeedUsesBearerCredential() {
        lateinit var call: FakeConnection
        val api = HttpForumIndexApi(connectionFactory = { FakeConnection(it, "{\"count\":0,\"results\":[]}").also { value -> call = value } })

        api.fetchPersonalizedTopics(1, "reader-token")

        assertEquals("for_you", call.url.query.split("&").associate { it.substringBefore("=") to it.substringAfter("=") }.getValue("feed"))
        assertEquals("Bearer reader-token", call.headers["Authorization"])
    }

    @Test fun trustedForumSubmissionUsesAuthenticatedEndpoint() {
        lateinit var call: FakeConnection
        val api = HttpForumIndexApi(connectionFactory = { FakeConnection(it, "{\"forum_id\":1,\"status\":\"candidate\"}").also { value -> call = value } })

        api.submitForum("https://community.example.com", "reader-token")

        assertEquals("POST", call.requestMethod)
        assertEquals("/api/v2/forum-submission", call.url.path)
        assertEquals("Bearer reader-token", call.headers["Authorization"])
        assertEquals(mapOf("url" to "https://community.example.com"), call.sentJson())
    }

    @Test fun untrustedForumSubmissionDoesNotExpireBearer() {
        val api = HttpForumIndexApi(connectionFactory = { FakeConnection(it, "{}", 403) })

        val error = assertThrows(ForumIndexApiException.Http::class.java) {
            api.submitForum("https://community.example.com", "reader-token")
        }

        assertEquals(403, error.status)
    }

    @Test fun contributionRequestsUseExactMethodsPathsBodiesAndHeaders() {
        val calls = mutableListOf<FakeConnection>()
        val responses = ArrayDeque(listOf(
            """{"token":"secret","installation":{"public_id":"A1","display_name":"Name","device_name":"Phone","device_verified":false,"trusted":true}}""",
            """{"installation":{"public_id":"A1","display_name":"Name","device_name":"Phone 2","device_verified":false,"trusted":true}}""",
            """{"installation":{"public_id":"A1","display_name":"Name","device_name":"Phone 2","device_verified":false,"trusted":true}}""",
            "{}", "{}"
        ))
        val api = HttpForumIndexApi(connectionFactory = { url -> FakeConnection(url, responses.removeFirst()).also(calls::add) })
        val enrollment = api.createInstallation("Name", "Phone")
        api.fetchInstallation("token")
        api.updateInstallation("Phone 2", "token")
        api.setTopicAction(42, "starred", true, "token")
        api.deleteInstallation("token")

        assertEquals("secret", enrollment.token)
        assertEquals(listOf("POST", "GET", "PATCH", "PUT", "DELETE"), calls.map { it.requestMethod })
        assertEquals(listOf("/api/v2/installations", "/api/v2/installation", "/api/v2/installation", "/api/v2/topics/42/action", "/api/v2/installation"), calls.map { it.url.path })
        assertEquals(mapOf("display_name" to "Name", "device_name" to "Phone"), calls[0].sentJson())
        assertEquals(mapOf("device_name" to "Phone 2"), calls[2].sentJson())
        assertEquals(mapOf("kind" to "starred", "active" to true), calls[3].sentJson())
        assertTrue(calls.all { it.headers["Accept"] == "application/json" })
        assertEquals("application/json", calls[0].headers["Content-Type"])
        assertNull(calls[1].headers["Content-Type"])
        assertEquals("Bearer token", calls[1].headers["Authorization"])
    }

    @Test fun rejectsInvalidBearerBeforeOpeningConnection() {
        var opened = false
        val api = HttpForumIndexApi(connectionFactory = { opened = true; FakeConnection(it) })
        listOf("", " ", "abc\ndef", "abc" + 0.toChar() + "def").forEach { token ->
            assertThrows(IllegalArgumentException::class.java) { api.fetchInstallation(token) }
        }
        assertFalse(opened)
    }

    @Test fun authenticatedUnauthorizedIsTypedAndErrorsLeakNoSecretsOrBodies() {
        val api = HttpForumIndexApi(connectionFactory = { FakeConnection(it, "response-secret", 401) })
        val expired = assertThrows(ForumIndexApiException.ExpiredAccess::class.java) { api.fetchInstallation("bearer-secret") }
        assertFalse(expired.toString().contains("bearer-secret"))
        assertFalse(expired.toString().contains("response-secret"))
        val publicApi = HttpForumIndexApi(connectionFactory = { FakeConnection(it, "response-secret", 500) })
        val failure = assertThrows(ForumIndexApiException.Http::class.java) { publicApi.fetchTaxonomy() }
        assertEquals(500, failure.status)
        assertTrue(failure.message!!.length <= 100)
        assertFalse(failure.toString().contains("response-secret"))
    }

    @Test fun malformedResponsesAreTypedAndDoNotLeakBodyValues() {
        val api = HttpForumIndexApi(connectionFactory = { FakeConnection(it, "{\"subjects\":\"private-value\"}") })
        val failure = assertThrows(ForumIndexApiException.InvalidResponse::class.java) { api.fetchTaxonomy() }
        assertFalse(failure.toString().contains("private-value"))
    }

    @Test fun appliesBoundedConnectionTimeouts() {
        lateinit var connection: FakeConnection
        val api = HttpForumIndexApi(connectionFactory = {
            FakeConnection(it, "{\"version\":\"v2\",\"window\":\"5d\",\"subjects\":[]}").also { value -> connection = value }
        })
        api.fetchTaxonomy()
        assertEquals(10_000, connection.connectTimeout)
        assertEquals(15_000, connection.readTimeout)
    }

    private class FakeConnection(url: URL, private val response: String = "{}", private val status: Int = 200) : HttpURLConnection(url) {
        private val sent = java.io.ByteArrayOutputStream()
        val headers = linkedMapOf<String, String>()
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getInputStream() = response.byteInputStream()
        override fun getErrorStream() = response.byteInputStream()
        override fun getOutputStream() = sent
        override fun setRequestMethod(method: String) { this.method = method }
        override fun setRequestProperty(key: String, value: String) { headers[key] = value }
        fun sentBody() = sent.toString(Charsets.UTF_8.name())
        fun sentJson(): Map<String, Any> = JSONObject(sentBody()).let { json -> json.keys().asSequence().associateWith(json::get) }
    }
}
