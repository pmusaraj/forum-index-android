package com.musaraj.forumindex

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.view.View
import android.view.ViewGroup
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.net.URL

internal enum class TopicNavigation { IN_WEBVIEW, EXTERNAL, REJECT }

internal fun validTopicUrl(url: URL?): URL? = url?.takeIf {
    it.protocol.equals("https", true) && !it.host.isNullOrBlank() && it.userInfo == null
}

internal fun routeTopicNavigation(initial: URL, candidate: URL, hasUserGesture: Boolean): TopicNavigation {
    if (validTopicUrl(candidate) == null) return TopicNavigation.REJECT
    if (candidate.host.equals(initial.host, true)) return TopicNavigation.IN_WEBVIEW
    return if (hasUserGesture) TopicNavigation.EXTERNAL else TopicNavigation.IN_WEBVIEW
}

internal fun openExternalTopic(url: URL, launch: (Intent) -> Unit): Boolean = try {
    launch(Intent(Intent.ACTION_VIEW, Uri.parse(url.toString())))
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

@Composable
internal fun TopicWebView(
    topic: Topic,
    starred: Boolean,
    onToggleStar: () -> Unit,
    onDismiss: () -> Unit,
    testHtml: String? = null,
) {
    val initial = validTopicUrl(topic.url) ?: return
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(TopicBackground)) {
            TopicWebPage(initial, testHtml = testHtml)
            TopicActions(topic.bookmark(), starred, onToggleStar, onDismiss, Modifier.align(Alignment.BottomEnd))
        }
    }
}

/** Kept composed behind Markdown so switching views preserves web scroll and load state. */
@Composable
internal fun TopicWebPage(
    initial: URL,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    testHtml: String? = null,
    onReady: (Boolean) -> Unit = {},
) {
    var loading by remember(initial) { mutableStateOf(true) }
    var failed by remember(initial) { mutableStateOf(false) }
    var webView by remember(initial) { mutableStateOf<WebView?>(null) }
    LaunchedEffect(loading, failed) { onReady(!loading && !failed) }
    Box(modifier.fillMaxSize().then(if (visible) Modifier else Modifier.clearAndSetSemantics { })) {
        AndroidView(
            modifier = Modifier.fillMaxSize().testTag("topic-webview"),
            factory = { context ->
                secureWebView(context, initial, { loading = it }, { failed = true }).also { view ->
                    webView = view
                    if (testHtml == null) view.loadUrl(initial.toString())
                    else view.loadDataWithBaseURL(initial.toString(), testHtml, "text/html", "UTF-8", null)
                }
            },
            update = { view ->
                view.visibility = if (visible && !failed) View.VISIBLE else View.INVISIBLE
                view.importantForAccessibility = if (visible && !failed) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                    else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            },
        )
        if (visible && loading && !failed) Text("Loading…", Modifier.align(Alignment.Center))
        if (visible && failed) {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Couldn’t load this topic")
                Button(onClick = {
                    failed = false
                    loading = true
                    webView?.reload()
                }, Modifier.padding(top = 12.dp)) { Text("Retry") }
            }
        }
    }
    DisposableEffect(initial) {
        onDispose {
            webView?.stopLoading()
            (webView?.parent as? ViewGroup)?.removeView(webView)
            webView?.destroy()
            webView = null
        }
    }
}

@Composable
internal fun TopicActions(topic: StarredTopic, starred: Boolean, onToggleStar: () -> Unit, onDismiss: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    Column(
        modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TopicControl("Share", "↗") {
            try {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, topic.title)
                    putExtra(Intent.EXTRA_TEXT, topic.url)
                }, null))
            } catch (_: ActivityNotFoundException) { /* No share targets installed. */ }
        }
        TopicControl(if (starred) "Unstar" else "Star", if (starred) "★" else "☆", onToggleStar)
        TopicControl("Close", "×", onDismiss)
    }
}

@Composable
private fun TopicControl(label: String, glyph: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp).shadow(4.dp, CircleShape).background(Color.White.copy(alpha = .94f), CircleShape)
            .semantics { contentDescription = label },
    ) { Text(glyph, fontSize = 24.sp) }
}

@SuppressLint("SetJavaScriptEnabled")
private fun secureWebView(
    context: android.content.Context,
    initial: URL,
    onLoading: (Boolean) -> Unit,
    onFailure: () -> Unit,
) = WebView(context).apply {
    setBackgroundColor(android.graphics.Color.TRANSPARENT)
    settings.javaScriptEnabled = true
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
    settings.safeBrowsingEnabled = true
    var currentPage = initial
    webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            url?.let { resolveTopicUrl(currentPage, it) }?.let { currentPage = it }
            onLoading(true)
        }
        override fun onPageFinished(view: WebView?, url: String?) = onLoading(false)

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame) return false
            val candidate = try { URL(request.url.toString()) } catch (_: Exception) { return true }
            return when (routeTopicNavigation(currentPage, candidate, request.hasGesture())) {
                TopicNavigation.IN_WEBVIEW -> false
                TopicNavigation.EXTERNAL -> {
                    openExternalTopic(candidate, context::startActivity)
                    true
                }
                TopicNavigation.REJECT -> true
            }
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest, error: WebResourceError?) {
            if (request.isForMainFrame) onFailure()
        }

        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler, error: SslError?) {
            handler.cancel()
            onFailure()
        }

        override fun onSafeBrowsingHit(
            view: WebView?, request: WebResourceRequest?, threatType: Int, callback: SafeBrowsingResponse,
        ) {
            callback.backToSafety(true)
            onFailure()
        }
    }
}

private val TopicBackground = Color(0xFFFAF6EE)
