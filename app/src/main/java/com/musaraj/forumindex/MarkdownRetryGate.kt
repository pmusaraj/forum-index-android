package com.musaraj.forumindex

import java.io.IOException
import java.net.URL
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal class MarkdownRetryGate(private val now: () -> Instant = Instant::now) {
    private val deadlines = mutableMapOf<String, Instant>()

    @Synchronized fun check(url: URL) {
        val key = url.host.lowercase(Locale.ROOT)
        val deadline = deadlines[key]
        if (deadline != null && deadline > now()) throw IOException("The reader is temporarily unavailable. Try again after $deadline.")
        deadlines.remove(key)
    }

    @Synchronized fun record(url: URL, value: String) {
        val current = now()
        val trimmed = value.trim()
        val deadline = runCatching {
            trimmed.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }?.let {
                current.plusMillis((it * 1000).toLong())
            } ?: ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        }.getOrNull() ?: return
        deadlines.entries.removeAll { it.value <= current }
        if (deadline <= current) return
        val key = url.host.lowercase(Locale.ROOT)
        deadlines[key] = maxOf(deadlines[key] ?: deadline, deadline)
    }
}
