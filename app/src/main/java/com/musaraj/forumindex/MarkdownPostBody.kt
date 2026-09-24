package com.musaraj.forumindex

import android.graphics.BitmapFactory
import android.net.Uri
import android.text.Spanned
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.doOnLayout
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TableAwareMovementMethod
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.image.AsyncDrawableScheduler
import io.noties.markwon.image.ImageItem
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.SchemeHandler
import io.noties.markwon.image.destination.ImageDestinationProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

@Composable
internal fun rememberMarkdownRenderer(baseUrl: URL, onInternalLink: (URL) -> Boolean = { false }): Markwon {
    val context = LocalContext.current
    val resources = LocalResources.current
    val handleInternalLink by rememberUpdatedState(onInternalLink)
    return remember(context, resources, baseUrl) {
        Markwon.builder(context)
            .usePlugin(TablePlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(ImagesPlugin.create { it.addSchemeHandler(TopicImageHandler(resources)) })
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.linkResolver { view, link ->
                        resolveTopicUrl(baseUrl, link)?.let {
                            if (!handleInternalLink(it)) openExternalTopic(it, view.context::startActivity)
                        }
                    }
                    builder.imageDestinationProcessor(object : ImageDestinationProcessor() {
                        override fun process(destination: String): String =
                            resolveTopicUrl(baseUrl, destination)?.toString() ?: "blocked:"
                    })
                }
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder.linkColor(0xFF008C95.toInt())
                        .blockQuoteColor(0xFF008C95.toInt())
                        .codeBackgroundColor(0xFFF0EBE2.toInt())
                }
            })
            .build()
    }
}

/** Native, selectable Android text: no HTML WebView is used for the preview. */
@Composable
internal fun MarkdownPostBody(markdown: String, renderer: Markwon, onRendered: () -> Unit = {}) {
    val renderedCallback by rememberUpdatedState(onRendered)
    val rendered by produceState<Spanned?>(null, markdown, renderer) {
        value = withContext(Dispatchers.Default) { renderer.toMarkdown(markdown) }
    }
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { context -> TextView(context).apply {
            textSize = 16f
            setTextColor(0xFF202020.toInt())
            setLinkTextColor(0xFF008C95.toInt())
            setTextIsSelectable(true)
            movementMethod = TableAwareMovementMethod.create()
            setLineSpacing(0f, 1.15f)
        } },
        update = { view ->
            rendered?.let {
                if (view.tag !== it) {
                    renderer.setParsedMarkdown(view, it)
                    view.tag = it
                    view.doOnLayout { renderedCallback() }
                }
            }
        },
        onRelease = { AsyncDrawableScheduler.unschedule(it) },
    )
}

/** The preview loads only bounded HTTPS images, without app credentials or local-file access. */
private class TopicImageHandler(private val resources: android.content.res.Resources) : SchemeHandler() {
    override fun supportedSchemes() = listOf("https")

    override fun handle(raw: String, uri: Uri): ImageItem {
        var url = validTopicUrl(URL(raw)) ?: throw IOException("Unsupported image URL")
        repeat(6) {
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 5_000
                connection.readTimeout = 10_000
                val status = connection.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    url = connection.getHeaderField("Location")?.let { resolveTopicUrl(url, it) }
                        ?: throw IOException("Unsupported image redirect")
                } else {
                    if (status !in 200..299) throw IOException("Image unavailable")
                    val bytes = connection.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val size = input.read(buffer)
                            if (size < 0) break
                            if (output.size() + size > 8 * 1024 * 1024) throw IOException("Image is too large")
                            output.write(buffer, 0, size)
                        }
                        output.toByteArray()
                    }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    if (bounds.outWidth !in 1..16384 || bounds.outHeight !in 1..16384) throw IOException("Unsupported image")
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 1
                        while (bounds.outWidth / inSampleSize > 2048 || bounds.outHeight / inSampleSize > 2048) inSampleSize *= 2
                    }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: throw IOException("Unsupported image")
                    return ImageItem.withResult(bitmap.toDrawable(resources).apply { setBounds(0, 0, bitmap.width, bitmap.height) })
                }
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("Too many image redirects")
    }
}
