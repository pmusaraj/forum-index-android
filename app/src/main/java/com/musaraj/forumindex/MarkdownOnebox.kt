package com.musaraj.forumindex

import java.net.URI

internal data class MarkdownSegment(val markdown: String, val isOnebox: Boolean)

/** Recognize exported oneboxes without rewriting ordinary quotations or fenced examples. */
internal object MarkdownOnebox {
    fun segments(markdown: String): List<MarkdownSegment> {
        val lines = markdown.split('\n')
        val result = mutableListOf<MarkdownSegment>()
        var start = 0
        var index = 0
        var fence: Char? = null
        var fenceLength = 0
        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trimStart(' ')
            val character = trimmed.firstOrNull()
            if (line.length - trimmed.length <= 3 && character in listOf('`', '~')) {
                val count = trimmed.takeWhile { it == character }.length
                if (fence != null) {
                    if (character == fence && count >= fenceLength && trimmed.drop(count).isBlank()) fence = null
                } else if (count >= 3) { fence = character; fenceLength = count }
                index++
                continue
            }
            val url = if (line.startsWith("> <") && line.endsWith('>'))
                runCatching { URI(line.substring(3, line.length - 1)) }.getOrNull() else null
            if (fence != null || (index > 0 && lines[index - 1].isNotEmpty()) ||
                url?.scheme !in listOf("https", "http") || url?.host == null ||
                index + 2 >= lines.size || lines[index + 1].trim() != ">" || !lines[index + 2].startsWith('>')) {
                index++
                continue
            }
            val quoteStart = index++
            while (index < lines.size && lines[index].startsWith('>')) index++
            if (start < quoteStart) result.add(MarkdownSegment(lines.subList(start, quoteStart).joinToString("\n"), false))
            var body = lines.subList(quoteStart + 1, index).joinToString("\n") { it.drop(1).removePrefix(" ") }
            if (url.host.equals("github.com", true)) body = body.replace(Regex("""\\([!"#$%&'()*+,\-./:;<=>?@\[\\\]^_`{|}~])""")) { it.groupValues[1] }
            result.add(MarkdownSegment("<$url>\n\n$body", true))
            start = index
        }
        if (start < lines.size) result.add(MarkdownSegment(lines.subList(start, lines.size).joinToString("\n"), false))
        return result
    }
}
