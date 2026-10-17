package com.musaraj.forumindex

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.Instant

internal object BookmarkActivity {
    fun endpoint(value: String): URL? = try {
        val url = validTopicUrl(URL(value))
        val parts = url?.path?.split('/')?.filter { it.isNotEmpty() }.orEmpty()
        val index = parts.indexOf("t")
        val id = if (index >= 0) parts.drop(index + 1).firstOrNull { it.toLongOrNull() != null } else null
        if (url == null || id == null) null else URI("https", null, url.host, url.port,
            "/" + (parts.take(index) + listOf("t", "$id.json")).joinToString("/"), null, null).toURL()
    } catch (_: Exception) { null }

    fun latestReply(value: String): Instant? {
        var url = endpoint(value) ?: return null
        repeat(6) {
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Accept", "application/json")
                if (connection.responseCode in listOf(301, 302, 303, 307, 308)) {
                    url = connection.getHeaderField("Location")?.let { resolveTopicUrl(url, it) } ?: return null
                } else {
                    if (connection.responseCode !in 200..299) return null
                    val bytes = connection.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (output.size() <= 2 * 1024 * 1024) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    if (bytes.size > 2 * 1024 * 1024) return null
                    return JSONObject(String(bytes, Charsets.UTF_8)).optString("last_posted_at").takeIf { it.isNotEmpty() }?.let(Instant::parse)
                }
            } finally { connection.disconnect() }
        }
        return null
    }
}
