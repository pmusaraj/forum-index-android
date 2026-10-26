package com.musaraj.forumindex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.Instant

internal fun StarredTopic.recordProgress(number: Int?, date: Long?, latest: Long? = null): StarredTopic {
    val canAdvance = lastReadPostNumber != null || date == null || date > lastReadAt
    return copy(
        lastReadPostNumber = if (number != null && canAdvance) maxOf(lastReadPostNumber ?: 0, number) else lastReadPostNumber,
        lastReadAt = maxOf(lastReadAt, date ?: 0, latest ?: 0),
    )
}

/** Reads public forum JSON with no installation credentials. */
internal class TopicResumeClient(private val fetch: suspend (URL) -> JSONObject = { fetchJSON(it) }) {
    data class Target(val pageUrl: URL, val postNumber: Int?, val readThrough: Int, val latestReplyAt: Long?)

    suspend fun target(bookmark: StarredTopic): Target {
        val endpoint = BookmarkActivity.endpoint(bookmark.url) ?: throw IOException("Unsupported bookmark")
        val topic = fetch(endpoint)
        val stream = topic.getJSONObject("post_stream")
        val ids = stream.getJSONArray("stream")
        val chunk = topic.getInt("chunk_size")
        if (ids.length() == 0 || chunk <= 0) throw IOException("Invalid topic stream")
        val cache = mutableMapOf<Int, JSONObject>()
        stream.getJSONArray("posts").let { posts -> repeat(posts.length()) { cache[posts.getJSONObject(it).getInt("id")] = posts.getJSONObject(it) } }
        suspend fun post(index: Int): JSONObject {
            currentCoroutineContext().ensureActive()
            val id = ids.getInt(index)
            return cache[id] ?: run {
                val url = URI("https", null, endpoint.host, endpoint.port, endpoint.path.removeSuffix(".json") + "/posts.json", "post_ids[]=$id", null).toURL()
                val posts = fetch(url).getJSONObject("post_stream").getJSONArray("posts")
                val found = (0 until posts.length()).map { posts.getJSONObject(it) }.firstOrNull { it.getInt("id") == id }
                    ?: throw IOException("Topic changed while finding unread replies")
                cache[id] = found
                found
            }
        }
        var low = 0
        var high = ids.length()
        while (low < high) {
            val middle = (low + high) / 2
            val item = post(middle)
            val unread = bookmark.lastReadPostNumber?.let { item.getInt("post_number") > it }
                ?: (Instant.parse(item.getString("created_at")).toEpochMilli() > bookmark.lastReadAt)
            if (unread) high = middle else low = middle + 1
        }
        val previous = if (low > 0) post(low - 1).getInt("post_number") else 0
        val number = if (low < ids.length()) post(low).getInt("post_number") else null
        val page = if (number == null) 1 else low / chunk + 1
        val parts = URL(bookmark.url).path.split('/').filter { it.isNotEmpty() }
        val topicIndex = parts.indexOf("t")
        val idIndex = (topicIndex + 1 until parts.size).first { parts[it].toLongOrNull() != null }
        val path = "/" + parts.take(idIndex + 1).joinToString("/") + ".md"
        val url = URI("https", null, endpoint.host, endpoint.port, path, "page=$page", null).toURL()
        return Target(url, number, maxOf(previous, bookmark.lastReadPostNumber ?: 0),
            topic.optString("last_posted_at").takeIf { it.isNotEmpty() && it != "null" }?.let { Instant.parse(it).toEpochMilli() })
    }

    companion object {
        suspend fun fetchJSON(initial: URL): JSONObject = withContext(Dispatchers.IO) {
            var url = validTopicUrl(initial) ?: throw IOException("Unsupported URL")
            repeat(6) {
                currentCoroutineContext().ensureActive()
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 15_000
                    connection.setRequestProperty("Accept", "application/json")
                    if (connection.responseCode in listOf(301, 302, 303, 307, 308)) {
                        url = connection.getHeaderField("Location")?.let { resolveTopicUrl(url, it) } ?: throw IOException("Invalid redirect")
                    } else {
                        if (connection.responseCode !in 200..299) throw IOException("Topic unavailable")
                        val bytes = connection.inputStream.use { input ->
                            val out = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (out.size() + count > 2 * 1024 * 1024) throw IOException("Topic too large")
                                out.write(buffer, 0, count)
                            }
                            out.toByteArray()
                        }
                        return@withContext JSONObject(String(bytes, Charsets.UTF_8))
                    }
                } finally { connection.disconnect() }
            }
            throw IOException("Too many redirects")
        }
    }
}
