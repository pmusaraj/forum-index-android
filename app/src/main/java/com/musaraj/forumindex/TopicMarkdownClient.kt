package com.musaraj.forumindex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

internal data class MarkdownPage(val markdown: String, val url: URL)

internal fun interface TopicMarkdownSource {
    suspend fun fetch(url: URL): MarkdownPage
}

/** Separate from the index API: never forwards contributor credentials to forums. */
internal class TopicMarkdownClient(
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) : TopicMarkdownSource {
    override suspend fun fetch(url: URL): MarkdownPage = withContext(Dispatchers.IO) { fetchBlocking(url) }

    internal fun fetchBlocking(url: URL): MarkdownPage {
        var current = validTopicUrl(url) ?: throw IOException("Unsupported topic URL")
        repeat(6) {
            val connection = connectionFactory(current)
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Accept", "text/markdown")
                val status = connection.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    current = connection.getHeaderField("Location")?.let { resolveTopicUrl(current, it) }
                        ?: throw IOException("Unsupported topic redirect")
                } else {
                    if (status !in 200..299 || connection.contentType?.substringBefore(';')?.trim()?.lowercase() != "text/markdown") {
                        throw IOException("Markdown preview unavailable")
                    }
                    val bytes = connection.inputStream.use { stream ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val size = stream.read(buffer)
                            if (size < 0) break
                            if (output.size() + size > 2 * 1024 * 1024) throw IOException("Topic preview is too large")
                            output.write(buffer, 0, size)
                        }
                        output.toByteArray()
                    }
                    val markdown = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString()
                    if (markdown.isBlank()) throw IOException("Empty topic preview")
                    return MarkdownPage(markdown, current)
                }
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("Too many topic redirects")
    }
}
