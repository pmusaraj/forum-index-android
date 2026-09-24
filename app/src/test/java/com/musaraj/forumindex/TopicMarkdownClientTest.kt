package com.musaraj.forumindex

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class TopicMarkdownClientTest {
    private val url = URL("https://example.com/t/123")

    @Test fun negotiatesMarkdownWithoutContributorCredentialsAndAcceptsCharset() {
        val connection = FakeConnection(url)
        val page = TopicMarkdownClient { connection }.fetchBlocking(url)
        assertEquals("# Hello", page.markdown)
        assertEquals(mapOf("Accept" to "text/markdown"), connection.headers)
        assertEquals(url, page.url)
        assertEquals(10_000, connection.connectTimeout)
        assertEquals(15_000, connection.readTimeout)
        assertTrue(connection.disconnected)
    }

    @Test fun redirectsToMigratedHttpsDomainAndReturnsFinalUrl() {
        val migrated = URL("https://new.example/t/123")
        val connections = mutableListOf<FakeConnection>()
        val client = TopicMarkdownClient { requested ->
            FakeConnection(requested, status = if (requested == url) 301 else 200, location = migrated.toString()).also(connections::add)
        }
        assertEquals(migrated, client.fetchBlocking(url).url)
        assertEquals(2, connections.size)
        assertTrue(connections.all { it.disconnected && !it.instanceFollowRedirects })
        assertTrue(connections.all { "Authorization" !in it.headers })
    }

    @Test fun unsupportedResponsesProduceFallbackErrors() {
        listOf(
            FakeConnection(url, type = "text/html"), FakeConnection(url, type = "text/plain"),
            FakeConnection(url, status = 403), FakeConnection(url, status = 500),
            FakeConnection(url, body = " \n\t".toByteArray()), FakeConnection(url, body = byteArrayOf(0xff.toByte())),
            FakeConnection(url, body = ByteArray(2 * 1024 * 1024 + 1) { 65 }),
        ).forEach { connection ->
            assertThrows(IOException::class.java) { TopicMarkdownClient { connection }.fetchBlocking(url) }
            assertTrue(connection.disconnected)
        }
    }

    @Test fun rejectsNonHttpsAndBoundsRedirects() {
        assertThrows(IOException::class.java) { TopicMarkdownClient { error("must not connect") }.fetchBlocking(URL("http://example.com/t/123")) }
        assertThrows(IOException::class.java) {
            TopicMarkdownClient { FakeConnection(it, status = 302, location = "http://example.com/t/123") }.fetchBlocking(url)
        }
        var calls = 0
        assertThrows(IOException::class.java) {
            TopicMarkdownClient { calls++; FakeConnection(it, status = 302, location = url.toString()) }.fetchBlocking(url)
        }
        assertEquals(6, calls)
    }

    private class FakeConnection(
        url: URL,
        private val body: ByteArray = "# Hello".toByteArray(),
        private val type: String = "text/markdown; charset=utf-8",
        private val status: Int = 200,
        private val location: String? = null,
    ) : HttpURLConnection(url) {
        val headers = mutableMapOf<String, String>()
        var disconnected = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getContentType() = type
        override fun getHeaderField(name: String): String? = if (name == "Location") location else null
        override fun getInputStream() = body.inputStream()
        override fun setRequestProperty(key: String, value: String) { headers[key] = value }
    }
}
