package com.musaraj.forumindex

import java.net.URL
import java.time.Instant

internal data class MarkdownPost(
    val id: Int,
    val username: String?,
    val avatarUrl: URL?,
    val publishedAt: Instant?,
    val markdown: String,
)

/** Parses only Discourse's generated envelope; post Markdown remains intact. */
internal data class MarkdownTopicDocument(val posts: List<MarkdownPost>, val nextPageUrl: URL?, val title: String? = null) {
    companion object {
        fun parse(markdown: String, baseUrl: URL, postIdOffset: Int = 0): MarkdownTopicDocument {
            val lines = markdown.replace("\r\n", "\n").split('\n')
            val hasEnvelope = isEnvelope(lines)
            val footer = mutableSetOf<Int>()
            if (hasEnvelope) {
                for (index in lines.indices.reversed()) {
                    if (lines[index].isBlank()) continue
                    if (!navigation.matches(lines[index])) break
                    footer += index
                }
            }
            val posts = mutableListOf<MarkdownPost>()
            val body = mutableListOf<String>()
            var author: String? = null
            var avatar: URL? = null
            var date: Instant? = null
            var hasMetadata = false
            var next: URL? = null
            var fence: Char? = null
            var fenceLength = 0

            fun appendPost(beforeMetadata: Boolean) {
                var content = body.joinToString("\n").trim()
                if (beforeMetadata && content.endsWith("\n\n---")) content = content.dropLast(5)
                if (!hasMetadata && posts.isEmpty()) content = removeEnvelope(content)
                if (hasMetadata || content.isNotBlank()) {
                    posts += MarkdownPost(postIdOffset + posts.size, author, avatar, date, content)
                }
                body.clear()
            }

            var index = 0
            while (index < lines.size) {
                val line = lines[index]
                val trimmed = line.trimStart()
                val character = trimmed.firstOrNull()
                if (character == '`' || character == '~') {
                    val count = trimmed.takeWhile { it == character }.length
                    if (fence != null) {
                        if (character == fence && count >= fenceLength && trimmed.drop(count).isBlank()) fence = null
                    } else if (count >= 3) {
                        fence = character
                        fenceLength = count
                    }
                    body += line
                    index++
                    continue
                }
                if (fence == null && index in footer) {
                    val match = navigation.matchEntire(line)!!
                    if (match.groupValues[1] == "Next") next = resolveTopicUrl(baseUrl, match.groupValues[2])
                    index++
                    continue
                }
                val legacy = if (fence == null && hasEnvelope) legacyMetadata.matchEntire(line) else null
                val legacyDate = legacy?.groupValues?.get(2)?.let { try { Instant.parse(it) } catch (_: Exception) { null } }
                if (legacy != null && legacyDate != null) {
                    appendPost(beforeMetadata = true)
                    author = legacy.groupValues[1].replace("\\_", "_")
                    avatar = null
                    date = legacyDate
                    hasMetadata = true
                    index++
                    continue
                }
                if (fence == null && line in metadataStarts) {
                    val end = (index + 1 until lines.size).firstOrNull { lines[it] == "</div>" }
                    val metadata = end?.let { lines.subList(index + 1, it) }
                    val authorLine = metadata?.firstOrNull { it.startsWith("### Author:") }
                    if (end != null && authorLine != null) {
                        appendPost(beforeMetadata = true)
                        author = username.find(authorLine)?.groupValues?.get(1)?.replace("\\_", "_")
                        avatar = avatarPattern.find(authorLine)?.groupValues?.get(1)?.let { resolveTopicUrl(baseUrl, it) }
                        val timestamp = datePattern.find(metadata.firstOrNull { it.startsWith("#### Post date:") }.orEmpty())
                            ?.groupValues?.get(1)
                        date = try { timestamp?.let(Instant::parse) } catch (_: Exception) { null }
                        hasMetadata = true
                        index = end + 1
                        continue
                    }
                }
                body += line
                index++
            }
            appendPost(beforeMetadata = false)
            val title = if (hasEnvelope) lines.first().removePrefix("# ").trim().takeIf { it.isNotEmpty() } else null
            return MarkdownTopicDocument(posts, next, title)
        }

        private val legacyMetadata = Regex("""^## Post [0-9]+ by @([^\s]+) — ([0-9]{4}-[^\s]+)$""")
        private val navigation = Regex("""^\[(Next|Previous) page\]\(([^\s)]+)\)$""")
        private val username = Regex("""\[@((?:\\.|[^\]])+)\]\(""")
        private val avatarPattern = Regex("""!\[[^\]]*\]\(([^\s)]+)\)""")
        private val datePattern = Regex("\"([0-9]{4}-[^\"\\s]+)\"")
        private val metadataStarts = setOf("<div class=\"post-metadata\">", "<div class='post-metadata'>")
        private val fields = listOf("**URL:**", "**Category:**", "**Tags:**", "**Created:**", "**Posts:**", "**Posts on this page:**", "**Page:**", "**Showing post:**")

        private fun isEnvelope(lines: List<String>) = lines.firstOrNull()?.startsWith("# ") == true &&
            lines.drop(1).firstOrNull { it.isNotEmpty() }?.startsWith("**URL:**") == true

        private fun removeEnvelope(input: String): String {
            val lines = input.split('\n')
            if (!isEnvelope(lines)) return input
            return lines.drop(1).dropWhile { line -> line.isEmpty() || fields.any(line::startsWith) }.joinToString("\n").trim()
        }
    }
}

internal fun resolveTopicUrl(base: URL, value: String): URL? = try {
    validTopicUrl(URL(base, value))
} catch (_: Exception) { null }

internal fun relativePostDate(date: Instant, now: Instant = Instant.now()): String {
    val minutes = ((now.epochSecond - date.epochSecond) / 60).coerceAtLeast(0)
    if (minutes == 0L) return "Just now"
    val (count, unit) = when {
        minutes < 60 -> minutes to "minute"
        minutes < 1440 -> minutes / 60 to "hour"
        else -> minutes / 1440 to "day"
    }
    return "$count $unit${if (count == 1L) "" else "s"} ago"
}
