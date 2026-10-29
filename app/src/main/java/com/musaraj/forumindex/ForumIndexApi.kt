package com.musaraj.forumindex

import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Instant

const val PRODUCTION_BASE_URL = "https://do3.musaraj.com"

data class NavigationEnvelope(val version: String, val window: String, val subjects: List<Subject>)
data class Subject(
    val id: Int,
    val name: String,
    val slug: String,
    val description: String,
    val position: Int,
    val secondary: Boolean,
    val topicCount: Int,
)
data class Topic(
    val id: Int,
    val title: String,
    val url: URL?,
    val excerpt: String?,
    val forum: ForumSummary,
    val publishedAt: Instant?,
    val replyCount: Int,
    val externalSignals: List<ExternalSignal> = emptyList(),
    val readerMarkdownUrl: URL? = if (forum.supportsMarkdown) url else null,
) {
    val usesMarkdownReader get() = readerMarkdownUrl != null
    val usesConvertedMarkdown get() = usesMarkdownReader && readerMarkdownUrl.toString() != url.toString()
}
data class ExternalSignal(val source: ExternalSignalSource, val url: URL)
enum class ExternalSignalSource(val apiValue: String, val displayName: String) {
    HACKER_NEWS("hacker_news", "Hacker News"),
    LOBSTERS("lobsters", "Lobsters"),
}

data class ForumSummary(
    val id: Int,
    val name: String,
    val slug: String,
    val baseUrl: URL?,
    val iconUrl: URL?,
    val supportsMarkdown: Boolean = false,
)
data class ForumDetail(val forum: ForumSummary, val description: String?, val siteUrl: URL?, val organizationUrl: URL?)

data class DiscoveredFeed(val id: String, val name: String, val description: String, val path: String, val personalized: Boolean)
data class FeedCatalog(val version: String = "v2", val feeds: List<DiscoveredFeed> = emptyList())

data class FeedEnvelope(val count: Int, val results: List<Topic>)
data class Installation(
    val publicId: String,
    val displayName: String,
    val deviceName: String,
    val deviceVerified: Boolean,
    val trusted: Boolean,
    val readCount: Int? = null,
    val forYouEligible: Boolean = false,
)
data class Enrollment(val token: String, val installation: Installation)

sealed interface Destination {
    val id: String

    data class Discovered(val feed: DiscoveredFeed) : Destination { override val id = "feed:${feed.id}" }
    data object ForYou : Destination { override val id = "for-you-feed" }
    data object Main : Destination { override val id = "main-feed" }
    data class Subject(val subject: com.musaraj.forumindex.Subject) : Destination {
        override val id = "parent:${subject.slug}"
    }
}

interface ForumIndexApi {
    fun fetchFeeds(): FeedCatalog = FeedCatalog()
    fun fetchDiscoveredTopics(feed: DiscoveredFeed, page: Int, token: String?): FeedEnvelope { throw UnsupportedOperationException() }
    fun fetchTaxonomy(): NavigationEnvelope
    fun fetchMainTopics(page: Int): FeedEnvelope
    fun fetchPersonalizedTopics(page: Int, token: String?): FeedEnvelope = fetchMainTopics(page)
    fun fetchSubjectTopics(slug: String, page: Int): FeedEnvelope
    fun createInstallation(displayName: String, deviceName: String): Enrollment
    fun fetchInstallation(token: String): Installation
    fun updateInstallation(deviceName: String, token: String): Installation
    fun updateDisplayName(displayName: String, token: String): Installation { throw UnsupportedOperationException() }
    fun deleteInstallation(token: String)
    fun setTopicAction(topicId: Int, kind: String, active: Boolean, token: String)
    fun submitForum(url: String, token: String)
    fun submitFeedback(submission: FeedbackSubmission, token: String) { throw UnsupportedOperationException() }
}

sealed class ForumIndexApiException(message: String) : Exception(message) {
    class ExpiredAccess : ForumIndexApiException("Contributor access expired")
    class Http(val status: Int) : ForumIndexApiException("Request failed ($status)")
    class Transport : ForumIndexApiException("Network request failed")
    class InvalidResponse : ForumIndexApiException("Invalid server response")
}

class HttpForumIndexApi(
    baseUrl: String = PRODUCTION_BASE_URL,
    private val devBuild: Boolean = BuildConfig.DEBUG,
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) : ForumIndexApi {
    private val base = validateBase(baseUrl)

    internal fun taxonomyUrl() = url("/api/v2/subjects", "window=5d&language=en")
    internal fun mainTopicsUrl(page: Int): URL {
        require(page > 0) { "page must be positive" }
        return url("/api/v2/topics", "feed=trending&limit=30&page=$page&language=en")
    }
    internal fun personalizedTopicsUrl(page: Int): URL {
        require(page > 0) { "page must be positive" }
        return url("/api/v2/topics", "feed=for_you&limit=30&page=$page&language=en")
    }
    internal fun subjectTopicsUrl(slug: String, page: Int): URL {
        require(page > 0) { "page must be positive" }
        return url("/api/v2/subjects/${encodeSlug(slug)}/topics", "feed=trending&limit=30&page=$page&language=en")
    }

    internal fun discoveredTopicsUrl(feed: DiscoveredFeed, page: Int): URL {
        require(page > 0)
        val path = URI(feed.path)
        require(path.scheme == null && path.rawAuthority == null && path.fragment == null &&
            path.path.startsWith("/api/v2/") && !path.path.contains("..") && !path.path.contains('\\'))
        val query = path.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() &&
            java.net.URLDecoder.decode(it.substringBefore('='), "UTF-8") !in setOf("page", "limit", "language") }
        return url(path.rawPath, (query + listOf("page=$page", "limit=30", "language=en")).joinToString("&"))
    }

    override fun fetchFeeds(): FeedCatalog = decode { ForumIndexJson.feeds(request(url("/api/v2/feeds"), "GET")) }

    override fun fetchDiscoveredTopics(feed: DiscoveredFeed, page: Int, token: String?): FeedEnvelope = decode {
        ForumIndexJson.feed(request(discoveredTopicsUrl(feed, page), "GET",
            token = if (feed.personalized) token?.let(::checkedToken) else null))
    }

    fun fetchForum(id: Int): ForumDetail {
        require(id > 0)
        val body = request(url("/api/v2/forums/$id"), "GET")
        return decode { ForumIndexJson.forumDetail(body) }
    }

    override fun fetchTaxonomy(): NavigationEnvelope {
        val body = request(taxonomyUrl(), "GET")
        return decode { ForumIndexJson.navigation(body) }
    }

    override fun fetchMainTopics(page: Int): FeedEnvelope {
        val body = request(mainTopicsUrl(page), "GET")
        return decode { ForumIndexJson.feed(body) }
    }

    override fun fetchPersonalizedTopics(page: Int, token: String?): FeedEnvelope {
        val body = request(personalizedTopicsUrl(page), "GET", token = checkedToken(requireNotNull(token)))
        return decode { ForumIndexJson.feed(body) }
    }

    override fun fetchSubjectTopics(slug: String, page: Int): FeedEnvelope {
        val body = request(subjectTopicsUrl(slug, page), "GET")
        return decode { ForumIndexJson.feed(body) }
    }

    override fun createInstallation(displayName: String, deviceName: String): Enrollment {
        val body = JSONObject().put("display_name", displayName).put("device_name", installationDeviceName(deviceName))
        val response = request(url("/api/v2/installations"), "POST", body = body.toString())
        return decode { ForumIndexJson.enrollment(response) }
    }

    override fun fetchInstallation(token: String): Installation {
        val body = request(url("/api/v2/installation"), "GET", token = checkedToken(token))
        val installation = decode { ForumIndexJson.installationEnvelope(body) }
        if (installationDeviceName(installation.deviceName) == installation.deviceName) return installation
        // Backfill older debug installations without making reads depend on a metadata update.
        return try {
            updateInstallation(installation.deviceName, token)
        } catch (e: ForumIndexApiException.ExpiredAccess) {
            throw e
        } catch (_: ForumIndexApiException) {
            installation // Retry on the next installation refresh.
        }
    }

    private fun installationDeviceName(name: String): String {
        if (!devBuild || name.startsWith("[dev] ")) return name
        // The API limits labels to 100 Unicode code points, including the marker.
        val end = name.offsetByCodePoints(0, minOf(94, name.codePointCount(0, name.length)))
        return "[dev] " + name.substring(0, end)
    }

    override fun updateInstallation(deviceName: String, token: String): Installation {
        val body = request(
            url("/api/v2/installation"), "PATCH",
            JSONObject().put("device_name", installationDeviceName(deviceName)).toString(), checkedToken(token),
        )
        return decode { ForumIndexJson.installationEnvelope(body) }
    }

    override fun updateDisplayName(displayName: String, token: String): Installation {
        require(displayName.isNotBlank() && displayName.codePointCount(0, displayName.length) <= 100)
        val body = request(
            url("/api/v2/installation"), "PATCH",
            JSONObject().put("display_name", displayName).toString(), checkedToken(token),
        )
        return decode { ForumIndexJson.installationEnvelope(body) }
    }

    override fun submitForum(url: String, token: String) {
        require(url.toByteArray(StandardCharsets.UTF_8).size <= 1_000) { "forum url is too long" }
        request(
            url("/api/v2/forum-submission"), "POST",
            JSONObject().put("url", url).toString(), checkedToken(token), forbiddenExpiresAccess = false,
        )
    }

    override fun submitFeedback(submission: FeedbackSubmission, token: String) {
        require(submission.isValid)
        request(url("/api/v2/feedback"), "POST", submission.toJson().toString(), checkedToken(token), forbiddenExpiresAccess = false)
    }

    override fun deleteInstallation(token: String) {
        request(url("/api/v2/installation"), "DELETE", token = checkedToken(token))
    }

    override fun setTopicAction(topicId: Int, kind: String, active: Boolean, token: String) {
        require(topicId > 0) { "topic id must be positive" }
        val body = JSONObject().put("kind", kind).put("active", active).toString()
        request(url("/api/v2/topics/$topicId/action"), "PUT", body, checkedToken(token))
    }

    private inline fun <T> decode(block: () -> T): T = try {
        block()
    } catch (error: ForumIndexApiException) {
        throw error
    } catch (_: Exception) {
        throw ForumIndexApiException.InvalidResponse()
    }

    private fun request(
        url: URL,
        method: String,
        body: String? = null,
        token: String? = null,
        forbiddenExpiresAccess: Boolean = true,
    ): String {
        val connection = try {
            connectionFactory(url)
        } catch (_: Exception) {
            throw ForumIndexApiException.Transport()
        }
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.requestMethod = method
            connection.setRequestProperty("Accept", "application/json")
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            }
            val status = connection.responseCode
            if (token != null && (status == 401 || (status == 403 && forbiddenExpiresAccess))) {
                throw ForumIndexApiException.ExpiredAccess()
            }
            if (status !in 200..299) throw ForumIndexApiException.Http(status)
            return readBounded(connection.inputStream, MAX_RESPONSE_BYTES)
        } catch (error: ForumIndexApiException) {
            throw error
        } catch (_: Exception) {
            throw ForumIndexApiException.Transport()
        } finally {
            connection.disconnect()
        }
    }

    private fun url(path: String, query: String? = null): URL {
        val value = base.toASCIIString().trimEnd('/') + path + if (query == null) "" else "?$query"
        return URI(value).toURL()
    }

    companion object {
        private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024

        private fun validateBase(value: String): URI {
            val uri = try { URI(value) } catch (_: Exception) { throw IllegalArgumentException("invalid base URL") }
            require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null &&
                uri.rawQuery == null && uri.rawFragment == null) { "invalid base URL" }
            return uri
        }

        private fun checkedToken(token: String): String {
            require(token.isNotBlank() && token.none(Char::isISOControl)) { "invalid bearer token" }
            return token
        }

        private fun encodeSlug(slug: String): String {
            require(slug.isNotBlank() && slug != "." && slug != "..") { "invalid slug" }
            require(slug.all { it.isLetterOrDigit() || it in "-._~ " }) { "invalid slug" }
            val bytes = slug.toByteArray(StandardCharsets.UTF_8)
            return buildString(bytes.size) {
                for (byte in bytes) {
                    val value = byte.toInt() and 0xff
                    if (value in 'a'.code..'z'.code || value in 'A'.code..'Z'.code || value in '0'.code..'9'.code ||
                        value == '-'.code || value == '.'.code || value == '_'.code || value == '~'.code) append(value.toChar())
                    else append('%').append(value.toString(16).uppercase().padStart(2, '0'))
                }
            }
        }

        private fun readBounded(input: InputStream, max: Int): String = input.use {
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                total += count
                if (total > max) throw ForumIndexApiException.InvalidResponse()
                output.write(buffer, 0, count)
            }
            output.toString(StandardCharsets.UTF_8.name())
        }
    }
}

internal object ForumIndexJson {
    fun feeds(json: String): FeedCatalog {
        val root = JSONObject(json)
        val feeds = root.getJSONArray("feeds")
        return FeedCatalog(root.getString("version"), List(feeds.length()) { index ->
            val value = feeds.getJSONObject(index)
            DiscoveredFeed(value.getString("id"), value.getString("name"), value.optString("description"),
                value.getString("path"), value.optBoolean("personalized"))
        })
    }

    fun navigation(json: String): NavigationEnvelope {
        val root = JSONObject(json)
        val values = root.getJSONArray("subjects")
        return NavigationEnvelope(
            root.optString("version", "v1"), root.getString("window"),
            List(values.length()) { index -> values.getJSONObject(index).subject() },
        )
    }

    fun feed(json: String): FeedEnvelope {
        val root = JSONObject(json)
        val values = root.getJSONArray("results")
        return FeedEnvelope(root.getInt("count"), List(values.length()) { index -> values.getJSONObject(index).topic() })
    }

    fun forumDetail(json: String): ForumDetail {
        val value = JSONObject(json).getJSONObject("forum")
        val forum = value.forum()
        return ForumDetail(forum, value.optStringOrNull("description"),
            acceptedUrl(value.optStringOrNull("site_url")) ?: forum.baseUrl,
            acceptedUrl(value.optStringOrNull("parent_site_url")))
    }

    fun enrollment(json: String): Enrollment {
        val root = JSONObject(json)
        return Enrollment(root.getString("token"), root.getJSONObject("installation").installation())
    }

    fun installationEnvelope(json: String): Installation = JSONObject(json).getJSONObject("installation").installation()

    private fun JSONObject.subject() = Subject(
        getInt("id"), getString("name"), getString("slug"), optString("description", ""),
        optInt("position", 0), optBoolean("secondary", false), getInt("topic_count"),
    )

    private fun JSONObject.topic(): Topic {
        val excerpt = if (isNull("excerpt")) null else optString("excerpt").takeUnless { it.isBlank() }
        val engagement = optJSONObject("engagement")
        return Topic(
            getInt("id"), getString("title"), acceptedUrl(optStringOrNull("url")), excerpt,
            getJSONObject("forum").forum(), parseInstant(optStringOrNull("published_at")),
            maxOf(0, engagement?.optInt("replies", 0) ?: 0),
            externalSignals(),
            if (has("reader_markdown_url")) acceptedUrl(optStringOrNull("reader_markdown_url"))
            else if (getJSONObject("forum").optBoolean("supports_markdown")) acceptedUrl(optStringOrNull("url")) else null,
        )
    }

    private fun JSONObject.externalSignals(): List<ExternalSignal> {
        val values = optJSONArray("external_signals") ?: return emptyList()
        return (0 until values.length()).mapNotNull { index ->
            val value = values.optJSONObject(index) ?: return@mapNotNull null
            val source = ExternalSignalSource.entries.firstOrNull { it.apiValue == value.optString("source") }
                ?: return@mapNotNull null
            val url = acceptedUrl(value.optStringOrNull("url")) ?: return@mapNotNull null
            ExternalSignal(source, url)
        }
    }

    private fun JSONObject.forum() = ForumSummary(
        getInt("id"), getString("name"), getString("slug"),
        acceptedUrl(optStringOrNull("base_url")), acceptedUrl(optStringOrNull("icon_url")),
        optBoolean("supports_markdown", false),
    )

    private fun JSONObject.installation() = Installation(
        getString("public_id"), getString("display_name"), getString("device_name"),
        optBoolean("device_verified", false), optBoolean("trusted", false),
        if (isNull("read_count")) null else optInt("read_count"), optBoolean("for_you_eligible", false),
    )

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private fun acceptedUrl(value: String?): URL? = try {
        value?.let { URI(it) }?.takeIf { it.scheme.equals("https", true) && !it.host.isNullOrBlank() && it.userInfo == null }?.toURL()
    } catch (_: Exception) { null }

    private fun parseInstant(value: String?): Instant? = try { value?.let(Instant::parse) } catch (_: Exception) { null }
}
